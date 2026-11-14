package com.cogwork.cogworkbridge.content.contraptions.bearing;

import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Mechanical bearing: rotates the attached contraption around its facing axis.
 *
 * <p>Metadata carries <em>facing only</em> (all six directions). Whether the
 * bearing is currently assembled is a tile entity flag, never a metadata bit —
 * see docs/BLOCK-META-CONTRACT.md.
 */
public class MechanicalBearingBlock extends KineticBlock implements ITileEntityProvider {

    /**
     * Assembled state is NOT metadata: it lives in the tile entity so that the block
     * keeps exactly one property (facing) and six states.
     */
    public static final PropertyDirection FACING = PropertyDirection.create("facing");

    public MechanicalBearingBlock() {
        super(Material.WOOD, "cogworkbridge.mechanical_bearing");
        setHardness(1.0F);
        setResistance(8.0F);
        setDefaultState(getDefaultState().withProperty(FACING, EnumFacing.NORTH));
    }

    @Override
    public boolean hasAxis() {
        return false;
    }

    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta) {
        return new MechanicalBearingTileEntity();
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        EnumFacing[] values = EnumFacing.values();
        return getDefaultState().withProperty(FACING, values[Math.floorMod(meta, values.length)]);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).ordinal();
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing,
                                            float hitX, float hitY, float hitZ, int meta,
                                            EntityLivingBase placer) {
        EnumFacing preferred = placer.isSneaking() ? null : getPreferredShaftFacing(world, pos);
        return getDefaultState().withProperty(FACING, preferred != null
                ? preferred.getOpposite() : getPlacementFacing(placer, placer.isSneaking()));
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (player.isSneaking() || !player.getHeldItem(hand).isEmpty()
                || !(world.getTileEntity(pos) instanceof MechanicalBearingTileEntity)) {
            return false;
        }
        if (!world.isRemote) {
            MechanicalBearingTileEntity bearing = (MechanicalBearingTileEntity) world.getTileEntity(pos);
            bearing.toggleAwaitingUpdate();
        }
        return true;
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof MechanicalBearingTileEntity) {
            MechanicalBearingTileEntity bearing =
                    (MechanicalBearingTileEntity) world.getTileEntity(pos);
            if (!bearing.emergencyDisassemble()) {
                com.cogwork.cogworkbridge.CogworkBridge.LOGGER.error(
                        "Could not restore bearing contraption at {}; keeping the bearing intact",
                        pos);
                return;
            }
        }
        super.breakBlock(world, pos, state);
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
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.MODEL;
    }

    /** The axis the bearing spins around: the facing axis. */
    @Override
    public EnumFacing.Axis getAxis(IBlockState state) {
        return state.getValue(FACING).getAxis();
    }

    /** The input shaft enters through the face opposite the bearing's FACING. */
    @Override
    public boolean hasShaftTowards(World world, BlockPos pos, IBlockState state, EnumFacing side) {
        return side == state.getValue(FACING).getOpposite();
    }

    @Override
    public EnumFacing getShaftFacing(World world, BlockPos pos, IBlockState state) {
        return state.getValue(FACING).getOpposite();
    }
}
