package com.cogwork.cogworkbridge.content.contraptions.actors.roller;

import com.cogwork.cogworkbridge.content.contraptions.actors.AttachedActorBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public final class MechanicalRollerBlock extends AttachedActorBlock {
    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing face,
                                            float hitX, float hitY, float hitZ, int meta,
                                            EntityLivingBase placer) {
        return getDefaultState().withProperty(FACING, placer.getHorizontalFacing().getOpposite());
    }

    @Override
    public boolean canPlaceBlockOnSide(World world, BlockPos pos, EnumFacing side) {
        return canPlaceBlockAt(world, pos);
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state,
                                EntityLivingBase placer, ItemStack stack) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof MechanicalRollerTileEntity roller) {
            roller.copyAdjacentSettings();
        }
    }

    public static Vec3d settingCenter(IBlockState state, BlockPos pos, boolean material) {
        EnumFacing facing = state.getValue(FACING);
        EnumFacing right = facing.rotateY();
        double side = (material ? 3.0D : -3.0D) / 16.0D;
        return new Vec3d(pos.getX() + 0.5D + right.getXOffset() * side - facing.getXOffset() * 3.0D / 16.0D,
                pos.getY() + 1.002D,
                pos.getZ() + 0.5D + right.getZOffset() * side - facing.getZOffset() * 3.0D / 16.0D);
    }

    public static boolean hitsSetting(IBlockState state, BlockPos pos, EnumFacing side,
                                      Vec3d hit, boolean material) {
        if (side != EnumFacing.UP || hit == null) {
            return false;
        }
        Vec3d center = settingCenter(state, pos, material);
        return Math.abs(hit.x - center.x) <= 2.25D / 16.0D
                && Math.abs(hit.z - center.z) <= 2.25D / 16.0D;
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                    EntityPlayer player, EnumHand hand, EnumFacing side,
                                    float hitX, float hitY, float hitZ) {
        Vec3d hit = new Vec3d(pos.getX() + hitX, pos.getY() + hitY, pos.getZ() + hitZ);
        if (player.isSneaking() || !hitsSetting(state, pos, side, hit, true)) {
            return false;
        }
        ItemStack held = player.getHeldItem(hand);
        if (hand == EnumHand.MAIN_HAND && held.isEmpty() && !player.getHeldItemOffhand().isEmpty()) {
            return false;
        }
        if (!(world.getTileEntity(pos) instanceof MechanicalRollerTileEntity roller)
                || !roller.isValidMaterial(held)) {
            return false;
        }
        if (!world.isRemote) {
            roller.setMaterial(held);
        }
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new MechanicalRollerTileEntity();
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return FULL_BLOCK_AABB;
    }
}
