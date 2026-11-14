package com.cogwork.cogworkbridge.content.contraptions.elevator;

import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Pulley casing: metadata stores only its horizontal orientation. */
public final class ElevatorPulleyBlock extends KineticBlock {
    public static final PropertyDirection FACING = PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);

    public ElevatorPulleyBlock() {
        super(Material.IRON);
        setHardness(2F);
        setResistance(6F);
        setLightOpacity(0);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }
    @Override public boolean hasAxis() { return false; }
    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, FACING); }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(FACING).getHorizontalIndex(); }
    @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(meta)); }
    @Override public IBlockState withRotation(IBlockState state, Rotation rotation) { return state.withProperty(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side, float x, float y, float z, int meta, EntityLivingBase placer) {
        EnumFacing preferred = getPreferredShaftFacing(world, pos);
        return getDefaultState().withProperty(FACING, preferred != null && preferred.getAxis().isHorizontal()
                ? preferred.rotateYCCW() : placer.getHorizontalFacing().getOpposite());
    }
    @Override public EnumFacing.Axis getAxis(IBlockState state) { return state.getValue(FACING).rotateY().getAxis(); }
    @Override public boolean hasShaftTowards(World world, BlockPos pos, IBlockState state, EnumFacing side) { return side.getAxis() == getAxis(state); }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new ElevatorPulleyTileEntity(); }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public BlockRenderLayer getRenderLayer() { return BlockRenderLayer.CUTOUT_MIPPED; }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                               EnumHand hand, EnumFacing side, float x, float y, float z) {
        if (player.isSneaking() || !player.getHeldItem(hand).isEmpty()) return false;
        if (!world.isRemote && world.getTileEntity(pos) instanceof ElevatorPulleyTileEntity pulley) pulley.clicked();
        return true;
    }
    @Override public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof ElevatorPulleyTileEntity pulley) pulley.removePulley();
        super.breakBlock(world, pos, state);
    }
    private static final java.util.Map<net.minecraft.util.EnumFacing, com.cogwork.cogworkbridge.util.BlockShape> COLLISION = com.cogwork.cogworkbridge.util.BlockShape.pixels(0,0,0,16,16,2, 0,0,14,16,16,16, 2,0,2,14,14,14).horizontal(net.minecraft.util.EnumFacing.EAST);

    private static com.cogwork.cogworkbridge.util.BlockShape collisionShape(net.minecraft.block.state.IBlockState state) { return COLLISION.get(state.getValue(FACING)); }
    @Override public void addCollisionBoxToList(net.minecraft.block.state.IBlockState state, net.minecraft.world.World world,
            net.minecraft.util.math.BlockPos pos, net.minecraft.util.math.AxisAlignedBB query, java.util.List<net.minecraft.util.math.AxisAlignedBB> boxes,
            @javax.annotation.Nullable net.minecraft.entity.Entity entity, boolean actual) { collisionShape(state).addCollisionBoxes(pos, query, boxes); }

    @Override public net.minecraft.util.math.AxisAlignedBB getBoundingBox(net.minecraft.block.state.IBlockState state,
            net.minecraft.world.IBlockAccess world, net.minecraft.util.math.BlockPos pos) { return collisionShape(state).bounds(); }
    @Override @javax.annotation.Nullable public net.minecraft.util.math.RayTraceResult collisionRayTrace(net.minecraft.block.state.IBlockState state,
            net.minecraft.world.World world, net.minecraft.util.math.BlockPos pos, net.minecraft.util.math.Vec3d start,
            net.minecraft.util.math.Vec3d end) { return collisionShape(state).rayTrace(pos, start, end); }
}
