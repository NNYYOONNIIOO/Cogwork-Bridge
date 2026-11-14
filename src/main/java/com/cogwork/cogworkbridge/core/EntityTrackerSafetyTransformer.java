package com.cogwork.cogworkbridge.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.apache.logging.log4j.LogManager;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Allows tracking callbacks to add or remove entities during tracker updates. */
public final class EntityTrackerSafetyTransformer implements IClassTransformer {
    private static final String TRACKER = "net.minecraft.entity.EntityTracker";
    private static final String SET = "java/util/Set";
    private static final String ITERATION =
            "com/cogwork/cogworkbridge/core/EntityTrackerIteration";

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !TRACKER.equals(transformedName)) return bytes;

        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        int[] replacements = {0};
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String methodName,
                                             String descriptor, String signature,
                                             String[] exceptions) {
                MethodVisitor method = super.visitMethod(
                        access, methodName, descriptor, signature, exceptions);
                return new MethodVisitor(Opcodes.ASM9, method) {
                    private boolean trackerEntries;

                    @Override
                    public void visitFieldInsn(int opcode, String owner,
                                               String fieldName, String fieldDescriptor) {
                        trackerEntries = opcode == Opcodes.GETFIELD
                                && reader.getClassName().equals(owner)
                                && "Ljava/util/Set;".equals(fieldDescriptor);
                        super.visitFieldInsn(opcode, owner, fieldName, fieldDescriptor);
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String owner,
                                                String calledName, String calledDescriptor,
                                                boolean isInterface) {
                        boolean entriesIteration = trackerEntries;
                        trackerEntries = false;
                        if (entriesIteration && opcode == Opcodes.INVOKEINTERFACE
                                && SET.equals(owner)
                                && "iterator".equals(calledName)
                                && "()Ljava/util/Iterator;".equals(calledDescriptor)) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, ITERATION,
                                    "snapshotIterator",
                                    "(Ljava/util/Set;)Ljava/util/Iterator;", false);
                            replacements[0]++;
                            return;
                        }
                        super.visitMethodInsn(opcode, owner, calledName,
                                calledDescriptor, isInterface);
                    }
                };
            }
        }, 0);

        if (replacements[0] < 2) {
            LogManager.getLogger("CogworkBridgeCore").error(
                    "Could not safely identify EntityTracker set iteration (matches: {})",
                    replacements[0]);
            return bytes;
        }
        return writer.toByteArray();
    }
}
