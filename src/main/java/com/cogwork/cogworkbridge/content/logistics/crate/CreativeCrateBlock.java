package com.cogwork.cogworkbridge.content.logistics.crate;

import com.cogwork.cogworkbridge.content.fluids.ListFilterItem;
import com.cogwork.cogworkbridge.content.contraptions.bearing.ContraptionWorld;
import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity;
import com.cogwork.cogworkbridge.registry.AllItems;
import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.Collections;

/** A bottomless source and sink whose template is selected on its upper face. */
public final class CreativeCrateBlock extends BlockContainer {
    public static final PropertyDirection FACING = PropertyDirection.create("facing");
    private static final AxisAlignedBB SHAPE = new AxisAlignedBB(1 / 16.0D, 0, 1 / 16.0D,
            15 / 16.0D, 14 / 16.0D, 15 / 16.0D);

    public CreativeCrateBlock() {
        super(Material.WOOD);
        setHardness(2.0F);
        setResistance(6.0F);
        setSoundType(SoundType.WOOD);
        setDefaultState(getDefaultState().withProperty(FACING, EnumFacing.UP));
    }

    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, FACING); }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(FACING).getIndex(); }
    @Override public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.byIndex(Math.floorMod(meta, 6)));
    }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side,
            float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer) {
        return getDefaultState().withProperty(FACING, side);
    }
    @Override public IBlockState withRotation(IBlockState state, Rotation rotation) {
        return state.withProperty(FACING, rotation.rotate(state.getValue(FACING)));
    }
    @Override public IBlockState withMirror(IBlockState state, Mirror mirror) {
        return state.withProperty(FACING, mirror.mirror(state.getValue(FACING)));
    }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new CreativeCrateTileEntity(); }
    @Override public EnumBlockRenderType getRenderType(IBlockState state) { return EnumBlockRenderType.MODEL; }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) { return SHAPE; }

    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
            EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        if (side != EnumFacing.UP || hitX < 0.2F || hitX > 0.8F || hitZ < 0.2F || hitZ > 0.8F
                || player.getHeldItem(hand).getItem() == AllItems.WRENCH) return false;
        if (!(world.getTileEntity(pos) instanceof CreativeCrateTileEntity crate)) return false;
        if (world.isRemote) return true;
        ItemStack held = player.getHeldItem(hand);
        ItemStack old = crate.getSelection();
        crate.setSelection(held);
        if (!player.capabilities.isCreativeMode && ListFilterItem.isFilterItem(held)) held.shrink(1);
        if (ListFilterItem.isFilterItem(old) && (!player.capabilities.isCreativeMode || !hasFilter(player, old)))
            ItemHandlerHelper.giveItemToPlayer(player, old);
        return true;
    }

    private static boolean hasFilter(EntityPlayer player, ItemStack filter) {
        for (int slot = 0; slot < player.inventory.getSizeInventory(); slot++) {
            ItemStack present = player.inventory.getStackInSlot(slot);
            if (ItemStack.areItemsEqual(present, filter) && ItemStack.areItemStackTagsEqual(present, filter)) return true;
        }
        return false;
    }

    @Override public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof CreativeCrateTileEntity crate) {
            ItemStack selected = crate.getSelection();
            if (ListFilterItem.isFilterItem(selected)) {
                if (world instanceof ContraptionWorld local) local.spawnCapturedDrop(pos, selected);
                else if (world instanceof FakeWorld fake
                        && fake.getMobileChunk().getEntityMovingWorld() instanceof BearingContraptionEntity entity)
                    entity.spawnDrop(pos, Collections.singletonList(selected));
                else if (!(world instanceof FakeWorld))
                    InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY(), pos.getZ(), selected);
            }
        }
        super.breakBlock(world, pos, state);
    }
}
