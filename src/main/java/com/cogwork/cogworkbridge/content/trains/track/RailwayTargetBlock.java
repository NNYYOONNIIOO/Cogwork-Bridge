package com.cogwork.cogworkbridge.content.trains.track;

import com.cogwork.cogworkbridge.registry.AllItems;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** The station, signal and observer all refer to a selected track edge. */
public final class RailwayTargetBlock extends BlockContainer {
    public static final PropertyBool ACTIVE=PropertyBool.create("active"),CROSS=PropertyBool.create("cross");
    private final String kind;
    public RailwayTargetBlock(String kind) {
        super(Material.IRON);this.kind=kind;setHardness(2F);setSoundType(net.minecraft.block.SoundType.METAL);
        setDefaultState(blockState.getBaseState().withProperty(ACTIVE,false).withProperty(CROSS,false));
    }
    @Override protected BlockStateContainer createBlockState(){return new BlockStateContainer(this,ACTIVE,CROSS);}
    @Override public int getMetaFromState(IBlockState state){return 0;}
    @Override public IBlockState getStateFromMeta(int meta){return getDefaultState();}
    @Override public IBlockState getActualState(IBlockState state,IBlockAccess world,BlockPos pos){
        TileEntity tile=world.getTileEntity(pos);
        boolean active=tile instanceof RailwayTargetTile.Signal signal&&signal.isRed()
                ||tile instanceof RailwayTargetTile.Observer observer&&observer.powered();
        boolean cross=tile instanceof RailwayTargetTile.Signal signal&&signal.crossSignal();
        return state.withProperty(ACTIVE,active).withProperty(CROSS,cross);
    }
    @Override public TileEntity createNewTileEntity(World world,int meta) {
        return switch(kind) {
            case "station" -> new RailwayTargetTile.Station();
            case "signal" -> new RailwayTargetTile.Signal();
            default -> new RailwayTargetTile.Observer();
        };
    }
    @Override public EnumBlockRenderType getRenderType(IBlockState state){return EnumBlockRenderType.MODEL;}
    @Override public boolean isOpaqueCube(IBlockState state){return false;}
    @Override public boolean isFullCube(IBlockState state){return false;}
    @Override public BlockRenderLayer getRenderLayer(){return BlockRenderLayer.CUTOUT_MIPPED;}
    @Override public AxisAlignedBB getBoundingBox(IBlockState state,IBlockAccess world,BlockPos pos){
        return "station".equals(kind)?new AxisAlignedBB(0,0,0,1,.8125D,1):FULL_BLOCK_AABB;
    }
    @Override public boolean hasComparatorInputOverride(IBlockState state){return "station".equals(kind)||"signal".equals(kind);}
    @Override public int getComparatorInputOverride(IBlockState state,World world,BlockPos pos){
        TileEntity tile=world.getTileEntity(pos);
        return tile instanceof RailwayTargetTile.Station station&&station.trainPresent()
                ||tile instanceof RailwayTargetTile.Signal signal&&signal.isRed()?15:0;
    }
    @Override public boolean canProvidePower(IBlockState state){return "observer".equals(kind);}
    @Override public int getWeakPower(IBlockState state,IBlockAccess world,BlockPos pos,EnumFacing side){
        return world.getTileEntity(pos) instanceof RailwayTargetTile.Observer observer&&observer.powered()?15:0;
    }
    @Override public int getStrongPower(IBlockState state,IBlockAccess world,BlockPos pos,EnumFacing side){return getWeakPower(state,world,pos,side);}
    @Override public boolean onBlockActivated(World world,BlockPos pos,IBlockState state,EntityPlayer player,
            EnumHand hand,EnumFacing side,float x,float y,float z) {
        if(player.isSneaking())return false;
        ItemStack held=player.getHeldItem(hand);
        if(world.getTileEntity(pos) instanceof RailwayTargetTile.Observer observer && side==EnumFacing.UP
                && x>=.25F&&x<=.75F&&z>=.25F&&z<=.75F && held.getItem()!=AllItems.WRENCH) {
            if(!world.isRemote)observer.setFilter(player,hand);
            return true;
        }
        if(world.getTileEntity(pos) instanceof RailwayTargetTile.Signal signal && held.getItem()==AllItems.WRENCH) {
            if(!world.isRemote){signal.cycle();player.sendStatusMessage(new net.minecraft.util.text.TextComponentTranslation(
                    signal.crossSignal()?"cogworkbridge.track_signal.cross":"cogworkbridge.track_signal.entry"),true);}
            return true;
        }
        if(!(world.getTileEntity(pos) instanceof RailwayTargetTile.Station station) || held.getItem()==AllItems.WRENCH)return false;
        if(world.isRemote)openStation(pos);
        return true;
    }
    @SideOnly(Side.CLIENT) private static void openStation(BlockPos pos) {
        net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(new com.cogwork.cogworkbridge.client.RailwayStationScreen(pos));
    }
    @Override public void breakBlock(World world,BlockPos pos,IBlockState state) {
        if(!world.isRemote&&world.getTileEntity(pos) instanceof RailwayTargetTile.Observer observer
                &&com.cogwork.cogworkbridge.content.fluids.ListFilterItem.isFilterItem(observer.filter()))
            net.minecraft.inventory.InventoryHelper.spawnItemStack(world,pos.getX()+.5D,pos.getY()+.5D,pos.getZ()+.5D,observer.filter());
        if(world.getTileEntity(pos) instanceof RailwayTargetTile target)target.removeFromNetwork();
        super.breakBlock(world,pos,state);
    }
}
