package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.logistics.arm.MechanicalArmItem;
import com.cogwork.cogworkbridge.content.logistics.ejector.EjectorItem;
import com.cogwork.cogworkbridge.content.contraptions.glue.client.SuperGlueOutlineRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class MechanicalArmSelectionClient {
    private MechanicalArmSelectionClient() { }
    @SubscribeEvent public static void render(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft(); Entity camera = mc.getRenderViewEntity();
        if (mc.world == null || mc.player == null || camera == null) return;
        ItemStack stack = mc.player.getHeldItemMainhand();
        if (!(stack.getItem() instanceof MechanicalArmItem || stack.getItem() instanceof EjectorItem)) stack = mc.player.getHeldItemOffhand();
        if (!(stack.getItem() instanceof MechanicalArmItem || stack.getItem() instanceof EjectorItem)) return;
        NBTTagList list = stack.getItem() instanceof MechanicalArmItem ? MechanicalArmItem.selectedPoints(stack) : new NBTTagList();
        BlockPos ejectorTarget = stack.getItem() instanceof EjectorItem ? EjectorItem.selectedTarget(stack, mc.world.provider.getDimension()) : null;
        double p = event.getPartialTicks();
        double cx = camera.lastTickPosX + (camera.posX-camera.lastTickPosX)*p;
        double cy = camera.lastTickPosY + (camera.posY-camera.lastTickPosY)*p;
        double cz = camera.lastTickPosZ + (camera.posZ-camera.lastTickPosZ)*p;
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound point = list.getCompoundTagAt(i);
            if (point.getInteger("Dimension") != mc.world.provider.getDimension()) continue;
            BlockPos pos = BlockPos.fromLong(point.getLong("Pos"));
            AxisAlignedBB box = new AxisAlignedBB(pos).grow(0.004).offset(-cx, -cy, -cz);
            boolean output = point.getInteger("Mode") == 1;
            SuperGlueOutlineRenderer.renderFrame(box, output ? 1F : 0.3F, output ? 0.55F : 1F, 0.2F, 0.8F, 1F / 16F);
        }
        if (ejectorTarget != null) SuperGlueOutlineRenderer.renderFrame(
                new AxisAlignedBB(ejectorTarget).grow(0.004).offset(-cx, -cy, -cz), 1F, 0.65F, 0.15F, 0.8F, 1F / 16F);
    }
}
