package com.cogwork.cogworkbridge.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Adapts vanilla's fixed furnace time for the upstream glass recycling recipes. */
public final class GlassFurnaceTransformer implements IClassTransformer {
    @Override public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !"net.minecraft.tileentity.TileEntityFurnace".equals(transformedName)) return bytes;
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        boolean[] found = {false};
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public MethodVisitor visitMethod(int access, String methodName, String descriptor,
                                                       String signature, String[] exceptions) {
                MethodVisitor method = super.visitMethod(access, methodName, descriptor, signature, exceptions);
                boolean mapped = (methodName.equals("getCookTime") || methodName.equals("func_174904_a"))
                        && descriptor.equals("(Lnet/minecraft/item/ItemStack;)I");
                boolean obfuscated = methodName.equals("a") && descriptor.equals("(Laip;)I");
                if (!mapped && !obfuscated) return method;
                found[0] = true;
                return new MethodVisitor(Opcodes.ASM9, method) {
                    @Override public void visitInsn(int opcode) {
                        if (opcode == Opcodes.IRETURN) {
                            super.visitVarInsn(Opcodes.ALOAD, 1);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,
                                    "com/cogwork/cogworkbridge/content/decoration/palette/PaletteRecipes",
                                    "glassCookTime", "(ILjava/lang/Object;)I", false);
                        }
                        super.visitInsn(opcode);
                    }
                };
            }
        }, 0);
        return found[0] ? writer.toByteArray() : bytes;
    }
}
