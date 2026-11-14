package com.cogwork.cogworkbridge.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Adds moving light sources to vanilla's block-light propagation. */
public final class ContraptionLightTransformer implements IClassTransformer {
    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !"net.minecraft.world.World".equals(transformedName)) {
            return bytes;
        }
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        boolean[] found = {false};
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String methodName,
                                             String descriptor, String signature,
                                             String[] exceptions) {
                MethodVisitor method = super.visitMethod(
                        access, methodName, descriptor, signature, exceptions);
                boolean mapped = (methodName.equals("getRawLight")
                        || methodName.equals("func_175638_a"))
                        && descriptor.endsWith(")I");
                boolean obfuscated = methodName.equals("a")
                        && descriptor.equals("(Let;Lana;)I");
                if (!mapped && !obfuscated) {
                    return method;
                }
                found[0] = true;
                return new MethodVisitor(Opcodes.ASM9, method) {
                    @Override
                    public void visitInsn(int opcode) {
                        if (opcode == Opcodes.IRETURN) {
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            super.visitVarInsn(Opcodes.ALOAD, 1);
                            super.visitVarInsn(Opcodes.ALOAD, 2);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,
                                    "com/cogwork/cogworkbridge/content/contraptions/bearing/ContraptionDynamicLight",
                                    "combineRawLight",
                                    "(ILjava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)I",
                                    false);
                        }
                        super.visitInsn(opcode);
                    }
                };
            }
        }, 0);
        if (!found[0]) {
            throw new IllegalStateException("Could not locate World.getRawLight");
        }
        return writer.toByteArray();
    }
}
