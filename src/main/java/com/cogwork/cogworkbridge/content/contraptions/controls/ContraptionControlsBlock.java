package com.cogwork.cogworkbridge.content.contraptions.controls;

import com.cogwork.cogworkbridge.registry.AllItems;
import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/** Button and optional actor filter for machines carried by a contraption. */
public final class ContraptionControlsBlock extends BlockContainer {
    public static final PropertyDirection FACING = PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);
    private static final AxisAlignedBB NORTH = new AxisAlignedBB(0, 0, .25D, 1, 1, 1);
    private static final AxisAlignedBB SOUTH = new AxisAlignedBB(0, 0, 0, 1, 1, .75D);
    private static final AxisAlignedBB WEST = new AxisAlignedBB(.25D, 0, 0, 1, 1, 1);
    private static final AxisAlignedBB EAST = new AxisAlignedBB(0, 0, 0, .75D, 1, 1);

    public ContraptionControlsBlock() {
        super(Material.IRON);
        setHardness(2F);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }
    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, FACING); }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(FACING).getHorizontalIndex(); }
    @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(meta)); }
    @Override public IBlockState withRotation(IBlockState state, Rotation rotation) { return state.withProperty(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side, float x, float y, float z, int meta, EntityLivingBase placer) {
        return getDefaultState().withProperty(FACING, placer.isSneaking() ? placer.getHorizontalFacing() : placer.getHorizontalFacing().getOpposite());
    }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new ContraptionControlsTileEntity(); }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public EnumBlockRenderType getRenderType(IBlockState state) { return EnumBlockRenderType.MODEL; }
    @Override public net.minecraft.util.BlockRenderLayer getRenderLayer() { return net.minecraft.util.BlockRenderLayer.CUTOUT; }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return switch (state.getValue(FACING)) { case NORTH -> NORTH; case SOUTH -> SOUTH; case WEST -> WEST; default -> EAST; };
    }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                               EnumHand hand, EnumFacing side, float x, float y, float z) {
        ItemStack held = player.getHeldItem(hand);
        if (held.getItem() == AllItems.WRENCH) return false;
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof ContraptionControlsTileEntity controls)) return false;
        // The inclined slot configures the filter; the rest is the control button.
        if (!(world instanceof FakeWorld)) {
            if (isFilterSlot(state.getValue(FACING), x, y, z)
                    && (held.isEmpty() || ContraptionControlsTileEntity.isControllable(held))) {
                if (!world.isRemote) controls.setFilter(held);
                return true;
            }
            if (!held.isEmpty()) return false;
            if (!world.isRemote) {
                controls.toggle();
                player.sendStatusMessage(new net.minecraft.util.text.TextComponentTranslation(
                        controls.isDisabled() ? "gui.cogworkbridge.controls.disabled" : "gui.cogworkbridge.controls.enabled"), true);
                world.playSound(null, pos, net.minecraft.init.SoundEvents.UI_BUTTON_CLICK,
                        net.minecraft.util.SoundCategory.BLOCKS, 1F, controls.isDisabled() ? .8F : 1.5F);
            }
            return true;
        }
        FakeWorld fake = (FakeWorld) world;
        if (fake.getMobileChunk().getEntityMovingWorld() instanceof
                com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity entity
                && entity.isElevator()) {
            if (!world.isRemote && fake.getParentWorld().getTileEntity(entity.getElevatorController()) instanceof
                    com.cogwork.cogworkbridge.content.contraptions.elevator.ElevatorPulleyTileEntity pulley)
                pulley.confirmFloor();
            return true;
        }
        if (!world.isRemote) {
            controls.toggle();
            player.sendStatusMessage(new net.minecraft.util.text.TextComponentTranslation(
                    controls.isDisabled() ? "gui.cogworkbridge.controls.disabled" : "gui.cogworkbridge.controls.enabled"), true);
        }
        return true;
    }

    private static boolean isFilterSlot(EnumFacing facing, float x, float y, float z) {
        float lateral, depth;
        switch (facing) {
            case EAST -> { lateral = z; depth = x; }
            case SOUTH -> { lateral = 1F - x; depth = 1F - z; }
            case WEST -> { lateral = 1F - z; depth = 1F - x; }
            default -> { lateral = x; depth = z; }
        }
        return lateral >= .30F && lateral <= .70F && y >= .68F && depth >= .20F && depth <= .57F;
    }
    @Override public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos from) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof ContraptionControlsTileEntity controls) controls.updatePower();
    }
    private static final java.util.Map<EnumFacing, com.cogwork.cogworkbridge.util.BlockShape> COLLISION = com.cogwork.cogworkbridge.util.BlockShape.pixels(
            0,0,6,2,16,16, 14,0,6,16,16,16, 0,0,14,16,16,16, 0,0,7,16,12,16).horizontal(EnumFacing.NORTH);
    @Override public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos, AxisAlignedBB query,
            java.util.List<AxisAlignedBB> boxes, @javax.annotation.Nullable net.minecraft.entity.Entity entity, boolean actual) {
        COLLISION.get(state.getValue(FACING)).addCollisionBoxes(pos, query, boxes);
    }
}
