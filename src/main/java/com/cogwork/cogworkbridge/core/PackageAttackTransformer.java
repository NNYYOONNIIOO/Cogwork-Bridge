package com.cogwork.cogworkbridge.core;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraftforge.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper;
import org.apache.logging.log4j.LogManager;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/** Keeps vanilla's invalid-target check while permitting attacks on Create parcels. */
public final class PackageAttackTransformer implements IClassTransformer {
    private static final String ITEM_ENTITY = "net/minecraft/entity/item/EntityItem";
    private static final String USE_PACKET = "net/minecraft/network/play/client/CPacketUseEntity";
    private static final String PACKAGE_ENTITY =
            "com/cogwork/cogworkbridge/content/logistics/packagebox/PackageEntity";

    @Override public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !"net.minecraft.network.NetHandlerPlayServer".equals(transformedName)) return bytes;
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        int[] replacements = {0};
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public MethodVisitor visitMethod(int access, String methodName, String descriptor,
                                                       String signature, String[] exceptions) {
                MethodVisitor method = super.visitMethod(access, methodName, descriptor, signature, exceptions);
                Type[] args = Type.getArgumentTypes(descriptor);
                if (args.length != 1 || args[0].getSort() != Type.OBJECT
                        || Type.getReturnType(descriptor).getSort() != Type.VOID
                        || !USE_PACKET.equals(FMLDeobfuscatingRemapper.INSTANCE.map(args[0].getInternalName())))
                    return method;
                return new MethodVisitor(Opcodes.ASM9, method) {
                    @Override public void visitTypeInsn(int opcode, String type) {
                        if (opcode != Opcodes.INSTANCEOF
                                || !ITEM_ENTITY.equals(FMLDeobfuscatingRemapper.INSTANCE.map(type))) {
                            super.visitTypeInsn(opcode, type);
                            return;
                        }
                        // (target instanceof EntityItem) && !(target instanceof PackageEntity)
                        super.visitInsn(Opcodes.DUP);
                        super.visitTypeInsn(Opcodes.INSTANCEOF, type);
                        super.visitInsn(Opcodes.SWAP);
                        super.visitTypeInsn(Opcodes.INSTANCEOF, PACKAGE_ENTITY);
                        super.visitInsn(Opcodes.ICONST_1);
                        super.visitInsn(Opcodes.IXOR);
                        super.visitInsn(Opcodes.IAND);
                        replacements[0]++;
                    }
                };
            }
        }, 0);
        if (replacements[0] != 1) {
            LogManager.getLogger("CogworkBridgeCore").error(
                    "Could not safely identify the package attack check (matches: {})", replacements[0]);
            return bytes;
        }
        return writer.toByteArray();
    }
}
