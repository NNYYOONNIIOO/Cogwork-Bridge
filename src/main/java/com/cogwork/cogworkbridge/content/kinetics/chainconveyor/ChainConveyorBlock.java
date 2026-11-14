package com.cogwork.cogworkbridge.content.kinetics.chainconveyor;

import com.cogwork.cogworkbridge.content.logistics.packagebox.PackageItem;
import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.oredict.OreDictionary;

/** Vertical kinetic wheel used to connect suspended chain conveyors. */
public final class ChainConveyorBlock extends KineticBlock {
    public static final String PORT_SELECTION = "CogworkBridgeFrogportSelection";
    public static final AxisAlignedBB OUTLINE = new AxisAlignedBB(-.625, .125, -.625, 1.625, .875, 1.625);

    public ChainConveyorBlock() { super(Material.IRON); setHardness(2F); setResistance(6F); }
    @Override public boolean hasAxis() { return false; }
    @Override public EnumFacing.Axis getAxis(IBlockState state) { return EnumFacing.Axis.Y; }
    @Override public boolean hasShaftTowards(World world, BlockPos pos, IBlockState state, EnumFacing side) { return side.getAxis() == EnumFacing.Axis.Y; }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new ChainConveyorTileEntity(); }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public net.minecraft.util.BlockRenderLayer getRenderLayer() { return net.minecraft.util.BlockRenderLayer.CUTOUT_MIPPED; }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) { return OUTLINE; }
    @Override public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) { return new AxisAlignedBB(-1, 0, -1, 2, 1, 2); }

    @Override public boolean canPlaceBlockAt(World world, BlockPos pos) {
        if (!super.canPlaceBlockAt(world, pos)) return false;
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
            if (world.getBlockState(pos.add(x, 0, z)).getBlock() instanceof ChainConveyorBlock) return false;
        return true;
    }

    public static boolean isChain(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem().getRegistryName() == null) return false;
        String id = stack.getItem().getRegistryName().toString();
        if (id.equals("farmers_future_delight:iron_chain") || id.equals("futuremc:chain")
                || id.equals("fd:chain") || id.equals("nb:chain_block") || id.equals("netherized:chain")) return true;
        for (int ore : OreDictionary.getOreIDs(stack)) {
            String name = OreDictionary.getOreName(ore);
            if (name.equals("chainIron") || name.equals("ironChain")) return true;
        }
        return false;
    }

    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
            EnumHand hand, EnumFacing face, float x, float y, float z) {
        ItemStack held = player.getHeldItem(hand);
        if (!(world.getTileEntity(pos) instanceof ChainConveyorTileEntity wheel)) return false;
        if (isChain(held)) {
            return true;
        }
        if (held.getItem() instanceof ItemBlock item && item.getBlock() == AllBlocks.PACKAGE_FROGPORT) {
            if (!world.isRemote) {
                NBTTagCompound selection = new NBTTagCompound(); selection.setLong("Pos", pos.toLong());
                selection.setInteger("Dimension", world.provider.getDimension());
                selection.setFloat("Angle", ChainConveyorTileEntity.angle(x - .5D, z - .5D));
                player.getEntityData().setTag(PORT_SELECTION, selection);
                player.sendStatusMessage(new TextComponentTranslation("cogworkbridge.chain.frog_selected"), true);
            }
            return true;
        }
        if (PackageItem.isPackage(held)) {
            if (!world.isRemote && wheel.addLoopPackage(held.copy(), ChainConveyorTileEntity.angle(x - .5D, z - .5D))) {
                if (!player.capabilities.isCreativeMode) held.shrink(1);
            }
            return true;
        }
        if (held.isEmpty() && !player.isSneaking()) {
            if (!world.isRemote) wheel.takeNearest(player, ChainConveyorTileEntity.angle(x - .5D, z - .5D));
            return true;
        }
        return false;
    }

    @Override public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof ChainConveyorTileEntity wheel) wheel.destroy();
        super.breakBlock(world, pos, state);
    }
    @Override public boolean removedByPlayer(IBlockState state, World world, BlockPos pos, EntityPlayer player, boolean willHarvest) {
        if (!world.isRemote && player.capabilities.isCreativeMode && world.getTileEntity(pos) instanceof ChainConveyorTileEntity wheel)
            wheel.suppressChainDrops();
        return super.removedByPlayer(state, world, pos, player, willHarvest);
    }
}
