package com.cogwork.cogworkbridge.content.contraptions.bearing;

import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.network.NetworkRegistry;

/** Forge's normal GUI handler with the moving world and local coordinates preserved. */
public final class ContraptionForgeGuiBridge {
    private ContraptionForgeGuiBridge() { }

    public static boolean open(Object playerObject, Object mod, int id,
                               Object worldObject, int x, int y, int z) {
        if (!(playerObject instanceof EntityPlayerMP player) || player instanceof FakePlayer
                || !(worldObject instanceof World local)) return false;
        BearingContraptionEntity entity;
        if (local instanceof FakeWorld fake
                && fake.getMobileChunk().getEntityMovingWorld() instanceof BearingContraptionEntity bearing) {
            entity = bearing;
        } else if (local instanceof ContraptionWorld carried && carried.getBearingContraption() != null) {
            entity = carried.getBearingContraption();
        } else return false;
        ModContainer owner = FMLCommonHandler.instance().findContainerFor(mod);
        if (owner == null) return false;
        BlockPos pos = new BlockPos(x, y, z);
        net.minecraft.tileentity.TileEntity tile = local.getTileEntity(pos);
        World previousWorld = player.world;
        World tileWorld = tile != null && tile.getWorld() != null ? tile.getWorld() : local;
        double oldX = player.posX, oldY = player.posY, oldZ = player.posZ;
        net.minecraft.util.math.AxisAlignedBB oldBounds = player.getEntityBoundingBox();
        net.minecraft.util.math.Vec3d localPoint = previousWorld == tileWorld ? null
                : entity.worldPointToLocal(new net.minecraft.util.math.Vec3d(oldX, oldY, oldZ));
        BlockPos anchor = entity.getAnchor();
        try {
            player.setWorld(tileWorld);
            if (localPoint != null) {
                player.posX = localPoint.x - anchor.getX();
                player.posY = localPoint.y - anchor.getY();
                player.posZ = localPoint.z - anchor.getZ();
            }
            player.setEntityBoundingBox(oldBounds.offset(player.posX - oldX, player.posY - oldY, player.posZ - oldZ));
            Container container = NetworkRegistry.INSTANCE.getRemoteGuiContainer(owner, player, id, tileWorld, x, y, z);
            if (container != null) {
                ContraptionGuiProxy.openForeignContainer(player, entity, pos, container, owner.getModId(), id);
            }
        } finally {
            player.setWorld(previousWorld);
            player.posX = oldX;
            player.posY = oldY;
            player.posZ = oldZ;
            player.setEntityBoundingBox(oldBounds);
        }
        return true;
    }
}
