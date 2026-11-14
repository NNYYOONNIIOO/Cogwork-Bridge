package com.cogwork.cogworkbridge.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import net.minecraftforge.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper;

/** Routes Forge GUI creation to the carried world when a block is moving. */
public final class ContraptionGuiTransformer implements IClassTransformer {
    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null) return null;
        boolean player = "net.minecraft.entity.player.EntityPlayer".equals(transformedName);
        boolean serverPlayer = "net.minecraft.entity.player.EntityPlayerMP".equals(transformedName);
        boolean mekanismPackets = "mekanism.common.PacketHandler".equals(transformedName);
        if (!player && !serverPlayer && !mekanismPackets) {
            return bytes;
        }
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String methodName, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor method = super.visitMethod(access, methodName, descriptor, signature, exceptions);
                if (mekanismPackets && methodName.equals("handlePacket") && (access & Opcodes.ACC_STATIC) != 0
                        && Type.getArgumentTypes(descriptor).length == 2
                        && descriptor.startsWith("(Ljava/lang/Runnable;")) {
                    return new MethodVisitor(Opcodes.ASM9, method) {
                        @Override public void visitCode() {
                            super.visitCode();
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            super.visitVarInsn(Opcodes.ALOAD, 1);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,
                                    "com/cogwork/cogworkbridge/content/contraptions/bearing/ContraptionGuiProxy",
                                    "contextualize", "(Ljava/lang/Runnable;Ljava/lang/Object;)Ljava/lang/Runnable;", false);
                            super.visitVarInsn(Opcodes.ASTORE, 0);
                        }
                    };
                }
                if (player || serverPlayer) {
                    method = new MethodVisitor(Opcodes.ASM9, method) {
                        @Override public void visitMethodInsn(int opcode, String owner, String calledName,
                                                              String calledDescriptor, boolean isInterface) {
                            String mappedOwner = FMLDeobfuscatingRemapper.INSTANCE.map(owner);
                            boolean containerCall = opcode == Opcodes.INVOKEVIRTUAL
                                    && "net/minecraft/inventory/Container".equals(mappedOwner);
                            String mappedName = containerCall
                                    ? FMLDeobfuscatingRemapper.INSTANCE.mapMethodName(owner, calledName, calledDescriptor)
                                    : calledName;
                            if (containerCall && (mappedName.equals("func_75145_c") || mappedName.equals("canInteractWith"))) {
                                super.visitMethodInsn(Opcodes.INVOKESTATIC,
                                        "com/cogwork/cogworkbridge/content/contraptions/bearing/ContraptionGuiProxy",
                                        "canInteract", "(Ljava/lang/Object;Ljava/lang/Object;)Z", false);
                            } else {
                                super.visitMethodInsn(opcode, owner, calledName, calledDescriptor, isInterface);
                            }
                        }
                    };
                }
                if (!player || !methodName.equals("openGui") || (access & Opcodes.ACC_STATIC) != 0
                        || Type.getArgumentTypes(descriptor).length != 6) return method;
                return new MethodVisitor(Opcodes.ASM9, method) {
                    @Override
                    public void visitCode() {
                        super.visitCode();
                        super.visitVarInsn(Opcodes.ALOAD, 0);
                        super.visitVarInsn(Opcodes.ALOAD, 1);
                        super.visitVarInsn(Opcodes.ILOAD, 2);
                        super.visitVarInsn(Opcodes.ALOAD, 3);
                        super.visitVarInsn(Opcodes.ILOAD, 4);
                        super.visitVarInsn(Opcodes.ILOAD, 5);
                        super.visitVarInsn(Opcodes.ILOAD, 6);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC,
                                "com/cogwork/cogworkbridge/content/contraptions/bearing/ContraptionForgeGuiBridge",
                                "open", "(Ljava/lang/Object;Ljava/lang/Object;ILjava/lang/Object;III)Z", false);
                        Label original = new Label();
                        super.visitJumpInsn(Opcodes.IFEQ, original);
                        super.visitInsn(Opcodes.RETURN);
                        super.visitLabel(original);
                        super.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                    }
                };
            }
        }, 0);
        return writer.toByteArray();
    }
}
