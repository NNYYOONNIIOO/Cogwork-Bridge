package com.cogwork.cogworkbridge.content.kinetics.chainconveyor;

import com.cogwork.cogworkbridge.Reference;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid=Reference.MOD_ID)
public final class ChainSelectionEvents {
    private ChainSelectionEvents() { }
    /** Include the rim when a 1.12 collision scan does not visit the wheel's anchor block. */
    @SubscribeEvent public static void collision(net.minecraftforge.event.world.GetCollisionBoxesEvent event) {
        net.minecraft.util.math.AxisAlignedBB query = event.getAabb();
        int minX = net.minecraft.util.math.MathHelper.floor(query.minX) - 1;
        int maxX = net.minecraft.util.math.MathHelper.floor(query.maxX) + 1;
        int minY = net.minecraft.util.math.MathHelper.floor(query.minY) - 1;
        int maxY = net.minecraft.util.math.MathHelper.floor(query.maxY) + 1;
        int minZ = net.minecraft.util.math.MathHelper.floor(query.minZ) - 1;
        int maxZ = net.minecraft.util.math.MathHelper.floor(query.maxZ) + 1;
        // Entity movement queries are small; avoid scanning arbitrary structure-sized boxes.
        if ((long) (maxX-minX+1) * (maxY-minY+1) * (maxZ-minZ+1) > 4096) return;
        for (int x=minX; x<=maxX; x++) for (int y=minY; y<=maxY; y++) for (int z=minZ; z<=maxZ; z++) {
            net.minecraft.util.math.BlockPos pos = new net.minecraft.util.math.BlockPos(x,y,z);
            if (!event.getWorld().isBlockLoaded(pos)) continue;
            net.minecraft.block.state.IBlockState state = event.getWorld().getBlockState(pos);
            if (!(state.getBlock() instanceof ChainConveyorBlock)) continue;
            net.minecraft.util.math.AxisAlignedBB box = state.getCollisionBoundingBox(event.getWorld(), pos);
            if (box == null) continue;
            box = box.offset(pos);
            if (box.intersects(query) && !event.getCollisionBoxesList().contains(box)) event.getCollisionBoxesList().add(box);
        }
    }
    @SubscribeEvent public static void block(PlayerInteractEvent.RightClickBlock event) {
        if (event.getWorld().isRemote || !ChainConveyorBlock.isChain(event.getItemStack())) return;
        double reach = event.getEntityPlayer().getEntityAttribute(net.minecraft.entity.player.EntityPlayer.REACH_DISTANCE).getAttributeValue();
        if (event.getWorld().getBlockState(event.getPos()).getBlock() instanceof ChainConveyorBlock
                || ChainConveyorInteraction.traceWheel(event.getEntityPlayer(), Math.max(0D, Math.min(32D, reach))) != null) {
            event.setCancellationResult(net.minecraft.util.EnumActionResult.SUCCESS);
            event.setCanceled(true);
        }
    }
}
