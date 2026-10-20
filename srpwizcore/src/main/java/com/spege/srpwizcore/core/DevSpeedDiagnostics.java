package com.spege.srpwizcore.core;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Failure-only diagnostics for the {@code /devspeed} server patch: when {@code DevSpeedMixinPlugin}
 * wires anything other than 4 constants, this says what {@code MinecraftServer} looked like at
 * that moment and writes the class to {@code logs/srpwizcore-devspeed/}, so the shape Mixin really
 * receives can be read with {@code javap} instead of guessed.
 *
 * <p>Runs inside the transformer: ASM tree and {@code java.*} only, no Minecraft or Forge type,
 * and no {@code asm-util} (not guaranteed on Cleanroom 0.6's classpath). Never throws.
 */
final class DevSpeedDiagnostics {

    private static final String DUMP_DIR = "logs/srpwizcore-devspeed";

    private static final long TICK = 50L;

    /** Distinguishes the passes: ZenUtils runs the transformer chain a second time for bytes. */
    private static int sequence;

    private DevSpeedDiagnostics() {
    }

    static void describe(Logger logger, String phase, ClassNode node, int wired) {
        try {
            logger.warn("[srpwizcore] devspeed diag [{}]: {} (class version {}, {} methods, "
                    + "thread {}), wired {}", phase, node.name, node.version & 0xFFFF,
                    node.methods.size(), Thread.currentThread().getName(), wired);
            for (MethodNode method : node.methods) {
                int ticks = 0;
                for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null;
                        insn = insn.getNext()) {
                    if (insn instanceof LdcInsnNode
                            && Long.valueOf(TICK).equals(((LdcInsnNode) insn).cst)) {
                        ticks++;
                    }
                }
                boolean isRun = method.name.startsWith("run");
                if (ticks > 0 || isRun) {
                    logger.warn("[srpwizcore] devspeed diag [{}]:   {}{} access=0x{} insns={} "
                            + "ldc50L={}", phase, method.name, method.desc,
                            Integer.toHexString(method.access), method.instructions.size(), ticks);
                }
                if ("run".equals(method.name) && "()V".equals(method.desc)) {
                    logger.warn("[srpwizcore] devspeed diag [{}]:     long constants: {}", phase,
                            longConstants(method));
                    logger.warn("[srpwizcore] devspeed diag [{}]:     calls/fields: {}", phase,
                            references(method));
                }
            }
            logger.warn("[srpwizcore] devspeed diag [{}]:   loaded via: {}", phase, callers());
        } catch (Throwable t) {
            logger.warn("[srpwizcore] devspeed diag [{}]: describe failed", phase, t);
        }
    }

    static void dump(Logger logger, String tag, ClassNode node) {
        try {
            int seq;
            synchronized (DevSpeedDiagnostics.class) {
                seq = ++sequence;
            }
            ClassWriter writer = new ClassWriter(0);
            node.accept(writer);
            File dir = new File(DUMP_DIR);
            if (!dir.isDirectory() && !dir.mkdirs()) {
                logger.warn("[srpwizcore] devspeed diag: cannot create {}", dir.getAbsolutePath());
                return;
            }
            String simple = node.name.substring(node.name.lastIndexOf('/') + 1);
            File out = new File(dir, simple + "-" + seq + "-" + tag + ".class");
            FileOutputStream stream = new FileOutputStream(out);
            try {
                stream.write(writer.toByteArray());
            } finally {
                stream.close();
            }
            logger.warn("[srpwizcore] devspeed diag: dumped {} to {}", node.name,
                    out.getAbsolutePath());
        } catch (Throwable t) {
            logger.warn("[srpwizcore] devspeed diag: dump of {} failed", node.name, t);
        }
    }

    private static List<Object> longConstants(MethodNode method) {
        List<Object> values = new ArrayList<Object>();
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null;
                insn = insn.getNext()) {
            if (insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof Long) {
                values.add(((LdcInsnNode) insn).cst);
            }
        }
        return values;
    }

    /** Every distinct owner.name the method calls or reads, in order — shows a delegated loop. */
    private static Set<String> references(MethodNode method) {
        Set<String> seen = new LinkedHashSet<String>();
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null;
                insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) insn;
                seen.add(call.owner + "." + call.name);
            } else if (insn instanceof FieldInsnNode) {
                FieldInsnNode field = (FieldInsnNode) insn;
                seen.add(field.owner + "#" + field.name);
            }
            if (seen.size() >= 60) {
                break;
            }
        }
        return seen;
    }

    /** Non-JDK frames on the way here, outermost last, so the transformer chain is visible. */
    private static List<String> callers() {
        List<String> frames = new ArrayList<String>();
        for (StackTraceElement element : new Throwable().getStackTrace()) {
            String cls = element.getClassName();
            if (cls.startsWith("java.") || cls.startsWith("jdk.") || cls.startsWith("sun.")
                    || cls.startsWith(DevSpeedDiagnostics.class.getName())) {
                continue;
            }
            String frame = cls.substring(cls.lastIndexOf('.') + 1) + "." + element.getMethodName();
            if (frames.isEmpty() || !frames.get(frames.size() - 1).equals(frame)) {
                frames.add(frame);
            }
            if (frames.size() >= 25) {
                break;
            }
        }
        return frames;
    }
}
