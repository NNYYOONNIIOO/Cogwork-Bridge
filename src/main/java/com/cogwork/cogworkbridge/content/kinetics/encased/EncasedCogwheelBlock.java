package com.cogwork.cogworkbridge.content.kinetics.encased;

import com.cogwork.cogworkbridge.content.kinetics.cogwheel.CogwheelBlock;
import com.cogwork.cogworkbridge.content.decoration.ConnectedTextureData;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;

/** A cog with individually configurable shaft faces. */
public final class EncasedCogwheelBlock extends CogwheelBlock {

    public static final IUnlistedProperty<Integer> OPEN_PORTS = new IUnlistedProperty<Integer>() {
        @Override public String getName() { return "open_ports"; }
        @Override public boolean isValid(Integer value) { return value != null && value >= 0 && value < 4; }
        @Override public Class<Integer> getType() { return Integer.class; }
        @Override public String valueToString(Integer value) { return value.toString(); }
    };

    public EncasedCogwheelBlock(boolean large) {
        super(large);
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new ExtendedBlockState(this, new IProperty<?>[]{AXIS},
                new IUnlistedProperty<?>[]{OPEN_PORTS, ConnectedTextureData.CONTEXT});
    }

    @Override
    public IBlockState getExtendedState(IBlockState state, IBlockAccess access, BlockPos pos) {
        TileEntity tile = access.getTileEntity(pos);
        int mask = tile instanceof EncasedCogwheelTileEntity cog ? cog.modelPorts(getAxis(state)) : 0;
        return ((IExtendedBlockState) state).withProperty(OPEN_PORTS, mask)
                .withProperty(ConnectedTextureData.CONTEXT, ConnectedTextureData.capture(access, pos, state));
    }

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.MODEL;
    }

    @Override
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT_MIPPED;
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new EncasedCogwheelTileEntity();
    }

    @Override
    public boolean hasShaftTowards(World world, BlockPos pos, IBlockState state, EnumFacing side) {
        if (side.getAxis() != getAxis(state)) {
            return false;
        }
        TileEntity tile = world.getTileEntity(pos);
        return tile instanceof EncasedCogwheelTileEntity cog && cog.isOpen(side);
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return FULL_BLOCK_AABB;
    }
}
