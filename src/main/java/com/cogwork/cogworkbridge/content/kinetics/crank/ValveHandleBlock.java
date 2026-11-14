package com.cogwork.cogworkbridge.content.kinetics.crank;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.nbt.NBTTagCompound;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.kinetic.KineticNetworkManager;

public final class ValveHandleBlock extends HandCrankBlock {
    private final int color;
    public ValveHandleBlock() { this(-1); }
    public ValveHandleBlock(int color) { this.color = color; }
    public int getColor() { return color; }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new ValveHandleTileEntity(); }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
            EnumHand hand, EnumFacing side, float x, float y, float z) {
        ItemStack held = player.getHeldItem(hand);
        if (held.getItem() == Items.DYE) {
            int next = EnumDyeColor.byDyeDamage(held.getMetadata()).getMetadata();
            if (next == color) return true;
            if (!world.isRemote) {
                TileEntity old = world.getTileEntity(pos);
                NBTTagCompound tag = old == null ? null : old.writeToNBT(new NBTTagCompound());
                world.setBlockState(pos, AllBlocks.COLOR_VALVE_HANDLES[next].getDefaultState().withProperty(FACING, state.getValue(FACING)), 3);
                TileEntity replacement = world.getTileEntity(pos);
                if (tag != null && replacement instanceof ValveHandleTileEntity valve) {
                    valve.readFromNBT(tag); valve.markDirty(); valve.refreshKinetics();
                }
                KineticNetworkManager.discard(world);
            }
            return true;
        }
        return super.onBlockActivated(world, pos, state, player, hand, side, x, y, z);
    }
}
