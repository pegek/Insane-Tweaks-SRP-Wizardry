package com.spege.srpwizcore.core;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import com.spege.srpwizcore.devspeed.DevSpeedState;

/**
 * Application gate + self-check for the dev-only game-speed mixins
 * ({@code mixins.srpwizcore.devspeed.json}). With the toggle off neither class is touched at all.
 *
 * <p>The server half is patched here directly ({@link #preApply}); the client half is a
 * {@code require = 0} redirect. Neither can break the boot when its target moves, and this is
 * where that silence is undone: {@link #postApply} counts, on the bytecode that will
 * actually be defined, how many sites now call our handlers, records the count in
 * {@link DevSpeedState} for {@code /devspeed}, and logs an ERROR when it is not what vanilla
 * (and Cleanroom 0.6.13) has — 4 constants in {@code MinecraftServer.run}, 1 read in
 * {@code Timer.updateTimer}. A count that is too low means another transformer rewrote the loop
 * and the feature would only half work.
 *
 * <p>{@link DevSpeedState} is safe to touch from here because it holds primitives only; see its
 * class comment.
 */
public class DevSpeedMixinPlugin extends ConfigFlagMixinPlugin {

    /** Must match {@code @Config.Name} on {@code DevSpeedCategory.enabled}. */
    private static final String FLAG_NAME = "Dev Speed: Enabled";

    /** Internal name, spelled out: the plugin must not resolve {@code DevSpeedState} for this. */
    private static final String STATE_OWNER = "com/spege/srpwizcore/devspeed/DevSpeedState";

    /** Must match {@code DevSpeedState.serverTickMillis()J}. */
    private static final String SERVER_HOOK = "serverTickMillis";

    private static final int EXPECTED_SERVER_CONSTANTS = 4;
    private static final int EXPECTED_CLIENT_READS = 1;

    public DevSpeedMixinPlugin() {
        super(FLAG_NAME, false, "dev game-speed tool (/devspeed)");
    }

    /**
     * The server half's actual patch: every {@code 50L} in {@code MinecraftServer.run()V} becomes
     * {@code INVOKESTATIC DevSpeedState.serverTickMillis()J}. Same stack effect (one long), so
     * nothing around it changes and no frame needs recomputing. Done here on the
     * {@code ClassNode} rather than with {@code @ModifyConstant}, which wired 0 of 4 under
     * CleanMix 0.7.4 — see {@code MixinMinecraftServerDevSpeed}. Both Cleanroom 0.6.13 and 0.7.11
     * ship the vanilla loop, so one rewrite covers both.
     */
    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName,
            IMixinInfo mixinInfo) {
        if (!mixinClassName.endsWith("MixinMinecraftServerDevSpeed")) {
            return;
        }
        for (MethodNode method : targetClass.methods) {
            if (!"run".equals(method.name) || !"()V".equals(method.desc)) {
                continue;
            }
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null;
                    insn = insn.getNext()) {
                if (insn instanceof LdcInsnNode
                        && Long.valueOf(DevSpeedState.VANILLA_MS_PER_TICK)
                                .equals(((LdcInsnNode) insn).cst)) {
                    AbstractInsnNode call = new MethodInsnNode(Opcodes.INVOKESTATIC, STATE_OWNER,
                            SERVER_HOOK, "()J", false);
                    method.instructions.set(insn, call);
                    insn = call;
                }
            }
        }
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName,
            IMixinInfo mixinInfo) {
        if (mixinClassName.endsWith("MixinMinecraftServerDevSpeed")) {
            int wired = countCalls(targetClass, "run", "()V", SERVER_HOOK);
            DevSpeedState.serverPatchedConstants = wired;
            if (wired == EXPECTED_SERVER_CONSTANTS) {
                this.logger.info("[srpwizcore] devspeed: MinecraftServer.run patched "
                        + "({} x 50L tick constant -> /devspeed)", wired);
            } else {
                this.logger.error("[srpwizcore] devspeed: MinecraftServer.run has {} of {} "
                        + "expected 50L tick constants wired - the server loop was rewritten by "
                        + "something else. /devspeed will {}.", wired, EXPECTED_SERVER_CONSTANTS,
                        wired == 0 ? "refuse to run" : "be unreliable");
            }
        } else if (mixinClassName.endsWith("MixinTimerDevSpeed")) {
            int wired = countCalls(targetClass, "func_74275_a", "()V", "devSpeedTickLength");
            if (wired == 0) {
                // Dev environment (MCP names) - harmless, but say which name was tried.
                wired = countCalls(targetClass, "updateTimer", "()V", "devSpeedTickLength");
            }
            DevSpeedState.clientPatchedReads = wired;
            if (wired == EXPECTED_CLIENT_READS) {
                this.logger.info("[srpwizcore] devspeed: client Timer.updateTimer patched");
            } else {
                this.logger.error("[srpwizcore] devspeed: client Timer.updateTimer has {} of {} "
                        + "expected tickLength reads wired - the client will keep ticking at "
                        + "20 TPS while the server runs faster.", wired, EXPECTED_CLIENT_READS);
            }
        }
    }

    private static int countCalls(ClassNode owner, String methodName, String desc,
            String handlerFragment) {
        int count = 0;
        for (MethodNode method : owner.methods) {
            if (!methodName.equals(method.name) || !desc.equals(method.desc)) {
                continue;
            }
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null;
                    insn = insn.getNext()) {
                if (insn instanceof MethodInsnNode
                        && ((MethodInsnNode) insn).name.contains(handlerFragment)) {
                    count++;
                }
            }
        }
        return count;
    }
}
