package com.cogwork.cogworkbridge.content.display;

import com.cogwork.cogworkbridge.kinetic.KineticTileEntity;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import java.util.Locale;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DisplayLinkTileEntity extends TileEntity implements ITickable {
    public static final int MODE_COUNT = 25;
    private BlockPos offset = BlockPos.ORIGIN;
    private int mode;
    private int targetLine;
    private String label = "";
    private boolean alternateFormat;
    private int rateInterval;
    private int trainNamePercent=50,platformChars=3;
    public int getTrainNamePercent(){return trainNamePercent;}
    public int getPlatformChars(){return platformChars;}
    public void configureRailwayColumns(int name,int platform){trainNamePercent=Math.max(0,Math.min(72,name));platformChars=Math.max(0,Math.min(15,platform));changed();}
    private NBTTagCompound sourceConfigs = new NBTTagCompound();
    private long collectedItems, lastItemTime = -1, stopwatchStart = -1;
    private int lastItemAmount, sampleCount, sampleIndex;
    private final float[] rateSamples = new float[10];
    private boolean observedPower, powerKnown;
    private int lastOutputLines;
    private BlockPos lastOutputTarget;
    private int lastOutputStart = -1;
    private final List<LinkedPanel> linkedPanels = new ArrayList<>();
    private record LinkedPanel(BlockPos pos, int slot) { }

    public int getMode() { return mode; }
    public boolean isVisuallyPowered() { return observedPower; }
    public int getTargetLine() { return targetLine; }
    public String getLabel() { return label; }
    public boolean getAlternateFormat() { return alternateFormat; }
    public int getRateInterval() { return rateInterval; }
    public String getLabelFor(int selected) {
        String key = Integer.toString(selected);
        return sourceConfigs.hasKey(key, 10) ? sourceConfigs.getCompoundTag(key).getString("Label")
                : selected == mode ? label : "";
    }
    public boolean getAlternateFormatFor(int selected) {
        String key = Integer.toString(selected);
        return sourceConfigs.hasKey(key, 10) ? sourceConfigs.getCompoundTag(key).getBoolean("AlternateFormat")
                : selected == mode && alternateFormat;
    }
    public int getRateIntervalFor(int selected) {
        String key = Integer.toString(selected);
        return sourceConfigs.hasKey(key, 10) ? Math.floorMod(sourceConfigs.getCompoundTag(key).getInteger("RateInterval"), selected == 2 ? 5 : 3)
                : selected == mode ? rateInterval : 0;
    }
    public boolean hasTarget() { return !offset.equals(BlockPos.ORIGIN); }
    public BlockPos getTargetPosition() { return pos.add(offset); }
    public BlockPos getSourcePosition() {
        if (world == null || !(world.getBlockState(pos).getBlock() instanceof DisplayLinkBlock)) return pos;
        return pos.offset(world.getBlockState(pos).getValue(DisplayLinkBlock.FACING).getOpposite());
    }
    /** Rows available at the bound target, including the full height of a connected display board. */
    public int getTargetLineCount() {
        if (world == null || !hasTarget() || !world.isBlockLoaded(getTargetPosition())) return 0;
        BlockPos target = getTargetPosition();
        TileEntity tile = world.getTileEntity(target);
        if (tile instanceof TileEntitySign) return 4;
        if (LecternDisplayTarget.supports(tile)) return 50;
        if (tile instanceof DisplayBoardTileEntity) {
            net.minecraft.block.state.IBlockState state = world.getBlockState(target);
            for (int i = 0; i < 31 && world.isBlockLoaded(target.up()) && world.getBlockState(target.up()) == state; i++) target = target.up();
            int blocks = 0;
            while (blocks < 32 && world.isBlockLoaded(target.down(blocks))
                    && world.getBlockState(target.down(blocks)) == state) blocks++;
            return blocks * 2;
        }
        return tile instanceof DisplayTarget ? 1 : 0;
    }
    private int getTargetColumns() {
        if (world == null || !hasTarget() || !world.isBlockLoaded(getTargetPosition())) return 1;
        BlockPos target = getTargetPosition();
        TileEntity tile = world.getTileEntity(target);
        if (tile instanceof TileEntitySign) return 10;
        if (LecternDisplayTarget.supports(tile)) return 256;
        net.minecraft.block.state.IBlockState state = world.getBlockState(target);
        EnumFacing left;
        int chars;
        if (tile instanceof DisplayBoardTileEntity) {
            left = state.getValue(DisplayBoardBlock.FACING).rotateY(); chars = 0;
        } else if (tile instanceof NixieTubeTileEntity) {
            NixieTubeBlock.Mount mount = state.getValue(NixieTubeBlock.MOUNT);
            left = mount == NixieTubeBlock.Mount.WALL ? EnumFacing.UP
                    : mount == NixieTubeBlock.Mount.WALL_REVERSED ? EnumFacing.DOWN
                    : state.getValue(NixieTubeBlock.FACING).getOpposite();
            chars = 2;
        } else return 1;
        for (int i = 0; i < 31 && world.isBlockLoaded(target.offset(left))
                && world.getBlockState(target.offset(left)) == state; i++) target = target.offset(left);
        int blocks = 0;
        while (blocks < 32 && world.isBlockLoaded(target.offset(left.getOpposite(), blocks))
                && world.getBlockState(target.offset(left.getOpposite(), blocks)) == state) blocks++;
        return Math.max(1, tile instanceof DisplayBoardTileEntity
                ? DisplayBoardTileEntity.slotCount(blocks) : chars * blocks);
    }
    private String progressBar(double fraction) {
        int length = Math.max(0, Math.min(128, getTargetColumns() - (label.isEmpty() ? 0 : label.length() + 1)));
        int filled = (int) (Math.max(0, Math.min(1, fraction)) * length);
        StringBuilder text = new StringBuilder(length);
        for (int i = 0; i < length; i++) text.append(i < filled ? '\u2588' : '\u2592');
        return text.toString();
    }
    public List<Integer> getAvailableModes() {
        List<Integer> modes = new ArrayList<>();
        if (world == null || !(world.getBlockState(pos).getBlock() instanceof DisplayLinkBlock)
                || !world.isBlockLoaded(getSourcePosition())) return modes;
        modes.add(0); modes.add(3); modes.add(6);
        if (world.getBlockState(getSourcePosition()).getBlock() instanceof com.cogwork.cogworkbridge.content.decoration.SeatBlock)
            modes.add(24);
        TileEntity source = world.getTileEntity(getSourcePosition());
        if(source instanceof com.cogwork.cogworkbridge.content.trains.track.RailwayTargetTile.Station){modes.add(21);modes.add(22);}
        if(source instanceof com.cogwork.cogworkbridge.content.trains.track.RailwayTargetTile.Observer)modes.add(23);
        EnumFacing facing = world.getBlockState(pos).getValue(DisplayLinkBlock.FACING);
        if (source instanceof KineticTileEntity) { modes.add(1); modes.add(2); }
        if (source instanceof com.cogwork.cogworkbridge.content.kinetics.clock.CuckooClockTileEntity) modes.add(20);
        if (source instanceof NixieTubeTileEntity) modes.add(9);
        if (source instanceof com.cogwork.cogworkbridge.content.contraptions.elevator.RedstoneContactTileEntity
                && world.getBlockState(getSourcePosition()).getBlock() instanceof com.cogwork.cogworkbridge.content.contraptions.elevator.RedstoneContactBlock contact
                && contact.isElevator()) modes.add(17);
        if (source instanceof net.minecraft.tileentity.TileEntityEnchantmentTable) modes.add(13);
        if (source instanceof net.minecraft.tileentity.TileEntityCommandBlock) modes.add(14);
        if (source instanceof com.cogwork.cogworkbridge.content.logistics.tunnel.BeltTunnelTileEntity) { modes.add(15); modes.add(16); }
        if (source instanceof com.cogwork.cogworkbridge.content.redstone.DetectorTileEntity
                && world.getBlockState(getSourcePosition()).getBlock() instanceof com.cogwork.cogworkbridge.content.redstone.DetectorBlock detector
                && !detector.isThreshold()) modes.add(18);
        if (!linkedPanels.isEmpty() || source instanceof com.cogwork.cogworkbridge.content.logistics.factory.FactoryPanelTileEntity) modes.add(19);
        if (source instanceof com.cogwork.cogworkbridge.content.fluids.FluidTankTileEntity tank && tank.isBoiler()) modes.add(10);
        TileEntity contents = contentTile(getSourcePosition());
        EnumFacing contentsSide = contentSide(getSourcePosition(), facing);
        boolean fluids = contents != null && (contents.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, contentsSide)
                || contents.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, null));
        boolean items = contents instanceof IInventory || contents != null && (contents.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, contentsSide)
                || contents.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null));
        if (fluids) { modes.add(4); modes.add(12); }
        if (items) {
            modes.add(5);
            if (!(source instanceof com.cogwork.cogworkbridge.content.redstone.DetectorTileEntity)) modes.add(7);
            modes.add(11);
        }
        if (fluids || items || source instanceof com.cogwork.cogworkbridge.content.redstone.DetectorTileEntity detector
                && detector.targetType() != 0) modes.add(8);
        Collections.sort(modes);
        return modes;
    }
    public void setTarget(BlockPos target) { offset = target.subtract(pos); changed(); }
    /** Connect a selected factory gauge on the same surface to this display link. */
    public boolean linkFactoryPanel(BlockPos panelPos, int slot, boolean remove) {
        if (world == null || world.isRemote || slot < 0 || slot > 3 || !world.isBlockLoaded(panelPos)
                || !(world.getTileEntity(panelPos) instanceof com.cogwork.cogworkbridge.content.logistics.factory.FactoryPanelTileEntity panel)
                || !panel.active(slot) || pos.distanceSq(panelPos) >= 256
                || !(world.getBlockState(pos).getBlock() instanceof DisplayLinkBlock)
                || !(world.getBlockState(panelPos).getBlock() instanceof com.cogwork.cogworkbridge.content.logistics.factory.FactoryPanelBlock)) return false;
        EnumFacing facing = world.getBlockState(pos).getValue(DisplayLinkBlock.FACING);
        if (world.getBlockState(panelPos).getValue(com.cogwork.cogworkbridge.content.logistics.factory.FactoryPanelBlock.FACING) != facing
                || facing.getAxis() == EnumFacing.Axis.X && pos.getX() != panelPos.getX()
                || facing.getAxis() == EnumFacing.Axis.Y && pos.getY() != panelPos.getY()
                || facing.getAxis() == EnumFacing.Axis.Z && pos.getZ() != panelPos.getZ()) return false;
        LinkedPanel entry = new LinkedPanel(panelPos.toImmutable(), slot);
        if (remove ? !linkedPanels.remove(entry) : linkedPanels.contains(entry) || linkedPanels.size() >= 128) return false;
        if (!remove) linkedPanels.add(entry);
        changed();
        if (mode == 19) updateTarget();
        return true;
    }
    public void configure(int mode, int targetLine, String label, boolean alternateFormat, int rateInterval) {
        this.mode = Math.floorMod(mode, MODE_COUNT);
        int rows = getTargetLineCount();
        this.targetLine = Math.max(0, Math.min(rows > 0 ? rows - 1 : 63, targetLine));
        this.label = label == null ? "" : label.substring(0, Math.min(64, label.length()));
        this.alternateFormat = alternateFormat;
        this.rateInterval = Math.floorMod(rateInterval, this.mode == 2 ? 5 : 3);
        NBTTagCompound sourceConfig = new NBTTagCompound();
        sourceConfig.setString("Label", this.label);
        sourceConfig.setBoolean("AlternateFormat", alternateFormat);
        sourceConfig.setInteger("RateInterval", this.rateInterval);
        sourceConfigs.setTag(Integer.toString(this.mode), sourceConfig);
        changed();
        updateTarget();
    }
    @Override public void update() {
        if (world == null || world.isRemote || !(world.getBlockState(pos).getBlock() instanceof DisplayLinkBlock)) return;
        boolean powered = externallyPowered();
        boolean powerChanged = !powerKnown || observedPower != powered;
        if (powerKnown && observedPower && !powered && mode == 20) { stopwatchStart = world.getTotalWorldTime(); markDirty(); }
        if (powerKnown && observedPower && !powered && (mode == 15 || mode == 16)) {
            if (mode == 15) collectedItems = 0;
            else { sampleCount = sampleIndex = 0; lastItemTime = -1; lastItemAmount = 0; }
            markDirty();
        }
        powerKnown = true; observedPower = powered;
        if (powerChanged) changed();
        if (powered && (powerChanged || world.getTotalWorldTime() % 20 == 0)) updateTarget();
    }
    private boolean externallyPowered() {
        EnumFacing back = world.getBlockState(pos).getValue(DisplayLinkBlock.FACING).getOpposite();
        for (EnumFacing side : EnumFacing.VALUES)
            if (side != back && world.getRedstonePower(pos.offset(side), side) > 0) return true;
        return false;
    }
    /** Called once for each actual item stack accepted beneath the attached tunnel. */
    public void onTunnelItems(int amount) {
        if (world == null || world.isRemote || amount <= 0 || (mode != 15 && mode != 16)
                || !(world.getBlockState(pos).getBlock() instanceof DisplayLinkBlock) || !externallyPowered()) return;
        if (mode == 15) collectedItems = Math.min(Long.MAX_VALUE - amount, collectedItems) + amount;
        else {
            long now = world.getTotalWorldTime();
            if (lastItemTime >= 0 && now > lastItemTime) {
                rateSamples[sampleIndex] = amount / (float) (now - lastItemTime);
                sampleIndex = (sampleIndex + 1) % rateSamples.length;
                sampleCount = Math.min(rateSamples.length, sampleCount + 1);
            }
            lastItemTime = now; lastItemAmount = amount;
        }
        markDirty();
    }
    private void updateTarget() {
        if (world == null || world.isRemote || offset.equals(BlockPos.ORIGIN)) return;
        if (!(world.getBlockState(pos).getBlock() instanceof DisplayLinkBlock)) return;
        EnumFacing facing = world.getBlockState(pos).getValue(DisplayLinkBlock.FACING);
        BlockPos sourcePos = pos.offset(facing.getOpposite());
        BlockPos targetPos = pos.add(offset);
        if (!world.isBlockLoaded(sourcePos) || !world.isBlockLoaded(targetPos)) return;
        if (!externallyPowered()) return;
        TileEntity target = world.getTileEntity(targetPos);
        List<String> values = mode >= 21 && mode <= 23 ? com.cogwork.cogworkbridge.content.trains.track.RailwayDisplayData.read(world.getTileEntity(sourcePos),mode,label,getTargetColumns(),trainNamePercent,platformChars)
                : mode == 14 ? readScoreboard()
                : mode == 19 ? factoryStatus(sourcePos)
                : mode == 10 && world.getTileEntity(sourcePos) instanceof com.cogwork.cogworkbridge.content.fluids.FluidTankTileEntity tank
                ? boilerStatus(tank)
                : mode == 11 || mode == 12
                ? readList(sourcePos, facing, mode == 12)
                : Collections.singletonList(readSource(sourcePos, facing));
        if (values.isEmpty()) values = Collections.singletonList("");
        if (!label.isEmpty() && mode != 10 && mode != 11 && mode != 12 && mode != 14 && mode != 17 && mode != 19 && mode != 21 && mode != 22)
            values = Collections.singletonList(label + " " + values.get(0));
        int previous = targetPos.equals(lastOutputTarget) && targetLine == lastOutputStart ? lastOutputLines : 0;
        boolean multiline = mode == 10 || mode == 11 || mode == 12 || mode == 14 || mode == 19 || mode == 22;
        int remainingRows = Math.max(0, getTargetLineCount() - targetLine);
        int lineCount = Math.min(remainingRows, multiline ? remainingRows : Math.max(values.size(), previous));
        TileEntity reservationTarget = reservationTarget(target);
        boolean signChanged = false;
        for (int i = 0; i < lineCount; i++) {
            String value = i < values.size() ? values.get(i) : "";
            int line = targetLine + i;
            if (reservationTarget != null) {
                if (i == 0) reserveLine(reservationTarget, line);
                else if (lineReserved(reservationTarget, line)
                        || target instanceof DisplayBoardTileEntity board && board.isLineManual(line)) break;
            }
            if (target instanceof DisplayTarget display) display.acceptDisplayText(line, value);
            else if (LecternDisplayTarget.supports(target)) LecternDisplayTarget.accept(target, line, value);
            else if (target instanceof TileEntitySign sign && line < sign.signText.length
                    && !sign.signText[line].getUnformattedText().equals(value)) {
                sign.signText[line] = new TextComponentString(value);
                signChanged = true;
            }
        }
        if (signChanged && target instanceof TileEntitySign sign) {
            sign.markDirty();
            world.notifyBlockUpdate(targetPos, world.getBlockState(targetPos), world.getBlockState(targetPos), 3);
        }
        lastOutputTarget = targetPos;
        lastOutputStart = targetLine;
        lastOutputLines = Math.min(64 - targetLine, values.size());
    }

    private TileEntity reservationTarget(TileEntity target) {
        if (!(target instanceof DisplayBoardTileEntity))
            return target instanceof TileEntitySign || LecternDisplayTarget.supports(target) ? target : null;
        BlockPos controller = target.getPos();
        net.minecraft.block.state.IBlockState state = world.getBlockState(controller);
        EnumFacing left = state.getValue(DisplayBoardBlock.FACING).rotateY();
        for (int i = 0; i < 31 && world.isBlockLoaded(controller.up()) && world.getBlockState(controller.up()) == state; i++) controller = controller.up();
        for (int i = 0; i < 31 && world.isBlockLoaded(controller.offset(left)) && world.getBlockState(controller.offset(left)) == state; i++) controller = controller.offset(left);
        return world.getTileEntity(controller);
    }
    private void reserveLine(TileEntity target, int line) {
        if (line == 0) return;
        NBTTagCompound data = target.getTileData();
        NBTTagCompound reservations = data.getCompoundTag("CogworkBridgeDisplayLinks");
        String key = "Line" + line;
        if (reservations.hasKey(key) && reservations.getLong(key) == pos.toLong()) return;
        reservations.setLong(key, pos.toLong()); data.setTag("CogworkBridgeDisplayLinks", reservations); target.markDirty();
    }
    private boolean lineReserved(TileEntity target, int line) {
        NBTTagCompound data = target.getTileData();
        NBTTagCompound reservations = data.getCompoundTag("CogworkBridgeDisplayLinks");
        String key = "Line" + line;
        if (!reservations.hasKey(key)) return false;
        BlockPos owner = BlockPos.fromLong(reservations.getLong(key));
        if (!owner.equals(pos)) {
            if (!world.isBlockLoaded(owner)) return true;
            TileEntity other = world.getTileEntity(owner);
            if (other instanceof DisplayLinkTileEntity link && link.hasTarget() && link.getTargetLine() == line
                    && world.isBlockLoaded(link.getTargetPosition())
                    && reservationTarget(world.getTileEntity(link.getTargetPosition())) == target) return true;
        }
        reservations.removeTag(key);
        if (reservations.isEmpty()) data.removeTag("CogworkBridgeDisplayLinks");
        target.markDirty();
        return false;
    }

    private List<String> boilerStatus(com.cogwork.cogworkbridge.content.fluids.FluidTankTileEntity tank) {
        int available = Math.max(0, getTargetLineCount() - targetLine);
        if (available == 1) return Collections.singletonList("Not enough room for boiler status");
        if (available < 4) return java.util.Arrays.asList("Not enough room", "for boiler status");
        return tank.getBoilerStatus();
    }

    private List<String> factoryStatus(BlockPos sourcePos) {
        List<String> result = new ArrayList<>();
        if (!linkedPanels.isEmpty()) {
            for (LinkedPanel entry : linkedPanels) {
                if (!world.isBlockLoaded(entry.pos())) continue;
                if (world.getTileEntity(entry.pos()) instanceof com.cogwork.cogworkbridge.content.logistics.factory.FactoryPanelTileEntity panel)
                    appendFactoryStatus(result, panel, entry.slot());
            }
        } else if (world.getTileEntity(sourcePos) instanceof com.cogwork.cogworkbridge.content.logistics.factory.FactoryPanelTileEntity panel) {
            for (int slot = 0; slot < 4; slot++) appendFactoryStatus(result, panel, slot);
        }
        return result;
    }
    private void appendFactoryStatus(List<String> result,
            com.cogwork.cogworkbridge.content.logistics.factory.FactoryPanelTileEntity panel, int slot) {
        ItemStack item = panel.filter(slot);
        if (!panel.active(slot) || item.isEmpty()) return;
        String status = panel.target(slot) <= 0 ? " " : panel.current(slot) >= panel.target(slot) ? "\u2714"
                : panel.promised(slot) > 0 ? "\u2191" : "\u25aa";
        result.add(formatListNumber(panel.current(slot)) + " " + status + " " + item.getDisplayName());
    }

    private List<String> readScoreboard() {
        net.minecraft.scoreboard.ScoreObjective objective = world.getScoreboard().getObjective(label);
        if (objective == null) return Collections.singletonList("No objective: " + label);
        List<net.minecraft.scoreboard.Score> scores = new ArrayList<>(world.getScoreboard().getSortedScores(objective));
        scores.sort((a, b) -> Integer.compare(b.getScorePoints(), a.getScorePoints()));
        List<String> result = new ArrayList<>();
        for (net.minecraft.scoreboard.Score score : scores) {
            if (result.size() >= 64) break;
            result.add(score.getPlayerName() + " " + formatListNumber(score.getScorePoints()));
        }
        return result;
    }

    private TileEntity contentTile(BlockPos sourcePos) {
        TileEntity source = world.getTileEntity(sourcePos);
        if (source instanceof com.cogwork.cogworkbridge.content.redstone.DetectorTileEntity
                && world.getBlockState(sourcePos).getBlock() instanceof com.cogwork.cogworkbridge.content.redstone.DetectorBlock) {
            BlockPos target = sourcePos.offset(com.cogwork.cogworkbridge.content.redstone.DetectorBlock.target(world.getBlockState(sourcePos)));
            return world.isBlockLoaded(target) ? world.getTileEntity(target) : null;
        }
        return source;
    }
    private EnumFacing contentSide(BlockPos sourcePos, EnumFacing fallback) {
        return world.getTileEntity(sourcePos) instanceof com.cogwork.cogworkbridge.content.redstone.DetectorTileEntity
                && world.getBlockState(sourcePos).getBlock() instanceof com.cogwork.cogworkbridge.content.redstone.DetectorBlock
                ? com.cogwork.cogworkbridge.content.redstone.DetectorBlock.target(world.getBlockState(sourcePos)).getOpposite() : fallback;
    }
    private ItemStack contentFilter(BlockPos sourcePos) {
        return world.getTileEntity(sourcePos) instanceof com.cogwork.cogworkbridge.content.redstone.DetectorTileEntity detector
                ? detector.getFilter() : ItemStack.EMPTY;
    }
    private List<String> readList(BlockPos sourcePos, EnumFacing side, boolean fluids) {
        TileEntity tile = contentTile(sourcePos);
        side = contentSide(sourcePos, side);
        ItemStack filter = contentFilter(sourcePos);
        if (tile == null) return Collections.emptyList();
        Map<String, Long> counts = new HashMap<>();
        if (fluids) {
            IFluidHandler handler = tile.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, side);
            if (handler == null) handler = tile.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, null);
            if (handler != null) for (IFluidTankProperties tank : handler.getTankProperties()) {
                FluidStack stack = tank.getContents();
                if (stack != null && stack.amount > 0 && com.cogwork.cogworkbridge.content.fluids.ListFilterItem.matchesFluid(filter, stack)) counts.merge(stack.getLocalizedName(), (long) stack.amount, Long::sum);
            }
        } else {
            IItemHandler handler = tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side);
            if (handler == null) handler = tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
            if (handler != null) for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack stack = handler.getStackInSlot(i);
                if (!stack.isEmpty() && com.cogwork.cogworkbridge.content.fluids.ListFilterItem.matchesItem(filter, stack)) counts.merge(stack.getDisplayName(), (long) stack.getCount(), Long::sum);
            }
            else if (tile instanceof IInventory inventory) for (int i = 0; i < inventory.getSizeInventory(); i++) {
                ItemStack stack = inventory.getStackInSlot(i);
                if (!stack.isEmpty() && com.cogwork.cogworkbridge.content.fluids.ListFilterItem.matchesItem(filter, stack)) counts.merge(stack.getDisplayName(), (long) stack.getCount(), Long::sum);
            }
        }
        List<Map.Entry<String, Long>> ordered = new ArrayList<>(counts.entrySet());
        ordered.sort((a, b) -> {
            int byCount = Long.compare(b.getValue(), a.getValue());
            return byCount != 0 ? byCount : a.getKey().compareTo(b.getKey());
        });
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, Long> entry : ordered) {
            if (result.size() >= 64) break;
            result.add(formatListNumber(entry.getValue()) + (fluids ? " mB " : " ") + entry.getKey());
        }
        return result;
    }
    private String formatListNumber(long number) {
        if (alternateFormat || Math.abs(number) < 1000) return Long.toString(number);
        if (Math.abs(number) >= 1000000) return (number / 1000000) + "M";
        return (number / 1000) + "K";
    }
    private String readSource(BlockPos sourcePos, EnumFacing side) {
        TileEntity tile = world.getTileEntity(sourcePos);
        int selected = mode;
        if (selected == 24 || selected == 0 && world.getBlockState(sourcePos).getBlock() instanceof com.cogwork.cogworkbridge.content.decoration.SeatBlock) {
            for (com.cogwork.cogworkbridge.content.decoration.SeatEntity seat : world.getEntitiesWithinAABB(
                    com.cogwork.cogworkbridge.content.decoration.SeatEntity.class, new net.minecraft.util.math.AxisAlignedBB(sourcePos)))
                if (!seat.getPassengers().isEmpty()) return seat.getPassengers().get(0).getDisplayName().getUnformattedText();
            return "";
        }
        if (selected == 0) {
            if (tile instanceof com.cogwork.cogworkbridge.content.contraptions.elevator.RedstoneContactTileEntity
                    && world.getBlockState(sourcePos).getBlock() instanceof com.cogwork.cogworkbridge.content.contraptions.elevator.RedstoneContactBlock contact
                    && contact.isElevator()) selected = 17;
            else if (tile instanceof NixieTubeTileEntity) selected = 9;
            else if (tile instanceof com.cogwork.cogworkbridge.content.kinetics.clock.CuckooClockTileEntity) selected = 6;
            else if (tile instanceof KineticTileEntity) selected = 1;
            else if (tile instanceof com.cogwork.cogworkbridge.content.redstone.DetectorTileEntity detector
                    && detector.targetType() != 0) selected = 8;
            else if (tile != null && tile.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, side)) selected = 4;
            else if (tile != null && (tile.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side) || tile instanceof IInventory)) selected = 5;
            else selected = 3;
        }
        if (selected == 1 && tile instanceof KineticTileEntity kinetic)
            return String.format(Locale.ROOT, "%.0f RPM", alternateFormat ? kinetic.getSpeed() : Math.abs(kinetic.getSpeed()));
        if (selected == 2 && tile instanceof KineticTileEntity kinetic) {
            float stress = kinetic.getNetworkStress(), capacity = kinetic.getNetworkCapacity();
            double fraction = capacity <= 0 ? 0 : stress / capacity;
            return switch (rateInterval) {
                case 0 -> progressBar(fraction);
                case 1 -> String.format(Locale.ROOT, "%.0f%%", 100D * fraction);
                case 2 -> String.format(Locale.ROOT, "%.0f SU", stress);
                case 3 -> String.format(Locale.ROOT, "%.0f SU", capacity);
                default -> String.format(Locale.ROOT, "%.0f SU", capacity - stress);
            };
        }
        if (selected == 3) {
            int power = world.getRedstonePowerFromNeighbors(sourcePos);
            net.minecraft.block.state.IBlockState state = world.getBlockState(sourcePos);
            for (net.minecraft.block.properties.IProperty<?> property : state.getPropertyKeys()) {
                if (!property.getName().equals("power")) continue;
                Object value = state.getProperties().get(property);
                if (value instanceof Number) power = Math.max(power, ((Number) value).intValue());
            }
            return alternateFormat ? progressBar(power / 15D) : Integer.toString(power);
        }
        if (selected == 8 && tile instanceof com.cogwork.cogworkbridge.content.redstone.DetectorTileEntity detector && detector.targetType() != 0) {
            double fraction = detector.getMaximum() <= 0 ? 0D : Math.max(0D, detector.getAmount()) / detector.getMaximum();
            return alternateFormat ? progressBar(fraction) : String.format(Locale.ROOT, "%.0f%%", 100D * fraction);
        }
        ItemStack filter = contentFilter(sourcePos);
        if (selected == 4 || selected == 5 || selected == 7 || selected == 8) {
            tile = contentTile(sourcePos);
            side = contentSide(sourcePos, side);
        }
        if (selected == 4 && tile != null) {
            IFluidHandler handler = tile.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, side);
            if (handler == null) handler = tile.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, null);
            if (handler != null) {
                int amount = 0;
                for (IFluidTankProperties tank : handler.getTankProperties()) {
                    FluidStack fluid = tank.getContents();
                    if (fluid != null && com.cogwork.cogworkbridge.content.fluids.ListFilterItem.matchesFluid(filter, fluid)) amount += fluid.amount;
                }
                return amount + " mB";
            }
        }
        if (selected == 5 && tile != null) {
            int count = 0;
            IItemHandler handler = tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side);
            if (handler == null) handler = tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
            if (handler != null) for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack stack = handler.getStackInSlot(i);
                if (com.cogwork.cogworkbridge.content.fluids.ListFilterItem.matchesItem(filter, stack)) count += stack.getCount();
            } else if (tile instanceof IInventory inv) for (int i = 0; i < inv.getSizeInventory(); i++) {
                ItemStack stack = inv.getStackInSlot(i);
                if (com.cogwork.cogworkbridge.content.fluids.ListFilterItem.matchesItem(filter, stack)) count += stack.getCount();
            }
            return Integer.toString(count);
        }
        if (selected == 20 && tile instanceof com.cogwork.cogworkbridge.content.kinetics.clock.CuckooClockTileEntity clock) {
            if (clock.getSpeed() == 0) return "--:--";
            if (stopwatchStart < 0) { stopwatchStart=world.getTotalWorldTime(); markDirty(); }
            long seconds=Math.max(0,world.getTotalWorldTime()-stopwatchStart)/20;
            long hours=seconds/3600, minutes=seconds/60%60;seconds%=60;
            return hours==0 ? String.format(Locale.ROOT,"%2d:%02d",minutes,seconds) : String.format(Locale.ROOT,"%2d:%02d:%02d",hours,minutes,seconds);
        }
        if (selected == 6) {
            if (tile instanceof com.cogwork.cogworkbridge.content.kinetics.clock.CuckooClockTileEntity clock && clock.getSpeed()==0) return "--:--";
            long ticks = Math.floorMod(world.getWorldTime() + 6000L, 24000L);
            long hours = ticks / 1000L, minutes = (ticks % 1000L) * 60L / 1000L;
            if (tile instanceof com.cogwork.cogworkbridge.content.kinetics.clock.CuckooClockTileEntity) minutes = minutes / 5L * 5L;
            if (alternateFormat) return String.format(Locale.ROOT, "%02d:%02d %s", hours % 12 == 0 ? 12 : hours % 12,
                    minutes, hours >= 12 ? "PM" : "AM");
            return String.format(Locale.ROOT, "%02d:%02d", hours, minutes);
        }
        if (selected == 7 && tile != null) {
            StringBuilder combined = new StringBuilder();
            BlockPos current = sourcePos;
            for (int i = 0; i < 32 && world.isBlockLoaded(current); i++, current = current.offset(side.getOpposite())) {
                TileEntity candidate = world.getTileEntity(current);
                ItemStack stack;
                if (candidate instanceof com.cogwork.cogworkbridge.content.logistics.belt.BeltTileEntity belt) {
                    stack = Math.abs(belt.renderPosition(1F) - .5F) <= .25F ? belt.getCarried() : ItemStack.EMPTY;
                } else if (candidate instanceof com.cogwork.cogworkbridge.content.logistics.transport.TransportedItemHolder holder) {
                    stack = holder.getTransportedItem();
                } else if (candidate instanceof com.cogwork.cogworkbridge.content.logistics.ejector.EjectorTileEntity ejector) {
                    stack = ejector.getStackInSlot(0);
                } else break;
                if (!stack.isEmpty()) combined.append(stack.getDisplayName());
            }
            if (combined.length() > 0) return combined.toString();
            IItemHandler handler = tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side);
            if (handler != null) {
                for (int i = 0; i < handler.getSlots(); i++) {
                    ItemStack stack = handler.getStackInSlot(i);
                    if (!stack.isEmpty()) return stack.getDisplayName();
                }
            } else if (tile instanceof IInventory inventory) {
                for (int i = 0; i < inventory.getSizeInventory(); i++) {
                    ItemStack stack = inventory.getStackInSlot(i);
                    if (!stack.isEmpty()) return stack.getDisplayName();
                }
            }
        }
        if (selected == 8 && tile != null) {
            IFluidHandler fluid = tile.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, side);
            if (fluid != null) {
                long amount = 0, capacity = 0;
                for (IFluidTankProperties tank : fluid.getTankProperties()) {
                    if (tank.getContents() != null) amount += tank.getContents().amount;
                    capacity += Math.max(0, tank.getCapacity());
                }
                double fraction = capacity == 0 ? 0D : amount / (double) capacity;
                return alternateFormat ? progressBar(fraction) : String.format(Locale.ROOT, "%.0f%%", 100D * fraction);
            }
            IItemHandler handler = tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side);
            if (handler != null && handler.getSlots() > 0) {
                double fill = 0;
                for (int i = 0; i < handler.getSlots(); i++) {
                    ItemStack stack = handler.getStackInSlot(i);
                    if (!stack.isEmpty()) fill += stack.getCount() / (double) Math.max(1, Math.min(stack.getMaxStackSize(), handler.getSlotLimit(i)));
                }
                double fraction = fill / handler.getSlots();
                return alternateFormat ? progressBar(fraction) : String.format(Locale.ROOT, "%.0f%%", 100D * fraction);
            }
            if (tile instanceof IInventory inventory && inventory.getSizeInventory() > 0) {
                double fill = 0;
                for (int i = 0; i < inventory.getSizeInventory(); i++) {
                    ItemStack stack = inventory.getStackInSlot(i);
                    if (!stack.isEmpty()) fill += stack.getCount() / (double) Math.max(1, Math.min(stack.getMaxStackSize(), inventory.getInventoryStackLimit()));
                }
                double fraction = fill / inventory.getSizeInventory();
                return alternateFormat ? progressBar(fraction) : String.format(Locale.ROOT, "%.0f%%", 100D * fraction);
            }
        }
        if (selected == 9 && tile instanceof NixieTubeTileEntity tube) return tube.fullText();
        if (selected == 17 && tile instanceof com.cogwork.cogworkbridge.content.contraptions.elevator.RedstoneContactTileEntity contact)
            return contact.getCurrentFloor();
        if (selected == 18 && tile instanceof com.cogwork.cogworkbridge.content.redstone.DetectorTileEntity detector)
            return detector.observedPackageAddress();
        if (selected == 10 && tile instanceof com.cogwork.cogworkbridge.content.fluids.FluidTankTileEntity tank && tank.isBoiler())
            return String.format(Locale.ROOT, "%.0f%%", tank.getBoilerEfficiency() * 100F);
        if (selected == 13 && tile instanceof net.minecraft.tileentity.TileEntityEnchantmentTable) {
            float power = 0;
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0 || !world.isAirBlock(sourcePos.add(x, 0, z))
                        || !world.isAirBlock(sourcePos.add(x, 1, z))) continue;
                power += net.minecraftforge.common.ForgeHooks.getEnchantPower(world, sourcePos.add(x * 2, 0, z * 2));
                power += net.minecraftforge.common.ForgeHooks.getEnchantPower(world, sourcePos.add(x * 2, 1, z * 2));
                if (x != 0 && z != 0) for (int y = 0; y <= 1; y++) {
                    power += net.minecraftforge.common.ForgeHooks.getEnchantPower(world, sourcePos.add(x * 2, y, z));
                    power += net.minecraftforge.common.ForgeHooks.getEnchantPower(world, sourcePos.add(x, y, z * 2));
                }
            }
            int result = net.minecraft.enchantment.EnchantmentHelper.calcItemStackEnchantability(world.rand, 2,
                    (int) power, new ItemStack(net.minecraft.init.Items.DIAMOND_PICKAXE));
            return Integer.toString(result);
        }
        if (selected == 15 && tile instanceof com.cogwork.cogworkbridge.content.logistics.tunnel.BeltTunnelTileEntity)
            return Long.toString(collectedItems);
        if (selected == 16 && tile instanceof com.cogwork.cogworkbridge.content.logistics.tunnel.BeltTunnelTileEntity) {
            float rate = 0;
            for (int i = 0; i < sampleCount; i++) rate += rateSamples[i];
            if (sampleCount > 0) rate /= sampleCount;
            if (lastItemTime < 0 || rate > 0 && world.getTotalWorldTime() - lastItemTime > 2D * lastItemAmount / rate) rate = 0;
            double interval = rateInterval == 2 ? 72000D : rateInterval == 1 ? 1200D : 20D;
            return String.format(Locale.ROOT, "%.1f", rate * interval);
        }
        return "";
    }
    private void changed() {
        markDirty();
        if (world != null && !world.isRemote) world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setBoolean("VisualPowered", observedPower);
        tag.setLong("TargetOffset", offset.toLong()); tag.setInteger("Mode", mode); tag.setInteger("TargetLine", targetLine);
        tag.setString("Label", label);
        tag.setBoolean("AlternateFormat", alternateFormat);
        tag.setInteger("RateInterval", rateInterval);
        tag.setInteger("TrainNamePercent",trainNamePercent);tag.setInteger("PlatformChars",platformChars);
        tag.setTag("SourceConfigs", sourceConfigs.copy());
        NBTTagList panels = new NBTTagList();
        for (LinkedPanel panel : linkedPanels) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setLong("Pos", panel.pos().toLong()); entry.setInteger("Slot", panel.slot()); panels.appendTag(entry);
        }
        tag.setTag("LinkedGauges", panels);
        tag.setLong("CollectedItems", collectedItems); tag.setLong("LastItemTime", lastItemTime); tag.setLong("StopwatchStart", stopwatchStart);
        tag.setInteger("LastItemAmount", lastItemAmount); tag.setInteger("RateSamples", sampleCount);
        tag.setInteger("RateIndex", sampleIndex);
        for (int i = 0; i < rateSamples.length; i++) tag.setFloat("Rate" + i, rateSamples[i]);
        return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        observedPower = tag.getBoolean("VisualPowered");
        offset = tag.hasKey("TargetOffset") ? BlockPos.fromLong(tag.getLong("TargetOffset")) : BlockPos.ORIGIN;
        mode = Math.floorMod(tag.getInteger("Mode"), MODE_COUNT); targetLine = Math.max(0, Math.min(63, tag.getInteger("TargetLine")));
        label = tag.getString("Label");
        alternateFormat = tag.getBoolean("AlternateFormat");
        rateInterval = Math.floorMod(tag.getInteger("RateInterval"), mode == 2 ? 5 : 3);
        trainNamePercent=tag.hasKey("TrainNamePercent")?Math.max(0,Math.min(72,tag.getInteger("TrainNamePercent"))):50;
        platformChars=tag.hasKey("PlatformChars")?Math.max(0,Math.min(15,tag.getInteger("PlatformChars"))):3;
        sourceConfigs = tag.hasKey("SourceConfigs", 10) ? tag.getCompoundTag("SourceConfigs").copy() : new NBTTagCompound();
        linkedPanels.clear();
        NBTTagList panels = tag.getTagList("LinkedGauges", 10);
        for (int i = 0; i < Math.min(128, panels.tagCount()); i++) {
            NBTTagCompound entry = panels.getCompoundTagAt(i);
            int slot = entry.getInteger("Slot");
            if (slot < 0 || slot > 3 || !entry.hasKey("Pos")) continue;
            LinkedPanel panel = new LinkedPanel(BlockPos.fromLong(entry.getLong("Pos")), slot);
            if (!linkedPanels.contains(panel)) linkedPanels.add(panel);
        }
        collectedItems = Math.max(0, tag.getLong("CollectedItems"));
        lastItemTime = tag.hasKey("LastItemTime") ? tag.getLong("LastItemTime") : -1;
        stopwatchStart = tag.hasKey("StopwatchStart") ? tag.getLong("StopwatchStart") : -1;
        lastItemAmount = Math.max(0, tag.getInteger("LastItemAmount"));
        sampleCount = Math.max(0, Math.min(rateSamples.length, tag.getInteger("RateSamples")));
        sampleIndex = Math.floorMod(tag.getInteger("RateIndex"), rateSamples.length);
        for (int i = 0; i < rateSamples.length; i++) rateSamples[i] = tag.getFloat("Rate" + i);
    }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager network, SPacketUpdateTileEntity packet) {
        readFromNBT(packet.getNbtCompound());
        if (world != null) world.markBlockRangeForRenderUpdate(pos, pos);
    }
}
