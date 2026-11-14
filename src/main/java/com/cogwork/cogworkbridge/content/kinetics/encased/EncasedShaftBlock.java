package com.cogwork.cogworkbridge.content.kinetics.encased;

import com.cogwork.cogworkbridge.content.kinetics.shaft.ShaftBlock;
import com.cogwork.cogworkbridge.content.decoration.ConnectedTextureData;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/** The casing is static; the shaft continues to transmit on both axis faces. */
public final class EncasedShaftBlock extends ShaftBlock {

    @Override protected BlockStateContainer createBlockState() {
        return new net.minecraftforge.common.property.ExtendedBlockState(this, new IProperty<?>[]{AXIS},
                new net.minecraftforge.common.property.IUnlistedProperty<?>[]{ConnectedTextureData.CONTEXT});
    }

    @Override public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        return ((net.minecraftforge.common.property.IExtendedBlockState) state)
                .withProperty(ConnectedTextureData.CONTEXT, ConnectedTextureData.capture(world, pos, state));
    }

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.MODEL;
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new EncasedShaftTileEntity();
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return FULL_BLOCK_AABB;
    }
}
