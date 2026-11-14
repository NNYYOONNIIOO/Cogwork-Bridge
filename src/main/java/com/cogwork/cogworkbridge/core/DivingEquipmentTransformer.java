package com.cogwork.cogworkbridge.core;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraftforge.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Fire immunity, lava swim particles and caller-scoped compatibility with FFD's swimming hooks. */
public final class DivingEquipmentTransformer implements IClassTransformer {
    private static final String HOOKS = "com/cogwork/cogworkbridge/content/equipment/DivingGearItem";
    private static final FMLDeobfuscatingRemapper NAMES = FMLDeobfuscatingRemapper.INSTANCE;

    @Override public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !"net.minecraft.entity.Entity".equals(transformedName)) return bytes;
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public MethodVisitor visitMethod(int access, String method, String descriptor,
                    String signature, String[] exceptions) {
                MethodVisitor delegate = super.visitMethod(access, method, descriptor, signature, exceptions);
                // This core plugin runs before FML's deobfuscating transformer.
                String mapped = NAMES.mapMethodName(reader.getClassName(), method, descriptor);
                String mappedDescriptor = NAMES.mapMethodDesc(descriptor);
                boolean water = mappedDescriptor.equals("()Z")
                        && (mapped.equals("isInWater") || mapped.equals("func_70090_H"));
                boolean eye = mappedDescriptor.equals("(Lnet/minecraft/block/material/Material;)Z")
                        && (mapped.equals("isInsideOfMaterial") || mapped.equals("func_70055_a"));
                boolean runningParticles = mappedDescriptor.equals("()V")
                        && (mapped.equals("spawnRunningParticles") || mapped.equals("func_174830_Y"));
                return new MethodVisitor(Opcodes.ASM9, delegate) {
                    @Override public void visitMethodInsn(int opcode, String owner, String called, String desc,
                            boolean isInterface) {
                        super.visitMethodInsn(opcode, owner, called, desc, isInterface);
                        if (!runningParticles || opcode != Opcodes.INVOKEVIRTUAL
                                || !NAMES.mapType(owner).equals("net/minecraft/entity/Entity")
                                || !NAMES.mapMethodDesc(desc).equals("()Z")) return;
                        String mappedCall = NAMES.mapMethodName(owner, called, desc);
                        if (mappedCall.equals("isSprinting") || mappedCall.equals("func_70051_ag")) {
                            // Filter this particle decision without clearing the sprint flag used by FFD swimming.
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "runningParticleSprintQuery",
                                    "(ZLjava/lang/Object;)Z", false);
                        }
                    }
                    @Override public void visitFieldInsn(int opcode, String owner, String field, String desc) {
                        boolean fire = opcode == Opcodes.GETFIELD && desc.equals("Z")
                                && NAMES.mapType(owner).equals("net/minecraft/entity/Entity")
                                && (NAMES.mapFieldName(owner, field, desc).equals("isImmuneToFire")
                                    || NAMES.mapFieldName(owner, field, desc).equals("field_70178_ae"));
                        if (fire) super.visitInsn(Opcodes.DUP);
                        super.visitFieldInsn(opcode, owner, field, desc);
                        if (fire) super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "equipmentFireImmunity",
                                "(Ljava/lang/Object;Z)Z", false);
                    }
                    @Override public void visitInsn(int opcode) {
                        if (opcode == Opcodes.IRETURN && (water || eye)) {
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            if (eye) super.visitVarInsn(Opcodes.ALOAD, 1);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS,
                                    eye ? "swimmingEyeQuery" : "swimmingFluidQuery",
                                    eye ? "(ZLjava/lang/Object;Ljava/lang/Object;)Z" : "(ZLjava/lang/Object;)Z", false);
                        }
                        super.visitInsn(opcode);
                    }
                };
            }
        }, 0);
        return writer.toByteArray();
    }
}
