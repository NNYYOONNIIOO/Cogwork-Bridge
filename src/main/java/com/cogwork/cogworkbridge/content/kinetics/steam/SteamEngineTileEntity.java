package com.cogwork.cogworkbridge.content.kinetics.steam;

import com.cogwork.cogworkbridge.content.fluids.FluidTankTileEntity;
import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import com.cogwork.cogworkbridge.kinetic.KineticNetworkManager;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

public final class SteamEngineTileEntity extends TileEntity implements ITickable {
    private boolean reverse;
    public boolean isReversed() { return reverse; }
    public void setReversed(boolean reverse) { this.reverse = reverse; markDirty(); if (world != null && !world.isRemote) world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3); }
    public EnumFacing facing() { return world.getBlockState(pos).getValue(SteamEngineBlock.FACING); }
    public BlockPos shaftPos() { return pos.offset(facing(), 2); }
    @Override public void update() {
        if (world == null || !(world.getBlockState(pos).getBlock() instanceof SteamEngineBlock)) return;
        if (world.isRemote) {
            if (world.getTotalWorldTime() % 10 == 0 && world.getTileEntity(shaftPos()) instanceof PoweredShaftTileEntity shaft && shaft.getSpeed() != 0) {
                EnumFacing face = facing();
                world.spawnParticle(net.minecraft.util.EnumParticleTypes.CLOUD,
                        pos.getX() + .5 + face.getXOffset() * .45, pos.getY() + .5 + face.getYOffset() * .45,
                        pos.getZ() + .5 + face.getZOffset() * .45,
                        face.getXOffset() * .03, face.getYOffset() * .03, face.getZOffset() * .03);
            }
            return;
        }
        EnumFacing facing = facing(); BlockPos tankPos = pos.offset(facing.getOpposite()); BlockPos shaftPos = shaftPos();
        if (!world.isBlockLoaded(tankPos) || !world.isBlockLoaded(shaftPos)) return;
        TileEntity tank = world.getTileEntity(tankPos);
        if (!(tank instanceof FluidTankTileEntity boiler)) { releaseShaft(); return; }
        IBlockState shaft = world.getBlockState(shaftPos);
        if (!(shaft.getBlock() instanceof com.cogwork.cogworkbridge.content.kinetics.shaft.ShaftBlock)
                || shaft.getValue(KineticBlock.AXIS) == facing.getAxis()) { releaseShaft(); return; }
        if (shaft.getBlock() == AllBlocks.SHAFT) {
            world.setBlockState(shaftPos, AllBlocks.POWERED_SHAFT.getDefaultState().withProperty(KineticBlock.AXIS, shaft.getValue(KineticBlock.AXIS)), 3);
            KineticNetworkManager.discard(world);
        }
        TileEntity target = world.getTileEntity(shaftPos);
        if (target instanceof PoweredShaftTileEntity powered && powered.canClaim(pos)) {
            EnumFacing.Axis axis = shaft.getValue(KineticBlock.AXIS);
            int direction = axis == EnumFacing.Axis.Y ? 1 : facing.getAxisDirection() == EnumFacing.AxisDirection.NEGATIVE ? -1 : 1;
            if (axis == EnumFacing.Axis.Z) direction = -direction;
            if (reverse) direction = -direction;
            powered.power(pos, direction, boiler.getBoilerEfficiency());
            if (boiler.getBoilerEfficiency() > 0) com.cogwork.cogworkbridge.content.advancement.AllAdvancements.awardAt(world, pos, "steam_engine");
        }
    }
    public void releaseShaft() {
        if (world == null || world.isRemote || !(world.getBlockState(pos).getBlock() instanceof SteamEngineBlock)) return;
        BlockPos target = shaftPos();
        if (world.isBlockLoaded(target) && world.getTileEntity(target) instanceof PoweredShaftTileEntity powered) powered.release(pos);
    }
    @Override public AxisAlignedBB getRenderBoundingBox() { return new AxisAlignedBB(pos).grow(3); }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) { super.writeToNBT(tag); tag.setBoolean("Reverse", reverse); return tag; }
    @Override public void readFromNBT(NBTTagCompound tag) { super.readFromNBT(tag); reverse = tag.getBoolean("Reverse"); }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) { readFromNBT(packet.getNbtCompound()); }
}
