package com.cogwork.cogworkbridge.content.display;

import com.cogwork.cogworkbridge.kinetic.KineticTileEntity;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.item.EnumDyeColor;
import java.util.Locale;

/** Two lines per block; neighbouring boards share a wider display surface. */
public final class DisplayBoardTileEntity extends KineticTileEntity implements DisplayTarget {
    private final String[] lines = {"", ""};
    private final String[] rowText = {"", ""};
    private final boolean[] rowTextKnown = {false, false};
    private int observedNeighbours = -1;
    private final String[] previousLines = {"", ""};
    private final int[] animation = {0, 0};
    private final boolean[] manualLines = {false, false};
    private final int[] lineColors = {0xffe7d6, 0xffe7d6};
    public String line(int index) { return lines[index]; }
    public int lineColor(int index) { return lineColors[index]; }
    public String animatedLine(int index) {
        String target = lines[index];
        if (animation[index] <= 0) return target;
        String old = previousLines[index];
        String alphabet = " ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-.:/";
        StringBuilder result = new StringBuilder();
        int elapsed = 14 - animation[index];
        for (int i = 0; i < Math.max(old.length(), target.length()); i++) {
            char before = i < old.length() ? old.charAt(i) : ' ';
            char after = i < target.length() ? target.charAt(i) : ' ';
            int stage = elapsed - i * 2;
            if (before == after || stage >= 8) result.append(after);
            else if (stage <= 0) result.append(before);
            else result.append(alphabet.charAt(Math.floorMod(before + i * 7 + stage * 3, alphabet.length())));
        }
        return result.toString();
    }
    public void setManualText(String text) {
        setManualText(text, 0);
    }
    public boolean setManualText(String text, int line) {
        if (world == null || world.isRemote || line < 0 || line > 1) return false;
        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof DisplayBoardBlock)) return false;
        EnumFacing left = state.getValue(DisplayBoardBlock.FACING).rotateY();
        BlockPos start = rowStart(pos, state, left);
        if (!rowRunning(start, state, left)) return false;
        writeAcrossRow(start, state, left, text == null ? "" : text, line, text != null);
        return true;
    }
    @Override public void update() {
        super.update();
        if (world != null && world.isRemote) for (int i = 0; i < 2; i++) if (animation[i] > 0) animation[i]--;
        if (world != null && !world.isRemote && world.getTotalWorldTime() % 10 == 0) refreshRow();
    }
    private void refreshRow() {
        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof DisplayBoardBlock)) return;
        EnumFacing left = state.getValue(DisplayBoardBlock.FACING).rotateY();
        int neighbours = (world.isBlockLoaded(pos.offset(left)) && world.getBlockState(pos.offset(left)) == state ? 1 : 0)
                | (world.isBlockLoaded(pos.offset(left.getOpposite())) && world.getBlockState(pos.offset(left.getOpposite())) == state ? 2 : 0);
        if (neighbours == observedNeighbours && rowTextKnown[0] && rowTextKnown[1]) return;
        observedNeighbours = neighbours;
        BlockPos start = rowStart(pos, state, left);
        for (int line = 0; line < 2; line++) {
            StringBuilder oldPieces = new StringBuilder();
            String full = null;
            boolean manual = false;
            for (int x = 0; x < 32; x++) {
                BlockPos at = start.offset(left.getOpposite(), x);
                if (!world.isBlockLoaded(at) || world.getBlockState(at) != state) break;
                if (!(world.getTileEntity(at) instanceof DisplayBoardTileEntity board)) continue;
                if (board.rowTextKnown[line] && (full == null || full.isEmpty())) {
                    full = board.rowText[line]; manual = board.manualLines[line];
                }
                if (!board.rowTextKnown[line]) {
                    oldPieces.append(board.lines[line]);
                    manual |= board.manualLines[line];
                }
            }
            if (full == null || full.isEmpty() && oldPieces.length() > 0) full = oldPieces.toString();
            writeAcrossRow(start, state, left, full, line, manual);
        }
    }
    @Override public void acceptDisplayText(int line, String text) {
        if (world == null || world.isRemote || !(world.getBlockState(pos).getBlock() instanceof DisplayBoardBlock)) return;
        IBlockState state = world.getBlockState(pos);
        EnumFacing left = state.getValue(DisplayBoardBlock.FACING).rotateY();
        BlockPos topLeft = pos;
        for (int i = 0; i < 31 && world.isBlockLoaded(topLeft.up()) && world.getBlockState(topLeft.up()) == state; i++) topLeft = topLeft.up();
        for (int i = 0; i < 31 && world.isBlockLoaded(topLeft.offset(left)) && world.getBlockState(topLeft.offset(left)) == state; i++) topLeft = topLeft.offset(left);
        BlockPos start = topLeft.down(line / 2);
        if (!world.isBlockLoaded(start) || world.getBlockState(start) != state) return;
        if (!rowRunning(start, state, left)) return;
        for (int x = 0; x < 32; x++) {
            BlockPos at = start.offset(left.getOpposite(), x);
            if (!world.isBlockLoaded(at) || world.getBlockState(at) != state) break;
            if (world.getTileEntity(at) instanceof DisplayBoardTileEntity board && board.manualLines[line % 2]) return;
        }
        writeAcrossRow(start, state, left, text == null ? "" : text, line % 2, false);
    }
    public boolean isLineManual(int line) {
        if (world == null || !(world.getBlockState(pos).getBlock() instanceof DisplayBoardBlock)) return false;
        IBlockState state = world.getBlockState(pos);
        EnumFacing left = state.getValue(DisplayBoardBlock.FACING).rotateY();
        BlockPos start = pos;
        for (int i = 0; i < 31 && world.isBlockLoaded(start.up()) && world.getBlockState(start.up()) == state; i++) start = start.up();
        start = rowStart(start, state, left).down(line / 2);
        for (int x = 0; x < 32; x++) {
            BlockPos at = start.offset(left.getOpposite(), x);
            if (!world.isBlockLoaded(at) || world.getBlockState(at) != state) break;
            if (world.getTileEntity(at) instanceof DisplayBoardTileEntity board && board.manualLines[line % 2]) return true;
        }
        return false;
    }
    private BlockPos rowStart(BlockPos at, IBlockState state, EnumFacing left) {
        BlockPos start = at;
        for (int x = 0; x < 31 && world.isBlockLoaded(start.offset(left))
                && world.getBlockState(start.offset(left)) == state; x++) start = start.offset(left);
        return start;
    }
    private boolean rowRunning(BlockPos start, IBlockState state, EnumFacing left) {
        for (int x = 0; x < 32; x++) {
            BlockPos at = start.offset(left.getOpposite(), x);
            if (!world.isBlockLoaded(at) || world.getBlockState(at) != state) break;
            if (world.getTileEntity(at) instanceof DisplayBoardTileEntity board && Math.abs(board.getSpeed()) >= 30F)
                return true;
        }
        return false;
    }
    public void setRowColor(EnumDyeColor color, int line) {
        if (world == null || world.isRemote || line < 0 || line > 1) return;
        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof DisplayBoardBlock)) return;
        EnumFacing left = state.getValue(DisplayBoardBlock.FACING).rotateY();
        BlockPos start = rowStart(pos, state, left);
        int next = color == EnumDyeColor.WHITE ? 0xffe7d6 : color.getColorValue();
        for (int x = 0; x < 32; x++) {
            BlockPos at = start.offset(left.getOpposite(), x);
            if (!world.isBlockLoaded(at) || world.getBlockState(at) != state) break;
            if (world.getTileEntity(at) instanceof DisplayBoardTileEntity board && board.lineColors[line] != next) {
                board.lineColors[line] = next;
                board.markDirty();
                world.notifyBlockUpdate(at, state, state, 3);
            }
        }
    }
    private void writeAcrossRow(BlockPos start, IBlockState state, EnumFacing left, String value, int line, boolean manual) {
        int width = 0;
        while (width < 32 && world.isBlockLoaded(start.offset(left.getOpposite(), width))
                && world.getBlockState(start.offset(left.getOpposite(), width)) == state) width++;
        value = value.toUpperCase(Locale.ROOT);
        for (int x = 0; x < width; x++) {
            BlockPos at = start.offset(left.getOpposite(), x);
            TileEntity tile = world.getTileEntity(at);
            if (tile instanceof DisplayBoardTileEntity board) {
                int from = Math.min(value.length(), firstSlot(width, x));
                int to = Math.min(value.length(), firstSlot(width, x + 1));
                board.setLine(line, value.substring(from, Math.max(from, to)), manual, value);
            }
        }
    }
    /** Create uses 7 font units per flap and one 16-pixel face per block. */
    public static int slotCount(int width) { return Math.max(0, (width * 32 - 4) / 7); }
    public static float slotPadding(int width) { return (width * 32 - slotCount(width) * 7) / 2F; }
    public static int firstSlot(int width, int column) {
        int slot = 0, count = slotCount(width);
        float edge = column * 32F, padding = slotPadding(width);
        while (slot < count && padding + slot * 7F + 3.5F < edge) slot++;
        return slot;
    }
    private void setLine(int line, String text, boolean manual, String full) {
        if (lines[line].equals(text) && manualLines[line] == manual && rowTextKnown[line] && rowText[line].equals(full)) return;
        lines[line] = text;
        manualLines[line] = manual;
        rowText[line] = full;
        rowTextKnown[line] = true;
        markDirty();
        if (world != null && !world.isRemote)
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag); writeDisplay(tag); return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag); readLines(tag);
    }
    @Override public NBTTagCompound getUpdateTag() {
        NBTTagCompound tag = super.getUpdateTag(); writeDisplay(tag); return tag;
    }
    private void writeDisplay(NBTTagCompound tag) {
        for (int i = 0; i < 2; i++) {
            tag.setString("Display" + i, lines[i]);
            if (rowTextKnown[i]) tag.setString("RowText" + i, rowText[i]);
            tag.setBoolean("Manual" + i, manualLines[i]);
            tag.setInteger("LineColor" + i, lineColors[i]);
        }
    }
    @Override public void handleUpdateTag(NBTTagCompound tag) { super.handleUpdateTag(tag); readLines(tag); }
    private void readLines(NBTTagCompound tag) {
        for (int i = 0; i < 2; i++) {
            String value = tag.getString("Display" + i);
            if (!lines[i].equals(value) && world != null && world.isRemote) {
                previousLines[i] = lines[i];
                animation[i] = 14;
            }
            lines[i] = value;
            rowTextKnown[i] = tag.hasKey("RowText" + i, 8);
            rowText[i] = rowTextKnown[i] ? tag.getString("RowText" + i) : "";
            lineColors[i] = tag.hasKey("LineColor" + i) ? tag.getInteger("LineColor" + i) : 0xffe7d6;
            manualLines[i] = tag.hasKey("Manual" + i) ? tag.getBoolean("Manual" + i)
                    : i == 0 && tag.getBoolean("Manual");
        }
    }
}
