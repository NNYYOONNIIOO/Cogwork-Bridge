package com.cogwork.cogworkbridge.content.decoration.palette;

import java.util.Random;
import javax.annotation.Nullable;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class PaletteDoor extends BlockDoor {
    public static final IUnlistedProperty<Boolean> SPECIAL = new IUnlistedProperty<Boolean>() {
        @Override public String getName() { return "sliding_door_special"; }
        @Override public boolean isValid(Boolean value) { return value != null; }
        @Override public Class<Boolean> getType() { return Boolean.class; }
        @Override public String valueToString(Boolean value) { return value.toString(); }
    };
    public PaletteDoor() { super(Material.IRON); setHardness(3F); setHarvestLevel("pickaxe", 0); }
    @Override protected BlockStateContainer createBlockState() {
        return new ExtendedBlockState(this, new IProperty<?>[]{HALF, FACING, OPEN, HINGE, POWERED}, new IUnlistedProperty<?>[]{SPECIAL});
    }
    @Override public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        BlockPos bottom = state.getValue(HALF) == EnumDoorHalf.UPPER ? pos.down() : pos;
        TileEntity tile = world.getTileEntity(bottom);
        // A single rendering path avoids racing asynchronous chunk rebuilds against the door animation.
        return ((IExtendedBlockState) state).withProperty(SPECIAL, tile instanceof DoorStateTile);
    }
    @Override public boolean hasTileEntity(IBlockState state) { return state.getValue(HALF) == EnumDoorHalf.LOWER; }
    @Nullable @Override public TileEntity createTileEntity(World world, IBlockState state) { return new DoorStateTile(); }
    @Override public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(meta & 3)).withProperty(HALF, (meta & 4) != 0 ? EnumDoorHalf.UPPER : EnumDoorHalf.LOWER);
    }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(FACING).getHorizontalIndex() | (state.getValue(HALF) == EnumDoorHalf.UPPER ? 4 : 0); }
    @Override public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
        BlockPos bottom = state.getValue(HALF) == EnumDoorHalf.UPPER ? pos.down() : pos;
        IBlockState lower = world.getBlockState(bottom);
        if (lower.getBlock() == this) state = state.withProperty(FACING, lower.getValue(FACING));
        TileEntity tile = world.getTileEntity(bottom);
        if (tile instanceof DoorStateTile door) state = state.withProperty(OPEN, door.open).withProperty(POWERED, door.powered)
                .withProperty(HINGE, door.rightHinge ? EnumHingePosition.RIGHT : EnumHingePosition.LEFT);
        return state;
    }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        state = getActualState(state, world, pos);
        if (!state.getValue(OPEN)) return super.getBoundingBox(state, world, pos);
        boolean right = state.getValue(HINGE) == EnumHingePosition.RIGHT;
        String name = getRegistryName().getPath();
        boolean fold = name.equals("andesite_door") || name.equals("copper_door");
        switch (state.getValue(FACING)) {
            case SOUTH: return right ? box(fold, -13, 0, 3, 3, -3, 0, 3, 9) : box(fold, 13, 0, 29, 3, 13, 0, 19, 9);
            case WEST: return right ? box(fold, 13, -13, 16, 3, 7, -3, 16, 3) : box(fold, 13, 13, 16, 29, 7, 13, 16, 19);
            case NORTH: return right ? box(fold, 13, 13, 29, 16, 13, 7, 19, 16) : box(fold, -13, 13, 3, 16, -3, 7, 3, 16);
            default: return right ? box(fold, 0, 13, 3, 29, 0, 13, 9, 19) : box(fold, 0, -13, 3, 3, 0, -3, 9, 3);
        }
    }
    private static AxisAlignedBB box(boolean fold, int minX, int minZ, int maxX, int maxZ,
                                     int foldMinX, int foldMinZ, int foldMaxX, int foldMaxZ) {
        return new AxisAlignedBB((fold ? foldMinX : minX) / 16D, 0, (fold ? foldMinZ : minZ) / 16D,
                (fold ? foldMaxX : maxX) / 16D, 1, (fold ? foldMaxZ : maxZ) / 16D);
    }
    @Override @SideOnly(Side.CLIENT) public BlockRenderLayer getRenderLayer() { return BlockRenderLayer.CUTOUT; }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing face, float x, float y, float z) {
        // Let the held door item place an adjacent panel, including the second half of a double door.
        if (player.getHeldItem(hand).getItem() instanceof DoorItem) return false;
        BlockPos bottom = state.getValue(HALF) == EnumDoorHalf.UPPER ? pos.down() : pos;
        TileEntity tile = world.getTileEntity(bottom);
        if (!(tile instanceof DoorStateTile door)) return false;
        if (!world.isRemote) setOpen(world, bottom, door, !door.open, player);
        return true;
    }
    private void setOpen(World world, BlockPos pos, DoorStateTile door, boolean open, @Nullable EntityPlayer player) {
        if (door.open == open) return;
        door.open = open; door.changed();
        world.playEvent(player, open ? 1005 : 1011, pos, 0);
        IBlockState state = world.getBlockState(pos);
        if (state.getBlock() != this) return;
        DoorStateTile paired = pairedDoor(world, pos, door);
        if (paired != null && paired.open != open) {
            paired.open = open; paired.changed();
        }
    }
    @Nullable private DoorStateTile pairedDoor(World world, BlockPos pos, DoorStateTile door) {
        IBlockState state = world.getBlockState(pos);
        if (state.getBlock() != this) return null;
        EnumFacing face = state.getValue(FACING);
        BlockPos otherPos = pos.offset(door.rightHinge ? face.rotateYCCW() : face.rotateY());
        IBlockState otherState = world.getBlockState(otherPos);
        TileEntity other = world.getTileEntity(otherPos);
        if (otherState.getBlock() != this || otherState.getValue(HALF) != EnumDoorHalf.LOWER
                || otherState.getValue(FACING) != face || !(other instanceof DoorStateTile paired)
                || paired.rightHinge == door.rightHinge) return null;
        return paired;
    }
    @Override public void neighborChanged(IBlockState state, World world, BlockPos pos, Block from, BlockPos fromPos) {
        if (world.isRemote) return;
        if (state.getValue(HALF) == EnumDoorHalf.UPPER) {
            if (world.getBlockState(pos.down()).getBlock() != this) world.setBlockToAir(pos);
            else world.getBlockState(pos.down()).neighborChanged(world, pos.down(), from, fromPos);
            return;
        }
        if (world.getBlockState(pos.up()).getBlock() != this || !world.getBlockState(pos.down()).isSideSolid(world, pos.down(), EnumFacing.UP)) {
            dropBlockAsItem(world, pos, state, 0); world.setBlockToAir(pos);
            if (world.getBlockState(pos.up()).getBlock() == this) world.setBlockToAir(pos.up());
            return;
        }
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof DoorStateTile door)) return;
        DoorStateTile paired = pairedDoor(world, pos, door);
        boolean powered = world.isBlockPowered(pos) || world.isBlockPowered(pos.up())
                || paired != null && (world.isBlockPowered(paired.getPos()) || world.isBlockPowered(paired.getPos().up()));
        if (powered != door.powered || paired != null && powered != paired.powered) {
            door.powered = powered;
            if (paired != null) { paired.powered = powered; paired.changed(); }
            setOpen(world, pos, door, powered, null);
            door.changed();
        }
    }
    @Override public Item getItemDropped(IBlockState state, Random random, int fortune) { return state.getValue(HALF) == EnumDoorHalf.UPPER ? Items.AIR : Item.getItemFromBlock(this); }
    @Override public ItemStack getItem(World world, BlockPos pos, IBlockState state) { return new ItemStack(this); }
    @Override public ItemStack getPickBlock(IBlockState state, RayTraceResult target, World world, BlockPos pos, EntityPlayer player) { return new ItemStack(this); }

    public static class DoorItem extends ItemBlock {
        public DoorItem(Block block) { super(block); }
        @Override public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing face, float x, float y, float z) {
            if (!world.getBlockState(pos).getBlock().isReplaceable(world, pos)) pos = pos.offset(face);
            ItemStack held = player.getHeldItem(hand);
            if (!player.canPlayerEdit(pos, face, held) || !player.canPlayerEdit(pos.up(), face, held) || !block.canPlaceBlockAt(world, pos)) return EnumActionResult.FAIL;
            EnumFacing direction = player.getHorizontalFacing();
            boolean right = direction == EnumFacing.NORTH ? x > .5F : direction == EnumFacing.SOUTH ? x < .5F : direction == EnumFacing.WEST ? z < .5F : z > .5F;
            BlockPos leftPos = pos.offset(direction.rotateYCCW()), rightPos = pos.offset(direction.rotateY());
            boolean leftDoor = world.getBlockState(leftPos).getBlock() == block;
            boolean rightDoor = world.getBlockState(rightPos).getBlock() == block;
            int leftWall = (world.getBlockState(leftPos).isNormalCube() ? 1 : 0) + (world.getBlockState(leftPos.up()).isNormalCube() ? 1 : 0);
            int rightWall = (world.getBlockState(rightPos).isNormalCube() ? 1 : 0) + (world.getBlockState(rightPos.up()).isNormalCube() ? 1 : 0);
            if (leftDoor != rightDoor) right = leftDoor;
            else if (leftWall != rightWall) right = rightWall > leftWall;
            IBlockState base = block.getDefaultState().withProperty(FACING, direction);
            world.setBlockState(pos, base.withProperty(HALF, EnumDoorHalf.LOWER), 2);
            world.setBlockState(pos.up(), base.withProperty(HALF, EnumDoorHalf.UPPER), 2);
            TileEntity tile = world.getTileEntity(pos);
            if (tile instanceof DoorStateTile door) {
                door.rightHinge = right; door.powered = world.isBlockPowered(pos) || world.isBlockPowered(pos.up()); door.open = door.powered; door.changed();
            }
            world.notifyNeighborsOfStateChange(pos, block, false); world.notifyNeighborsOfStateChange(pos.up(), block, false);
            net.minecraft.block.SoundType sound = block.getSoundType(base, world, pos, player);
            world.playSound(player, pos, sound.getPlaceSound(), net.minecraft.util.SoundCategory.BLOCKS,
                    (sound.getVolume() + 1F) / 2F, sound.getPitch() * .8F);
            if (!player.capabilities.isCreativeMode) held.shrink(1);
            return EnumActionResult.SUCCESS;
        }
    }
}
