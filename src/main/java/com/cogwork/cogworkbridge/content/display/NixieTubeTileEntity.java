package com.cogwork.cogworkbridge.content.display;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.item.EnumDyeColor;

/** Each block contains two characters; without custom text it shows redstone. */
public final class NixieTubeTileEntity extends TileEntity implements ITickable, DisplayTarget {
    private String text = "";
    private String rowText = "";
    private boolean manual;
    private EnumDyeColor color = EnumDyeColor.ORANGE;
    private int observedNeighbours = -1;
    public String text() { return text; }
    public String fullText() { return manual ? rowText : Integer.toString(world == null ? 0 : world.getRedstonePowerFromNeighbors(pos)); }
    public EnumDyeColor color() { return color; }
    public void setManualText(String value) {
        if (value == null) distribute("", false);
        else distribute(value, true);
    }
    @Override public void acceptDisplayText(int line, String value) { if (line == 0) distribute(value, true); }
    @Override public void update() {
        if (world == null || world.isRemote || world.getTotalWorldTime() % 10 != 0) return;
        refreshConnectedRow();
        if (!manual) updateRedstone();
    }
    private void updateRedstone() {
        if (world != null) setValue(String.format(java.util.Locale.ROOT, "%02d", world.getRedstonePowerFromNeighbors(pos)), false, "");
    }
    private static EnumFacing left(IBlockState state) {
        return switch (state.getValue(NixieTubeBlock.MOUNT)) {
            case WALL -> EnumFacing.UP;
            case WALL_REVERSED -> EnumFacing.DOWN;
            default -> state.getValue(NixieTubeBlock.FACING).getOpposite();
        };
    }
    private void refreshConnectedRow() {
        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof NixieTubeBlock)) return;
        EnumFacing left = left(state);
        int neighbours = (sameTube(pos.offset(left), state) ? 1 : 0)
                | (sameTube(pos.offset(left.getOpposite()), state) ? 2 : 0);
        if (neighbours == observedNeighbours) return;
        observedNeighbours = neighbours;

        BlockPos start = pos;
        for (int i = 0; i < 31 && sameTube(start.offset(left), state); i++) start = start.offset(left);
        for (int i = 0; i < 32; i++) {
            BlockPos at = start.offset(left.getOpposite(), i);
            if (!sameTube(at, state)) break;
            if (world.getTileEntity(at) instanceof NixieTubeTileEntity tube && tube.manual) {
                distribute(tube.rowText, true);
                return;
            }
        }
    }
    private boolean sameTube(BlockPos at, IBlockState state) {
        return world.isBlockLoaded(at) && world.getBlockState(at) == state;
    }
    public void setRowColor(EnumDyeColor color) {
        if (world == null || world.isRemote || !(world.getBlockState(pos).getBlock() instanceof NixieTubeBlock)) return;
        IBlockState state = world.getBlockState(pos);
        EnumFacing left = left(state);
        BlockPos start = pos;
        for (int i = 0; i < 31 && world.isBlockLoaded(start.offset(left)) && world.getBlockState(start.offset(left)) == state; i++) start = start.offset(left);
        for (int i = 0; i < 32; i++) {
            BlockPos at = start.offset(left.getOpposite(), i);
            if (!world.isBlockLoaded(at) || world.getBlockState(at) != state) break;
            if (world.getTileEntity(at) instanceof NixieTubeTileEntity tube && tube.color != color) {
                tube.color = color; tube.changed();
            }
        }
    }
    private void distribute(String value, boolean manual) {
        if (world == null || world.isRemote || !(world.getBlockState(pos).getBlock() instanceof NixieTubeBlock)) return;
        IBlockState state = world.getBlockState(pos);
        EnumFacing left = left(state);
        BlockPos start = pos;
        for (int i = 0; i < 31 && world.isBlockLoaded(start.offset(left)) && world.getBlockState(start.offset(left)) == state; i++) start = start.offset(left);
        String input = value == null ? "" : value;
        for (int i = 0; i < 32; i++) {
            BlockPos at = start.offset(left.getOpposite(), i);
            if (!world.isBlockLoaded(at) || world.getBlockState(at) != state) break;
            if (world.getTileEntity(at) instanceof NixieTubeTileEntity tube) {
                if (!manual) tube.setValue(String.format(java.util.Locale.ROOT, "%02d", world.getRedstonePowerFromNeighbors(at)), false, "");
                else {
                    int from = Math.min(i * 2, input.length());
                    tube.setValue(input.substring(from, Math.min(from + 2, input.length())), true, input);
                }
            }
        }
    }
    private void setValue(String value, boolean manual, String fullText) {
        if (text.equals(value) && this.manual == manual && rowText.equals(fullText)) return;
        text = value; this.manual = manual; rowText = fullText; changed();
    }
    private void changed() {
        markDirty();
        if (world != null && !world.isRemote) world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) { super.writeToNBT(tag); tag.setString("Text", text); tag.setString("RowText", rowText); tag.setBoolean("Manual", manual); tag.setInteger("Color", color.getMetadata()); return tag; }
    @Override public void readFromNBT(NBTTagCompound tag) { super.readFromNBT(tag); text = tag.getString("Text"); rowText = tag.hasKey("RowText", 8) ? tag.getString("RowText") : text; manual = tag.getBoolean("Manual"); color = tag.hasKey("Color") ? EnumDyeColor.byMetadata(tag.getInteger("Color")) : EnumDyeColor.ORANGE; }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager network, SPacketUpdateTileEntity packet) { readFromNBT(packet.getNbtCompound()); }
}
