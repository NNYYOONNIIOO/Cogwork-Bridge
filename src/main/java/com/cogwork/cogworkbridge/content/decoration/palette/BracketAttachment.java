package com.cogwork.cogworkbridge.content.decoration.palette;

import com.cogwork.cogworkbridge.content.fluids.FluidPipeBlock;
import com.cogwork.cogworkbridge.content.fluids.FluidPipeTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.cogwheel.CogwheelBlock;
import com.cogwork.cogworkbridge.content.kinetics.shaft.ShaftBlock;
import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import com.cogwork.cogworkbridge.kinetic.KineticTileEntity;
import com.cogwork.cogworkbridge.registry.BlockFamily;
import javax.annotation.Nullable;
import net.minecraft.block.Block;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.ItemHandlerHelper;

/** A bracket belongs to its host tile rather than occupying a separate block. */
public final class BracketAttachment {
    private boolean present, metal, first;
    private EnumFacing face = EnumFacing.UP;
    private String type = "shaft";
    public boolean present() { return present; }
    public ItemStack item() { return present ? new ItemStack(BlockFamily.get(metal ? "metal_bracket" : "wooden_bracket")) : ItemStack.EMPTY; }
    public IBlockState model() {
        if (!present) return null;
        Block block = BlockFamily.get("render_" + (metal ? "metal" : "wooden") + "_bracket_" + type);
        return block.getDefaultState().withProperty(RenderBlock.FACING, face).withProperty(RenderBlock.FIRST, first);
    }
    public void write(NBTTagCompound tag) {
        if (!present) { tag.removeTag("Bracket"); return; }
        NBTTagCompound data = new NBTTagCompound(); data.setBoolean("Metal", metal); data.setBoolean("First", first);
        data.setByte("Face", (byte) face.getIndex()); data.setString("Type", type); tag.setTag("Bracket", data);
    }
    public void read(NBTTagCompound tag) {
        present = tag.hasKey("Bracket", 10); if (!present) return;
        NBTTagCompound data = tag.getCompoundTag("Bracket"); metal = data.getBoolean("Metal"); first = data.getBoolean("First");
        face = EnumFacing.byIndex(data.getByte("Face")); type = data.getString("Type");
        if (!type.equals("shaft") && !type.equals("cog") && !type.equals("pipe")) { present = false; type = "shaft"; }
    }
    @Nullable public static BracketAttachment of(TileEntity tile) {
        if (tile instanceof KineticTileEntity kinetic) return kinetic.bracket();
        if (tile instanceof FluidPipeTileEntity pipe) return pipe.bracket();
        return null;
    }
    public static void changed(TileEntity tile) {
        tile.markDirty(); World world = tile.getWorld();
        if (world != null && !world.isRemote) world.notifyBlockUpdate(tile.getPos(), world.getBlockState(tile.getPos()), world.getBlockState(tile.getPos()), 3);
    }
    public static boolean remove(TileEntity tile, @Nullable EntityPlayer player) {
        BracketAttachment attachment = of(tile);
        if (attachment == null || !attachment.present) return false;
        ItemStack result = attachment.item(); attachment.present = false; changed(tile);
        if (player != null && !player.capabilities.isCreativeMode) ItemHandlerHelper.giveItemToPlayer(player, result);
        return true;
    }

    public static class BracketItem extends ItemBlock {
        public BracketItem(Block block) { super(block); }
        @Override public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side, float x, float y, float z, EnumHand hand) {
            IBlockState state = world.getBlockState(pos); Block target = state.getBlock(); TileEntity tile = world.getTileEntity(pos);
            BracketAttachment attachment = of(tile); EnumFacing.Axis axis; String kind;
            if (attachment == null) return EnumActionResult.PASS;
            if (target.getClass() == ShaftBlock.class) { axis = ((KineticBlock) target).getAxis(state); kind = "shaft"; }
            else if (target instanceof CogwheelBlock cog && !cog.isLarge()) { axis = cog.getAxis(state); kind = "cog"; }
            else if (target.getClass() == FluidPipeBlock.class && tile instanceof FluidPipeTileEntity pipe && pipe.getStraightAxis() != null) {
                axis = pipe.getStraightAxis(); kind = "pipe";
            } else return EnumActionResult.PASS;
            if (!player.canPlayerEdit(pos, side, player.getHeldItem(hand)) || !world.isBlockModifiable(player, pos)) return EnumActionResult.FAIL;
            EnumFacing support = side;
            if (support.getAxis() == axis) support = EnumFacing.getDirectionFromEntityLiving(pos, player).getOpposite();
            if (support.getAxis() == axis) support = axis == EnumFacing.Axis.Y ? EnumFacing.NORTH : EnumFacing.UP;
            boolean nextMetal = block.getRegistryName().getPath().equals("metal_bracket");
            boolean alongFirst = support.getAxis() != EnumFacing.Axis.Z ? axis == EnumFacing.Axis.Z : axis == EnumFacing.Axis.Y;
            boolean nextFirst = !alongFirst;
            if (attachment.present && attachment.metal == nextMetal && attachment.face == support && attachment.first == nextFirst && attachment.type.equals(kind)) return EnumActionResult.SUCCESS;
            if (!world.isRemote) {
                ItemStack previous = attachment.item();
                attachment.present = true; attachment.metal = nextMetal; attachment.face = support; attachment.first = nextFirst; attachment.type = kind;
                changed(tile);
                if (!player.capabilities.isCreativeMode) { player.getHeldItem(hand).shrink(1); if (!previous.isEmpty()) ItemHandlerHelper.giveItemToPlayer(player, previous); }
            }
            return EnumActionResult.SUCCESS;
        }
        @Override public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing side, float x, float y, float z) {
            return onItemUseFirst(player, world, pos, side, x, y, z, hand);
        }
    }

    public static class RenderBlock extends Block {
        public static final PropertyDirection FACING = PropertyDirection.create("facing");
        public static final PropertyBool FIRST = PropertyBool.create("axis_along_first");
        public RenderBlock() { super(net.minecraft.block.material.Material.IRON); setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.UP).withProperty(FIRST, false)); }
        @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, FACING, FIRST); }
        @Override public int getMetaFromState(IBlockState state) { return state.getValue(FACING).getIndex() | (state.getValue(FIRST) ? 8 : 0); }
        @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(FACING, EnumFacing.byIndex((meta & 7) % 6)).withProperty(FIRST, (meta & 8) != 0); }
        @Override public boolean isOpaqueCube(IBlockState state) { return false; }
        @Override public boolean isFullCube(IBlockState state) { return false; }
    }
}
