package com.cogwork.cogworkbridge.content.processing.crafter;

import com.cogwork.cogworkbridge.registry.AllItems;
import com.cogwork.cogworkbridge.content.decoration.ConnectedTextureData;
import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.SoundCategory;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

public final class MechanicalCrafterBlock extends KineticBlock {
    public static final PropertyDirection FACING = PropertyDirection.create("facing");

    public MechanicalCrafterBlock() {
        super(Material.ROCK, "cogworkbridge.mechanical_crafter");
        setHardness(2.0F);
        setResistance(6.0F);
        setDefaultState(getDefaultState().withProperty(FACING, EnumFacing.NORTH));
    }

    @Override
    public boolean canRenderInLayer(IBlockState state, net.minecraft.util.BlockRenderLayer layer) {
        return layer == net.minecraft.util.BlockRenderLayer.CUTOUT;
    }

    @Override public boolean hasAxis() { return false; }

    @Override
    protected BlockStateContainer createBlockState() {
        return new net.minecraftforge.common.property.ExtendedBlockState(this, new net.minecraft.block.properties.IProperty<?>[]{FACING},
                new net.minecraftforge.common.property.IUnlistedProperty<?>[]{ConnectedTextureData.CONTEXT});
    }

    @Override public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        return ((net.minecraftforge.common.property.IExtendedBlockState) state)
                .withProperty(ConnectedTextureData.CONTEXT, ConnectedTextureData.capture(world, pos, state));
    }

    public static boolean hasValidTarget(IBlockAccess world, BlockPos pos, IBlockState state) {
        EnumFacing facing = state.getValue(FACING);
        TileEntity local = world.getTileEntity(pos);
        if (!(local instanceof MechanicalCrafterTileEntity crafter)) return false;
        EnumFacing output = crafter.getPointing().relativeTo(facing);
        BlockPos target = pos.offset(output);
        if (world instanceof World level && !level.isBlockLoaded(target)) return false;
        IBlockState neighbor = world.getBlockState(target);
        TileEntity neighborTile = world.getTileEntity(target);
        return neighbor.getBlock() instanceof MechanicalCrafterBlock
                && neighbor.getValue(FACING) == facing
                && neighborTile instanceof MechanicalCrafterTileEntity other
                && other.getPointing().relativeTo(facing) != output.getOpposite();
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos fromPos) {
        if (world.isRemote) world.markBlockRangeForRenderUpdate(pos, pos);
        else world.notifyBlockUpdate(pos, state, state, 2);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.byIndex(meta));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getIndex();
    }

    @Override
    public EnumFacing.Axis getAxis(IBlockState state) {
        return state.getValue(FACING).getAxis();
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing clickedFace,
                                            float hitX, float hitY, float hitZ, int meta,
                                            EntityLivingBase placer) {
        EnumFacing facing = getPlacementFacing(placer).getOpposite();
        IBlockState against = world.getBlockState(pos.offset(clickedFace.getOpposite()));
        if (!placer.isSneaking() && against.getBlock() == this) {
            facing = against.getValue(FACING);
        }
        return getDefaultState().withProperty(FACING, facing);
    }

    @Override
    public IBlockState withRotation(IBlockState state, Rotation rotation) {
        return state.withProperty(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public IBlockState withMirror(IBlockState state, Mirror mirror) {
        return state.withProperty(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new MechanicalCrafterTileEntity();
    }

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.MODEL;
    }

    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }

    @Override
    public boolean hasShaftTowards(World world, BlockPos pos, IBlockState state, EnumFacing side) {
        return false;
    }

    @Override
    public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos,
                                      net.minecraft.util.math.AxisAlignedBB mask,
                                      List<net.minecraft.util.math.AxisAlignedBB> boxes,
                                      @Nullable net.minecraft.entity.Entity entity, boolean actualState) {
        addCollisionBoxToList(pos, mask, boxes, FULL_BLOCK_AABB);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        if (hand != EnumHand.MAIN_HAND) return false;
        ItemStack held = player.getHeldItem(hand);
        if (held.getItem() == com.cogwork.cogworkbridge.registry.AllItems.WRENCH) {
            return !player.isSneaking() && wrench(world, pos, state, side);
        }

        // Only the work surface accepts items.  Returning false on every
        // other face lets ItemBlock perform normal adjacent placement.
        if (side != state.getValue(FACING)) return false;

        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof MechanicalCrafterTileEntity crafter)) return false;
        if (world.isRemote) return true;

        if (held.isEmpty()) {
            ItemStack taken = crafter.takeForPlayer();
            if (taken.isEmpty()) return false;
            if (!player.addItemStackToInventory(taken)) player.dropItem(taken, false);
            world.playSound(null, pos, SoundEvents.ENTITY_ITEMFRAME_REMOVE_ITEM,
                    SoundCategory.BLOCKS, 0.5F, 1.0F);
            return true;
        }

        if (!crafter.hasInput() && crafter.insertOne(held)) {
            if (!player.capabilities.isCreativeMode) held.shrink(1);
            world.playSound(null, pos, SoundEvents.ENTITY_ITEMFRAME_ADD_ITEM,
                    SoundCategory.BLOCKS, 0.5F, 1.0F);
            return true;
        }
        return false;
    }

    public boolean wrench(World world, BlockPos pos, IBlockState state, EnumFacing side) {
        if (side != state.getValue(FACING)) return false;
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof MechanicalCrafterTileEntity crafter)) return false;
        MechanicalCrafterDirection next = switch (crafter.getPointing()) {
            case LEFT -> MechanicalCrafterDirection.UP;
            case UP -> MechanicalCrafterDirection.RIGHT;
            case RIGHT -> MechanicalCrafterDirection.DOWN;
            case DOWN -> MechanicalCrafterDirection.LEFT;
        };
        if (!world.isRemote) {
            crafter.setPointing(next);
        }
        return true;
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity tile = world.getTileEntity(pos);
        if (!world.isRemote && tile instanceof MechanicalCrafterTileEntity crafter) {
            crafter.dropItems(world, pos);
        }
        super.breakBlock(world, pos, state);
    }
}
