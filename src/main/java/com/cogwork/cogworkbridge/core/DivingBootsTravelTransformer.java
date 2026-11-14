package com.cogwork.cogworkbridge.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;

/** Installs the upstream heavy-boots multiplier at the actual travel call site. */
public final class DivingBootsTravelTransformer implements IClassTransformer {
    @Override public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !"net.minecraft.entity.EntityLivingBase".equals(transformedName)) return bytes;
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public MethodVisitor visitMethod(int access, String method, String descriptor, String signature, String[] exceptions) {
                MethodVisitor visitor = super.visitMethod(access, method, descriptor, signature, exceptions);
                if (!descriptor.equals("(FFF)V") || !(method.equals("travel") || method.equals("func_191986_a") || method.equals("a"))) return visitor;
                return new MethodVisitor(Opcodes.ASM9, visitor) {
                    @Override public void visitMethodInsn(int opcode, String owner, String called, String desc, boolean itf) {
                        super.visitMethodInsn(opcode, owner, called, desc, itf);
                        if (opcode == Opcodes.INVOKEVIRTUAL && desc.endsWith(";DDD)V")
                                && (called.equals("move") || called.equals("func_70091_d") || called.equals("a"))) {
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,
                                    "com/cogwork/cogworkbridge/content/equipment/DivingGearItem", "afterFluidTravel", "(Ljava/lang/Object;)V", false);
                        }
                    }
                };
            }
        }, 0);
        return writer.toByteArray();
    }
}
