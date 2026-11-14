package com.cogwork.cogworkbridge.content.display;

import com.cogwork.cogworkbridge.CogworkBridge;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;


/** Reads the block against its back face and sends data to the selected display. */
public final class DisplayLinkBlock extends BlockContainer {
    public static final PropertyDirection FACING = PropertyDirection.create("facing");
    public static final IUnlistedProperty<Boolean> POWERED = new IUnlistedProperty<Boolean>() {
        @Override public String getName() { return "powered"; }
        @Override public boolean isValid(Boolean value) { return value != null; }
        @Override public Class<Boolean> getType() { return Boolean.class; }
        @Override public String valueToString(Boolean value) { return value.toString(); }
    };
    public DisplayLinkBlock() {
        super(Material.IRON);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.UP));
        setHardness(1.5F);
    }
    @Override protected BlockStateContainer createBlockState() {
        return new ExtendedBlockState(this, new IProperty<?>[]{FACING}, new IUnlistedProperty<?>[]{POWERED});
    }
    @Override public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        return ((IExtendedBlockState) state).withProperty(POWERED,
                tile instanceof DisplayLinkTileEntity link && link.isVisuallyPowered());
    }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(FACING).getIndex(); }
    @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(FACING, EnumFacing.byIndex(meta)); }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side, float x, float y, float z,
                                                       int meta, EntityLivingBase placer) {
        return getDefaultState().withProperty(FACING, side);
    }
    @Override public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos from) {
        if (world.isRemote) world.markBlockRangeForRenderUpdate(pos, pos);
    }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new DisplayLinkTileEntity(); }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public net.minecraft.util.BlockRenderLayer getRenderLayer() { return net.minecraft.util.BlockRenderLayer.TRANSLUCENT; }
    @Override public EnumBlockRenderType getRenderType(IBlockState state) { return EnumBlockRenderType.MODEL; }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return switch (state.getValue(FACING)) {
            case UP -> new AxisAlignedBB(.125, 0, .125, .875, .375, .875);
            case DOWN -> new AxisAlignedBB(.125, .625, .125, .875, 1, .875);
            case NORTH -> new AxisAlignedBB(.125, .125, .625, .875, .875, 1);
            case SOUTH -> new AxisAlignedBB(.125, .125, 0, .875, .875, .375);
            case WEST -> new AxisAlignedBB(.625, .125, .125, 1, .875, .875);
            case EAST -> new AxisAlignedBB(0, .125, .125, .375, .875, .875);
        };
    }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                               EnumHand hand, EnumFacing side, float x, float y, float z) {
        if (player.isSneaking() || !player.getHeldItem(hand).isEmpty()) return false;
        if (world.isRemote) {
            TileEntity tile = world.getTileEntity(pos);
            if (tile instanceof DisplayLinkTileEntity link && link.hasTarget()) CogworkBridge.proxy.openDisplayLinkScreen(pos);
            else player.sendStatusMessage(new net.minecraft.util.text.TextComponentTranslation("gui.cogworkbridge.display.invalid"), true);
        }
        return true;
    }
}
