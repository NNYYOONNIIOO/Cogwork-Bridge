package com.cogwork.cogworkbridge.content.kinetics.shaft;

import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import com.cogwork.cogworkbridge.content.kinetics.placement.KineticPlacementContext;
import com.cogwork.cogworkbridge.content.kinetics.placement.KineticPlacementRules;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * A shaft transfers rotation between the two faces of its axis.
 *
 * <p>Single registry name, single item, exactly one metadata property (the axis).
 * Every casing variant will be a separate registry name in later phases.
 */
public class ShaftBlock extends KineticBlock {

    protected static final double CORE = 3.0 / 16.0;

    public ShaftBlock() {
        super(Material.ROCK, "cogworkbridge.shaft");
        setHardness(0.5F);
        setResistance(5.0F);
        setDefaultState(getDefaultState().withProperty(AXIS, EnumFacing.Axis.Y));
    }

    @Override
    public boolean hasAxis() {
        return true;
    }

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing,
                                            float hitX, float hitY, float hitZ, int meta,
                                            net.minecraft.entity.EntityLivingBase placer) {
        EnumFacing.Axis forced = KineticPlacementContext.getForcedAxis();
        return getDefaultState().withProperty(AXIS, forced != null ? forced
                : KineticPlacementRules.placementAxis(world, pos, facing, placer, this));
    }

    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta) {
        return new ShaftTileEntity();
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing face, float hitX, float hitY, float hitZ) {
        return com.cogwork.cogworkbridge.content.kinetics.encased.Encasing
                .tryApply(world, pos, state, player, hand);
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
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return boundingBoxFor(getAxis(state));
    }

    public static AxisAlignedBB boundingBoxFor(EnumFacing.Axis axis) {
        switch (axis) {
            case X:
                return new AxisAlignedBB(0.0D, 0.5D - CORE, 0.5D - CORE, 1.0D, 0.5D + CORE, 0.5D + CORE);
            case Z:
                return new AxisAlignedBB(0.5D - CORE, 0.5D - CORE, 0.0D, 0.5D + CORE, 0.5D + CORE, 1.0D);
            case Y:
            default:
                return new AxisAlignedBB(0.5D - CORE, 0.0D, 0.5D - CORE, 0.5D + CORE, 1.0D, 0.5D + CORE);
        }
    }
}
