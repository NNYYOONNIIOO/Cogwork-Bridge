package com.cogwork.cogworkbridge.content.contraptions.chassis;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;

/** Redstone controlled sticky piston used by contraption assembly. */
public final class StickerBlock extends BlockContainer {
    public static final PropertyDirection FACING = PropertyDirection.create("facing");
    public static final IUnlistedProperty<Boolean> POWERED = new IUnlistedProperty<Boolean>() {
        @Override public String getName() { return "powered"; }
        @Override public boolean isValid(Boolean value) { return value != null; }
        @Override public Class<Boolean> getType() { return Boolean.class; }
        @Override public String valueToString(Boolean value) { return value.toString(); }
    };

    public StickerBlock() {
        super(Material.ROCK);
        setHardness(1.5F);
        setResistance(6.0F);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new ExtendedBlockState(this, new IProperty<?>[]{FACING}, new IUnlistedProperty<?>[]{POWERED});
    }

    @Override
    public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        return ((IExtendedBlockState) state).withProperty(POWERED,
                tile instanceof StickerTileEntity && ((StickerTileEntity) tile).isPowered());
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side,
                                            float hitX, float hitY, float hitZ, int meta,
                                            EntityLivingBase placer) {
        EnumFacing looking = com.cogwork.cogworkbridge.kinetic.KineticBlock.getPlacementFacing(placer);
        EnumFacing facing = placer.isSneaking() ? looking : looking.getOpposite();
        return getDefaultState().withProperty(FACING, facing);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.byIndex(Math.floorMod(meta, 6)));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getIndex();
    }

    @Override
    public IBlockState withRotation(IBlockState state, Rotation rotation) {
        return state.withProperty(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public IBlockState withMirror(IBlockState state, Mirror mirror) {
        return state.withRotation(mirror.toRotation(state.getValue(FACING)));
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new StickerTileEntity();
    }

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.MODEL;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    public net.minecraft.util.BlockRenderLayer getRenderLayer() {
        return net.minecraft.util.BlockRenderLayer.CUTOUT;
    }

    @Override
    public void onFallenUpon(World world, BlockPos pos, net.minecraft.entity.Entity entity, float distance) {
        if (world.getBlockState(pos).getValue(FACING) == EnumFacing.UP && !entity.isSneaking()) {
            entity.fall(distance, 1.0F);
        } else {
            super.onFallenUpon(world, pos, entity, distance);
        }
    }

    @Override
    public void onLanded(World world, net.minecraft.entity.Entity entity) {
        BlockPos below = new BlockPos(entity.posX, entity.getEntityBoundingBox().minY - 0.2D, entity.posZ);
        IBlockState state = world.getBlockState(below);
        if (state.getBlock() == this && state.getValue(FACING) == EnumFacing.UP && !entity.isSneaking()) {
            if (entity.motionY < 0.0D)
                entity.motionY = -entity.motionY * (entity instanceof EntityLivingBase ? 1.0D : 0.8D);
        } else {
            super.onLanded(world, entity);
        }
    }

    @Override
    public void onEntityWalk(World world, BlockPos pos, net.minecraft.entity.Entity entity) {
        if (world.getBlockState(pos).getValue(FACING) == EnumFacing.UP
                && Math.abs(entity.motionY) < 0.1D && !entity.isSneaking()) {
            double slowdown = 0.4D + Math.abs(entity.motionY) * 0.2D;
            entity.motionX *= slowdown;
            entity.motionZ *= slowdown;
        }
        super.onEntityWalk(world, pos, entity);
    }

    @Override
    public boolean addLandingEffects(IBlockState state, net.minecraft.world.WorldServer world, BlockPos pos,
                                     IBlockState landedState, EntityLivingBase entity, int count) {
        if (state.getValue(FACING) != EnumFacing.UP) return false;
        world.spawnParticle(net.minecraft.util.EnumParticleTypes.BLOCK_CRACK,
                entity.posX, entity.posY, entity.posZ, count, 0D, 0D, 0D, .15D,
                Block.getStateId(net.minecraft.init.Blocks.SLIME_BLOCK.getDefaultState()));
        return true;
    }

    @Override
    public boolean addRunningEffects(IBlockState state, World world, BlockPos pos, net.minecraft.entity.Entity entity) {
        if (state.getValue(FACING) != EnumFacing.UP) return false;
        world.spawnParticle(net.minecraft.util.EnumParticleTypes.BLOCK_CRACK,
                entity.posX + (world.rand.nextFloat() - .5D) * entity.width,
                entity.posY + .1D, entity.posZ + (world.rand.nextFloat() - .5D) * entity.width,
                -entity.motionX * 4D, 1.5D, -entity.motionZ * 4D,
                Block.getStateId(net.minecraft.init.Blocks.SLIME_BLOCK.getDefaultState()));
        return true;
    }

    @Override
    public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
        super.onBlockAdded(world, pos, state);
        TileEntity tile = world.getTileEntity(pos);
        if (!world.isRemote && tile instanceof StickerTileEntity sticker)
            sticker.initializePower(world.isBlockPowered(pos));
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos,
                                Block block, BlockPos fromPos) {
        updatePower(world, pos);
    }

    private static void updatePower(World world, BlockPos pos) {
        if (world.isRemote) return;
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof StickerTileEntity sticker) {
            sticker.setPowered(world.isBlockPowered(pos));
        }
    }
}
