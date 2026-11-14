package com.cogwork.cogworkbridge.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.apache.logging.log4j.LogManager;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/** Lets vanilla's powered-rail search traverse the rail inside an assembler. */
public final class CartAssemblerRailTransformer implements IClassTransformer {
    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !"net.minecraft.block.BlockRailPowered".equals(transformedName)) return bytes;
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        boolean[] found = {false};
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String methodName, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor method = super.visitMethod(access, methodName, descriptor, signature, exceptions);
                boolean named = methodName.equals("isSameRailWithPower") || methodName.equals("func_176567_a");
                boolean obfuscated = methodName.equals("a") && descriptor.equals("(Lamu;Let;ZILaos$b;)Z");
                Type[] arguments = Type.getArgumentTypes(descriptor);
                if ((!named && !obfuscated) || arguments.length != 5 || !descriptor.endsWith(")Z")) return method;
                found[0] = true;
                return new MethodVisitor(Opcodes.ASM9, method) {
                    @Override public void visitCode() {
                        super.visitCode();
                        super.visitVarInsn(Opcodes.ALOAD, 0);
                        super.visitVarInsn(Opcodes.ALOAD, 1);
                        super.visitVarInsn(Opcodes.ALOAD, 2);
                        super.visitVarInsn(Opcodes.ILOAD, 3);
                        super.visitVarInsn(Opcodes.ILOAD, 4);
                        super.visitVarInsn(Opcodes.ALOAD, 5);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC,
                                "com/cogwork/cogworkbridge/content/contraptions/mounted/CartAssemblerRailPower",
                                "intercept", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;ZILjava/lang/Object;)I", false);
                        Label vanilla = new Label();
                        super.visitInsn(Opcodes.DUP);
                        super.visitJumpInsn(Opcodes.IFLT, vanilla);
                        super.visitInsn(Opcodes.IRETURN);
                        super.visitLabel(vanilla);
                        super.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[]{Opcodes.INTEGER});
                        super.visitInsn(Opcodes.POP);
                    }
                };
            }
        }, 0);
        if (!found[0]) {
            LogManager.getLogger("CogworkBridgeCore").error("Could not locate powered rail signal search");
            return bytes;
        }
        return writer.toByteArray();
    }
}
