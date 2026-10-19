package com.spege.srpwizcore.core;

import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Application gate, strip pass and self-check for {@code mixins.srpwizcore.bloodmoon.json}.
 *
 * <ul>
 * <li>{@link #shouldApplyMixin}: only when Bloodmoon's jar is on the classpath. Looked up as a
 * resource, which defines nothing; {@code Loader.isModLoaded} does not exist yet this early.</li>
 * <li>{@link #preApply}: removes the lightmap hook Bloodmoon's own transformer inserted into
 * {@code updateLightmap}, matched by instruction shape — {@code GETSTATIC
 * ClientBloodmoonHandler.INSTANCE; ILOAD a; ILOAD b; INVOKEVIRTUAL manipulateX(II)I; ISTORE b} —
 * never by slot number, since wrong slot numbers are the whole bug (see the mixin). It works only
 * because Mixin's transformer runs after the coremod transformers, so the {@code ClassNode} here
 * already carries Bloodmoon's edit; if that ever stops being true the strip finds nothing and says
 * so.</li>
 * <li>{@link #postApply}: counts what is left — expects 0 Bloodmoon hooks and 1 call into ours, and
 * logs ERROR otherwise. The injection is {@code require = 0} so a moved anchor degrades to "no red
 * light" instead of a boot failure; this is where that silence is undone.</li>
 * </ul>
 *
 * <p>Deliberately no config flag: without the strip, Bloodmoon's broken patch is a guaranteed
 * {@code VerifyError} on Cleanroom 0.7, so there is no "off" that would be useful.
 */
public class BloodmoonMixinPlugin implements IMixinConfigPlugin {

    private static final Logger LOGGER = LogManager.getLogger("srpwizcore");

    private static final String HANDLER_OWNER = "lumien/bloodmoon/client/ClientBloodmoonHandler";
    private static final String HANDLER_RESOURCE = HANDLER_OWNER + ".class";

    private static final String METHOD_SRG = "func_78472_g";
    private static final String METHOD_MCP = "updateLightmap";
    private static final String METHOD_DESC = "(F)V";

    /** Bloodmoon inserts one hook per channel. */
    private static final int EXPECTED_HOOKS = 3;

    private static final String OUR_HANDLER = "bloodmoonLightmap";

    private Boolean present;

    @Override
    public void onLoad(String mixinPackage) {
        // nothing to prepare
    }

    @Override
    public String getRefMapperConfig() {
        // No refmaps in this project (-proc:none); matching is by explicit SRG + remap = false.
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (this.present == null) {
            this.present = Boolean.valueOf(bloodmoonOnClasspath());
            LOGGER.info("[srpwizcore] Bloodmoon lightmap fix {}", this.present.booleanValue()
                    ? "ENABLED (Bloodmoon found)" : "skipped (Bloodmoon not installed)");
        }
        return this.present.booleanValue();
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        // no-op
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName,
            IMixinInfo mixinInfo) {
        MethodNode method = findLightmapMethod(targetClass);
        if (method == null) {
            LOGGER.error("[srpwizcore] Bloodmoon: {}{} not found in {} - nothing stripped",
                    METHOD_SRG, METHOD_DESC, targetClassName);
            return;
        }
        int stripped = stripHooks(method);
        if (stripped == EXPECTED_HOOKS) {
            LOGGER.info("[srpwizcore] Bloodmoon: removed its {} slot-bound lightmap hooks from "
                    + "{}; the slot-free replacement takes over", stripped, method.name);
        } else {
            LOGGER.warn("[srpwizcore] Bloodmoon: removed {} of {} expected lightmap hooks from {}. "
                    + "0 means Bloodmoon's transformer did not run before Mixin's - on Cleanroom "
                    + "0.7 its broken hook will then fail verification.", stripped,
                    EXPECTED_HOOKS, method.name);
        }
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName,
            IMixinInfo mixinInfo) {
        MethodNode method = findLightmapMethod(targetClass);
        if (method == null) {
            return;
        }
        int ours = 0;
        int theirs = 0;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null;
                insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) insn;
                if (call.name.contains(OUR_HANDLER)) {
                    ours++;
                } else if (HANDLER_OWNER.equals(call.owner)) {
                    theirs++;
                }
            }
        }
        if (ours == 1 && theirs == 0) {
            LOGGER.info("[srpwizcore] Bloodmoon: {} patched (red lightmap via srpwizcore)",
                    method.name);
        } else {
            LOGGER.error("[srpwizcore] Bloodmoon: {} has {} srpwizcore hook(s) (expected 1) and "
                    + "{} Bloodmoon call(s) left (expected 0) - the blood moon will not tint the "
                    + "light correctly.", method.name, ours, theirs);
        }
    }

    private static MethodNode findLightmapMethod(ClassNode owner) {
        for (MethodNode method : owner.methods) {
            if (METHOD_DESC.equals(method.desc)
                    && (METHOD_SRG.equals(method.name) || METHOD_MCP.equals(method.name))) {
                return method;
            }
        }
        return null;
    }

    /**
     * Removes every exact 5-instruction hook Bloodmoon inserted. Bloodmoon adds the five as one
     * contiguous {@code InsnList} with no labels or frames, so strict adjacency is required —
     * that keeps an unrelated call into the handler from ever matching.
     */
    private static int stripHooks(MethodNode method) {
        int stripped = 0;
        AbstractInsnNode insn = method.instructions.getFirst();
        while (insn != null) {
            AbstractInsnNode[] hook = matchHook(insn);
            if (hook == null) {
                insn = insn.getNext();
                continue;
            }
            insn = hook[hook.length - 1].getNext();
            for (AbstractInsnNode part : hook) {
                method.instructions.remove(part);
            }
            stripped++;
        }
        return stripped;
    }

    private static AbstractInsnNode[] matchHook(AbstractInsnNode start) {
        if (start.getOpcode() != Opcodes.GETSTATIC) {
            return null;
        }
        FieldInsnNode instance = (FieldInsnNode) start;
        if (!HANDLER_OWNER.equals(instance.owner) || !"INSTANCE".equals(instance.name)) {
            return null;
        }
        AbstractInsnNode index = instance.getNext();
        AbstractInsnNode channel = index == null ? null : index.getNext();
        AbstractInsnNode call = channel == null ? null : channel.getNext();
        AbstractInsnNode store = call == null ? null : call.getNext();
        if (index == null || index.getOpcode() != Opcodes.ILOAD
                || channel == null || channel.getOpcode() != Opcodes.ILOAD
                || call == null || call.getOpcode() != Opcodes.INVOKEVIRTUAL
                || store == null || store.getOpcode() != Opcodes.ISTORE) {
            return null;
        }
        MethodInsnNode invoke = (MethodInsnNode) call;
        if (!HANDLER_OWNER.equals(invoke.owner) || !invoke.name.startsWith("manipulate")
                || !"(II)I".equals(invoke.desc)) {
            return null;
        }
        if (((VarInsnNode) channel).var != ((VarInsnNode) store).var) {
            return null;
        }
        return new AbstractInsnNode[] {start, index, channel, call, store};
    }

    private static boolean bloodmoonOnClasspath() {
        try {
            ClassLoader own = BloodmoonMixinPlugin.class.getClassLoader();
            if (own != null && own.getResource(HANDLER_RESOURCE) != null) {
                return true;
            }
            ClassLoader context = Thread.currentThread().getContextClassLoader();
            return context != null && context.getResource(HANDLER_RESOURCE) != null;
        } catch (Throwable t) {
            LOGGER.warn("[srpwizcore] Bloodmoon: classpath probe failed, assuming absent", t);
            return false;
        }
    }
}
