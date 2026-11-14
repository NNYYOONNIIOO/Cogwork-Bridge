package com.cogwork.cogworkbridge.content.fluids;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import com.cogwork.cogworkbridge.core.CogworkTileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import com.cogwork.cogworkbridge.content.contraptions.bearing.ContraptionWorld;
import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

import javax.annotation.Nullable;

public class FluidTankTileEntity extends CogworkTileEntity implements ITickable {
    public static int capacityPerBlock() { return FluidConfig.fluidTankCapacity * 1000; }

    protected final FluidTank tank = new FluidTank(capacityPerBlock()) {
        @Override protected void onContentsChanged() { contentsChanged(); }
    };

    @Nullable private BlockPos controller;
    private int width = 1;
    private int height = 1;
    private boolean windows = true;
    private boolean updateConnectivity = true;
    private boolean restructuring;
    private int lastComparator = -1;
    private int luminosity;
    private int lastLightWidth = -1;
    private int lastLightHeight = -1;
    private int lastLightLevel = -1;
    private int lastLightLayers = -1;
    private boolean lastLightGas;
    private final BoilerData boiler = new BoilerData();

    private final IFluidHandler handler = new IFluidHandler() {
        @Override public IFluidTankProperties[] getTankProperties() {
            FluidTankTileEntity root = getControllerTile();
            return root == null ? new IFluidTankProperties[0] : root.boiler.active()
                    ? new IFluidTankProperties[]{new net.minecraftforge.fluids.capability.FluidTankProperties(null, 10000, true, false)}
                    : root.tank.getTankProperties();
        }
        @Override public int fill(FluidStack resource, boolean doFill) {
            FluidTankTileEntity root = getControllerTile();
            if (root == null || resource == null || resource.amount <= 0) return 0;
            if (root.boiler.active()) {
                if (resource.getFluid() != net.minecraftforge.fluids.FluidRegistry.WATER) return 0;
                if (doFill && root.world != null && !root.world.isRemote) root.boiler.addWater(resource.amount);
                return resource.amount;
            }
            return root.tank.fill(resource, doFill);
        }
        @Override public FluidStack drain(FluidStack resource, boolean doDrain) {
            FluidTankTileEntity root = getControllerTile();
            return root == null || root.boiler.active() ? null : root.tank.drain(resource, doDrain);
        }
        @Override public FluidStack drain(int maxDrain, boolean doDrain) {
            FluidTankTileEntity root = getControllerTile();
            return root == null || root.boiler.active() ? null : root.tank.drain(maxDrain, doDrain);
        }
    };

    @Override
    public void update() {
        if (world == null || world.isRemote || !(world.getBlockState(pos).getBlock() instanceof FluidTankBlock)) return;
        if (updateConnectivity) FluidTankConnectivity.rebuild(this);
        if (isController()) boiler.tick(this);
    }

    public float getBoilerEfficiency() {
        FluidTankTileEntity root = getControllerTile();
        return root == null ? 0F : root.boiler.efficiency(root);
    }
    public boolean isBoiler() { FluidTankTileEntity root = getControllerTile(); return root != null && root.boiler.active(); }
    public boolean hasBoilerHeat() { FluidTankTileEntity root = getControllerTile(); return root != null && root.boiler.heated(); }
    public float getBoilerGauge() { FluidTankTileEntity root = getControllerTile(); return root == null ? 0F : root.boiler.gauge(root); }
    public int getBoilerHeat() { FluidTankTileEntity root = getControllerTile(); return root == null ? 0 : root.boiler.heat(); }
    public boolean hasPassiveBoilerHeat() { FluidTankTileEntity root = getControllerTile(); return root != null && root.boiler.passiveHeat(); }
    public float getBoilerWaterRate() { FluidTankTileEntity root = getControllerTile(); return root == null ? 0F : root.boiler.waterRate(); }
    /** Snapshot for a display link attached to any part of the tank. */
    public java.util.List<String> getBoilerStatus() {
        FluidTankTileEntity root = getControllerTile();
        if (root == null || !root.boiler.active()) return java.util.Collections.emptyList();
        NBTTagCompound data = root.boiler.write();
        String heat = data.getInteger("Heat") == 0 && data.getBoolean("Passive")
                ? "passive" : Integer.toString(data.getInteger("Heat"));
        return java.util.Arrays.asList(String.format(java.util.Locale.ROOT, "BOILER %.0f%%", root.boiler.efficiency(root) * 100F),
                "SIZE " + root.getWidth() + "x" + root.getWidth() + "x" + root.getHeight(),
                String.format(java.util.Locale.ROOT, "WATER %.1f mB/t", data.getFloat("WaterRate")), "HEAT " + heat);
    }

    public IFluidHandler getFluidHandler() { return handler; }
    public FluidStack getFluid() {
        FluidTankTileEntity root = getControllerTile();
        return root == null ? null : root.tank.getFluid();
    }
    public int getCapacity() {
        FluidTankTileEntity root = getControllerTile();
        return root == null ? 0 : root.tank.getCapacity();
    }
    public boolean isController() { return controller == null || controller.equals(pos); }
    public BlockPos getControllerPos() { return isController() ? pos : controller; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public boolean hasWindows() { return windows; }
    public int getLuminosity() { return luminosity; }

    @Override
    public void setPos(BlockPos position) {
        if (controller != null) controller = controller.add(position.subtract(pos));
        super.setPos(position);
    }

    public static BlockPos controllerOffset(NBTTagCompound tag) {
        if (tag.hasKey("TankControllerOffset", 10)) {
            NBTTagCompound offset = tag.getCompoundTag("TankControllerOffset");
            return new BlockPos(offset.getInteger("X"), offset.getInteger("Y"), offset.getInteger("Z"));
        }
        if (tag.hasKey("TankController", 4)) {
            BlockPos storedPos = new BlockPos(tag.getInteger("x"), tag.getInteger("y"), tag.getInteger("z"));
            return BlockPos.fromLong(tag.getLong("TankController")).subtract(storedPos);
        }
        return BlockPos.ORIGIN;
    }

    public static void setControllerOffset(NBTTagCompound tag, BlockPos offset) {
        tag.removeTag("TankController");
        if (BlockPos.ORIGIN.equals(offset)) {
            tag.removeTag("TankControllerOffset");
            return;
        }
        NBTTagCompound encoded = new NBTTagCompound();
        encoded.setInteger("X", offset.getX());
        encoded.setInteger("Y", offset.getY());
        encoded.setInteger("Z", offset.getZ());
        tag.setTag("TankControllerOffset", encoded);
    }

    public static void relocateTag(NBTTagCompound tag, BlockPos position) {
        setControllerOffset(tag, controllerOffset(tag));
        tag.setInteger("x", position.getX());
        tag.setInteger("y", position.getY());
        tag.setInteger("z", position.getZ());
    }

    public String getModelName() {
        // Modern Ponder structures store each member's model in its block state, not in its NBT.
        NBTTagCompound visual = getTileData().getCompoundTag("TutorialState");
        if (visual.hasKey("top", 8) && visual.hasKey("bottom", 8) && visual.hasKey("shape", 8)) {
            boolean top = visual.getString("top").equals("true");
            boolean bottom = visual.getString("bottom").equals("true");
            String level = top && bottom ? "single" : bottom ? "bottom" : top ? "top" : "middle";
            String shape = visual.getString("shape");
            if (shape.equals("plain")) return level;
            if (shape.equals("window") || shape.equals("window_nw") || shape.equals("window_ne")
                    || shape.equals("window_sw") || shape.equals("window_se")) return level + "_" + shape;
        }
        int relativeY = pos.getY() - getControllerPos().getY();
        String level = height == 1 ? "single" : relativeY == 0 ? "bottom"
                : relativeY == height - 1 ? "top" : "middle";
        if (!windows) return level;
        int x = pos.getX() - getControllerPos().getX();
        int z = pos.getZ() - getControllerPos().getZ();
        if (width == 1 || width == 3 && Math.abs(x - z) == 1) return level + "_window";
        if (width != 2) return level;
        return level + "_window_" + (z == 0 ? "n" : "s") + (x == 0 ? "w" : "e");
    }

    @Nullable
    public FluidTankTileEntity getControllerTile() {
        if (isController()) return this;
        if (world == null || !world.isBlockLoaded(controller)) return null;
        TileEntity tile = world.getTileEntity(controller);
        return tile instanceof FluidTankTileEntity other && other.getClass() == getClass()
                && other.isController() ? other : null;
    }

    void requestConnectivityUpdate() { updateConnectivity = true; }
    void finishConnectivityUpdate() { updateConnectivity = false; }
    void setRestructuring(boolean value) { restructuring = value; }

    /** Split a carried multiblock before the moving chunk removes one of its parts. */
    public void prepareForContraptionRemoval(ContraptionWorld localWorld) {
        if (localWorld != null && !localWorld.isRemote
                && localWorld.getBlockState(pos).getBlock() instanceof FluidTankBlock) {
            FluidTankConnectivity.beforeRemoved(localWorld, pos);
        }
    }

    void setStructure(@Nullable BlockPos root, int width, int height, boolean windows) {
        controller = root == null || root.equals(pos) ? null : root.toImmutable();
        this.width = width;
        this.height = height;
        this.windows = windows;
        updateConnectivity = false;
        lastComparator = -1;
        lastLightLevel = -1;
    }

    void syncStructure() {
        markDirty();
        if (world != null && !world.isRemote) {
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 2);
            world.updateComparatorOutputLevel(pos, getBlockType());
        }
    }

    public void toggleWindows() {
        FluidTankTileEntity root = getControllerTile();
        if (root == null || world == null || world.isRemote) return;
        boolean next = !root.windows;
        BlockPos origin = root.pos;
        for (int y = 0; y < root.height; y++) {
            for (int x = 0; x < root.width; x++) {
                for (int z = 0; z < root.width; z++) {
                    TileEntity part = world.getTileEntity(origin.add(x, y, z));
                    if (part instanceof FluidTankTileEntity tankPart
                            && tankPart.getControllerPos().equals(origin)) {
                        tankPart.windows = next;
                        tankPart.syncStructure();
                        tankPart.recheckLight();
                    }
                }
            }
        }
    }

    protected void contentsChanged() {
        if (restructuring) return;
        markDirty();
        if (world == null || world.isRemote) return;
        world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 2);
        FluidStack fluid = getFluid();
        int capacity = getCapacity();
        refreshLighting();
        int level = fluid == null || fluid.amount <= 0 || capacity <= 0 ? 0
                : Math.min(15, 1 + (int) Math.floor(14.0D * fluid.amount / capacity));
        if (lastComparator == level) return;
        lastComparator = level;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                for (int z = 0; z < width; z++) {
                    BlockPos part = pos.add(x, y, z);
                    if (world.isBlockLoaded(part)) world.updateComparatorOutputLevel(part, world.getBlockState(part).getBlock());
                }
            }
        }
    }

    void refreshLighting() {
        if (world == null || world.isRemote || !isController()) return;
        FluidStack fluid = getFluid();
        int capacity = getCapacity();
        int light = fluid == null ? 0 : Math.min(15, Math.max(0,
                (int) (fluid.getFluid().getLuminosity(fluid) / 1.2F)));
        boolean gas = fluid != null && fluid.getFluid().isGaseous(fluid);
        float fraction = fluid == null || capacity <= 0 ? 0 : Math.min(1.0F, fluid.amount / (float) capacity);
        int filledLayers = (int) (fraction * height + 1.0F);
        if (lastLightWidth == width && lastLightHeight == height && lastLightLevel == light
                && lastLightLayers == filledLayers && lastLightGas == gas) return;
        lastLightWidth = width;
        lastLightHeight = height;
        lastLightLevel = light;
        lastLightLayers = filledLayers;
        lastLightGas = gas;
        for (int y = 0; y < height; y++) {
            boolean bright = gas ? height - y <= filledLayers : y < filledLayers;
            int next = bright ? light : light > 0 ? 1 : 0;
            for (int x = 0; x < width; x++) {
                for (int z = 0; z < width; z++) {
                    BlockPos at = pos.add(x, y, z);
                    if (!world.isBlockLoaded(at)) continue;
                    TileEntity tile = world.getTileEntity(at);
                    if (!(tile instanceof FluidTankTileEntity part)
                            || !part.getControllerPos().equals(pos) || part.luminosity == next) continue;
                    part.luminosity = next;
                    part.syncStructure();
                    part.recheckLight();
                }
            }
        }
    }

    private void recheckLight() {
        // A moving chunk has no real light engine; its projected world properties
        // update light when the tile's changed data is persisted.
        if (world != null && !(world instanceof ContraptionWorld) && !(world instanceof FakeWorld)) {
            world.checkLight(pos);
        }
    }

    @Override public AxisAlignedBB getRenderBoundingBox() {
        return isController() ? new AxisAlignedBB(pos, pos.add(width, height, width)) : super.getRenderBoundingBox();
    }

    @Override
    public boolean hasCapability(Capability<?> capability, EnumFacing facing) {
        return capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY || super.hasCapability(capability, facing);
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing facing) {
        return capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY
                ? CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(getFluidHandler()) : super.getCapability(capability, facing);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setTag("Tank", tank.writeToNBT(new NBTTagCompound()));
        setControllerOffset(tag, controller == null ? BlockPos.ORIGIN : controller.subtract(pos));
        tag.setInteger("TankWidth", width);
        tag.setInteger("TankHeight", height);
        tag.setBoolean("TankWindows", windows);
        tag.setInteger("TankLuminosity", luminosity);
        tag.setTag("Boiler", boiler.write());
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        BlockPos offset = controllerOffset(tag);
        controller = BlockPos.ORIGIN.equals(offset) ? null : pos.add(offset);
        width = Math.max(1, Math.min(3, tag.getInteger("TankWidth")));
        // Preserve the stored shape until connectivity has redistributed it under the current limit.
        height = Math.max(1, Math.min(256, tag.getInteger("TankHeight")));
        windows = !tag.hasKey("TankWindows", 1) || tag.getBoolean("TankWindows");
        luminosity = Math.max(0, Math.min(15, tag.getInteger("TankLuminosity")));
        if (tag.hasKey("Boiler", 10)) boiler.read(tag.getCompoundTag("Boiler"));
        lastLightLevel = -1;
        if (getClass() == FluidTankTileEntity.class || getClass() == CreativeFluidTankTileEntity.class) {
            tank.setCapacity(isController() ? capacityPerBlock() * width * width * height : capacityPerBlock());
        }
        if (tag.hasKey("Tank", 10)) tank.readFromNBT(tag.getCompoundTag("Tank"));
        updateConnectivity = true;
    }

    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) {
        int oldLight = windows ? luminosity : 0;
        readFromNBT(packet.getNbtCompound());
        if (world != null && world.isRemote) {
            if (oldLight != (windows ? luminosity : 0)) recheckLight();
            world.markBlockRangeForRenderUpdate(pos.add(-1, 0, -1), pos.add(1, 0, 1));
        }
    }
}
