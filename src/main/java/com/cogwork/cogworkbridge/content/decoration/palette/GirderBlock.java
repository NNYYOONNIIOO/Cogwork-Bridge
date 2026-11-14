package com.cogwork.cogworkbridge.content.decoration.palette;

import com.cogwork.cogworkbridge.registry.AllItems;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class GirderBlock extends Block {
    public static final PropertyEnum<EnumFacing.Axis> AXIS = PropertyEnum.create("axis", EnumFacing.Axis.class);
    public static final PropertyBool X = PropertyBool.create("x"), Z = PropertyBool.create("z"), TOP = PropertyBool.create("top"), BOTTOM = PropertyBool.create("bottom");
    private static final AxisAlignedBB Y_BAR = new AxisAlignedBB(.25, 0, .25, .75, 1, .75);
    private static final AxisAlignedBB X_BAR = new AxisAlignedBB(0, .125, .25, 1, .875, .75);
    private static final AxisAlignedBB Z_BAR = new AxisAlignedBB(.25, .125, 0, .75, .875, 1);
    private static final AxisAlignedBB CROSS_CENTER = new AxisAlignedBB(3D / 16D, 0, 3D / 16D, 13D / 16D, 1, 13D / 16D);
    public GirderBlock() {
        super(Material.IRON); setHardness(3F); setHarvestLevel("pickaxe", 0);
        setDefaultState(blockState.getBaseState().withProperty(AXIS, EnumFacing.Axis.Y).withProperty(X, false).withProperty(Z, false).withProperty(TOP, false).withProperty(BOTTOM, false));
    }
    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, AXIS, X, Z, TOP, BOTTOM); }
    @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(AXIS, meta == 1 ? EnumFacing.Axis.X : meta == 2 ? EnumFacing.Axis.Z : EnumFacing.Axis.Y); }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(AXIS) == EnumFacing.Axis.X ? 1 : state.getValue(AXIS) == EnumFacing.Axis.Z ? 2 : 0; }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side, float x, float y, float z, int meta, EntityLivingBase placer) {
        return getDefaultState().withProperty(AXIS, side.getAxis());
    }
    @Override public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
        EnumFacing.Axis axis = state.getValue(AXIS);
        boolean x = axis == EnumFacing.Axis.X || connects(world, pos, EnumFacing.EAST) || connects(world, pos, EnumFacing.WEST);
        boolean z = axis == EnumFacing.Axis.Z || connects(world, pos, EnumFacing.NORTH) || connects(world, pos, EnumFacing.SOUTH);
        return state.withProperty(X, x).withProperty(Z, z).withProperty(TOP, supports(world, pos, EnumFacing.UP))
                .withProperty(BOTTOM, supports(world, pos, EnumFacing.DOWN));
    }
    private boolean connects(IBlockAccess world, BlockPos pos, EnumFacing side) {
        IBlockState neighbor = world.getBlockState(pos.offset(side));
        if (neighbor.getBlock() == this) return true;
        return neighbor.getBlock() instanceof GirderShaftBlock && neighbor.getValue(com.cogwork.cogworkbridge.kinetic.KineticBlock.AXIS) != side.getAxis();
    }
    static boolean supports(IBlockAccess world, BlockPos pos, EnumFacing side) {
        BlockPos other = pos.offset(side);
        IBlockState neighbor = world.getBlockState(other);
        return neighbor.getBlock() instanceof GirderBlock || neighbor.getBlock() instanceof GirderShaftBlock
                || neighbor.isSideSolid(world, other, side.getOpposite());
    }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing side, float x, float y, float z) {
        if (GirderPlacement.place(world, pos, state, player, hand, side, x, y, z)) return true;
        if (player.getHeldItem(hand).getItem() == net.minecraft.item.Item.getItemFromBlock(com.cogwork.cogworkbridge.registry.AllBlocks.SHAFT)) {
            if (!world.isRemote) {
                IBlockState actual = getActualState(state, world, pos);
                EnumFacing.Axis axis = actual.getValue(X) || side.getAxis() == EnumFacing.Axis.Z ? EnumFacing.Axis.Z : EnumFacing.Axis.X;
                Block shaft = com.cogwork.cogworkbridge.registry.BlockFamily.get("metal_girder_encased_shaft");
                if (world.setBlockState(pos, shaft.getDefaultState().withProperty(com.cogwork.cogworkbridge.kinetic.KineticBlock.AXIS, axis), 3)
                        && !player.capabilities.isCreativeMode) player.getHeldItem(hand).shrink(1);
            }
            return true;
        }
        if (player.isSneaking() || player.getHeldItem(hand).getItem() != AllItems.WRENCH) return false;
        if (!world.isRemote) world.setBlockState(pos, state.withProperty(AXIS, side.getAxis()), 3);
        return true;
    }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        state = getActualState(state, world, pos);
        if (state.getValue(X) && state.getValue(Z)) return X_BAR.union(Z_BAR).union(CROSS_CENTER);
        return state.getValue(X) ? X_BAR : state.getValue(Z) ? Z_BAR : Y_BAR;
    }
    @Override public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos, AxisAlignedBB entityBox, List<AxisAlignedBB> boxes, @Nullable Entity entity, boolean actual) {
        state = getActualState(state, world, pos);
        if (state.getValue(X)) addCollisionBoxToList(pos, entityBox, boxes, X_BAR);
        if (state.getValue(Z)) addCollisionBoxToList(pos, entityBox, boxes, Z_BAR);
        if (state.getValue(X) && state.getValue(Z)) addCollisionBoxToList(pos, entityBox, boxes, CROSS_CENTER);
        if (!state.getValue(X) && !state.getValue(Z)) addCollisionBoxToList(pos, entityBox, boxes, Y_BAR);
    }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override @SideOnly(Side.CLIENT) public BlockRenderLayer getRenderLayer() { return BlockRenderLayer.CUTOUT; }
}
