package com.cogwork.cogworkbridge.content.logistics.port;

import com.cogwork.cogworkbridge.content.logistics.packagebox.PackageItem;
import com.cogwork.cogworkbridge.content.logistics.transport.ItemTransfer;
import com.cogwork.cogworkbridge.content.equipment.clipboard.ClipboardData;
import com.cogwork.cogworkbridge.content.kinetics.chainconveyor.ChainConveyorTileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import javax.annotation.Nullable;

public final class PackagePortTileEntity extends TileEntity implements ITickable {
    private String address = "";
    @Nullable private BlockPos mailStation;
    private String stationName="";
    private long mailRevision;
    private boolean applyingMail;
    public BlockPos mailStation() { return mailStation; }
    public String stationName() { return stationName; }
    void refreshStationName(String name){if(!stationName.equals(name)){stationName=name;changed();}}
    public long mailRevision() { return mailRevision; }
    public void setMailRevision(long revision) { mailRevision=revision;markDirty(); }
    public void bindStation(BlockPos station,String name) { mailStation=station.toImmutable();stationName=name;changed(); }
    public void applyMailInventory(NBTTagCompound inventoryData,long revision) {
        applyingMail=true;try { inventory.deserializeNBT(inventoryData);mailRevision=revision;changed(); } finally {applyingMail=false;}
    }
    @Override public void onLoad() { super.onLoad();if(world!=null&&!world.isRemote&&mailStation!=null)MailboxNetwork.get(world).load(this); }
    private boolean accepts = true;
    private boolean goggles;
    private int viewers;
    private boolean tutorialFlag;
    @Nullable private BlockPos chainTarget;
    @Nullable private BlockPos chainEnd;
    private float chainAngle;
    private boolean chainReversed;
    private ItemStack animatedPackage = ItemStack.EMPTY;
    private int animationTicks;
    private boolean sending;
    private float openProgress, previousOpenProgress, flagProgress, previousFlagProgress;
    private final ItemStackHandler inventory = new ItemStackHandler(18) {
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return PackageItem.isPackage(stack) ? super.insertItem(slot, stack, simulate) : stack;
        }
        @Override protected void onContentsChanged(int slot) { changed(); }
    };
    private final IItemHandler automation = new IItemHandler() {
        @Override public int getSlots() { return inventory.getSlots(); }
        @Override public ItemStack getStackInSlot(int slot) { return inventory.getStackInSlot(slot); }
        @Override public int getSlotLimit(int slot) { return inventory.getSlotLimit(slot); }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!PackageItem.isPackage(stack) || accepts && PackageItem.matchAddress(PackageItem.address(stack), address)) return stack;
            return inventory.insertItem(slot, stack, simulate);
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            ItemStack stack = inventory.getStackInSlot(slot);
            return !accepts || !PackageItem.isPackage(stack) || !PackageItem.matchAddress(PackageItem.address(stack), address)
                    ? ItemStack.EMPTY : inventory.extractItem(slot, amount, simulate);
        }
    };
    public ItemStackHandler inventory() { return inventory; }
    void animateMailTransfer(boolean sent) { sending=sent;animationTicks=10;changed(); }
    @Override public void setPos(BlockPos newPos) {
        BlockPos old = this.pos;
        if (old != null && newPos != null && !old.equals(newPos)) {
            BlockPos delta = newPos.subtract(old);
            if (chainTarget != null) chainTarget = chainTarget.add(delta);
            if (chainEnd != null) chainEnd = chainEnd.add(delta);
        }
        super.setPos(newPos);
    }
    public String address() { return address; }
    public boolean accepts() { return accepts; }
    public boolean hasGoggles() { return goggles; }
    @Nullable public BlockPos chainTarget() { return chainTarget; }
    @Nullable public BlockPos chainEnd() { return chainEnd; }
    public float chainAngle() { return chainAngle; }
    public ItemStack animatedPackage() { return animatedPackage; }
    public boolean isSending() { return sending; }
    public float animationProgress(float partial) { return Math.max(0F, Math.min(1F, (10F - animationTicks + partial) / 10F)); }
    public boolean matchesIncoming(ItemStack stack) { return accepts && PackageItem.isPackage(stack) && PackageItem.matchAddress(PackageItem.address(stack), address); }
    public boolean acceptFromChain(ItemStack stack) {
        if (world == null || world.isRemote || animationTicks > 0 || !matchesIncoming(stack)
                || !ItemHandlerHelper.insertItemStacked(inventory, stack.copy(), true).isEmpty()) return false;
        animatedPackage = stack.copy(); animatedPackage.setCount(1); sending = false; animationTicks = 10; changed();
        if (world.getBlockState(pos).getBlock() instanceof PackagePortBlock block && block.isFrog())
            com.cogwork.cogworkbridge.content.advancement.AllAdvancements.awardAt(world, pos, "frogport");
        return true;
    }
    public void bindChain(BlockPos wheel, float angle) {
        bindChain(wheel, null, angle);
    }
    public void bindChain(BlockPos wheel, @Nullable BlockPos end, float position) {
        if (world == null || world.isRemote || !world.isBlockLoaded(wheel)
                || !(world.getTileEntity(wheel) instanceof ChainConveyorTileEntity conveyor)) return;
        if (end != null && !conveyor.connections().contains(end)) return;
        if (chainTarget != null && world.isBlockLoaded(chainTarget) && world.getTileEntity(chainTarget) instanceof ChainConveyorTileEntity old) old.unregisterPort(pos);
        chainTarget = wheel.toImmutable(); chainEnd = end == null ? null : end.toImmutable();
        chainAngle = end == null ? (position % 360F + 360F) % 360F : Math.max(0, Math.min(conveyor.geometry(end).length(), position));
        chainReversed = conveyor.getSpeed() < 0;
        conveyor.registerPort(pos); changed();
    }
    @Nullable public Vec3d chainPosition() {
        if (chainTarget == null) return null;
        if (chainEnd != null) {
            if (world == null || !world.isBlockLoaded(chainTarget)
                    || !(world.getTileEntity(chainTarget) instanceof ChainConveyorTileEntity wheel)) return null;
            var geometry = wheel.geometry(chainEnd);
            return geometry.start().add(geometry.end().subtract(geometry.start()).scale(Math.min(1, chainAngle / Math.max(.001F, geometry.length()))));
        }
        double rad = Math.toRadians(chainAngle);
        return new Vec3d(chainTarget.getX() + .5D + Math.sin(rad) * .875D, chainTarget.getY() + 6D/16D,
                chainTarget.getZ() + .5D + Math.cos(rad) * .875D);
    }
    public void equipGoggles() { if (!goggles) { goggles = true; changed(); } }
    public boolean open() { return viewers > 0; }
    public float openProgress(float partial) { return previousOpenProgress + (openProgress - previousOpenProgress) * partial; }
    public float flagProgress(float partial) { return previousFlagProgress + (flagProgress - previousFlagProgress) * partial; }
    public void openViewer() { viewers++; changed(); }
    public void closeViewer() { viewers = Math.max(0, viewers - 1); changed(); }
    public void configure(String value, boolean receive) {
        address = value.substring(0, Math.min(25, value.length())); accepts = receive; changed();
    }
    public void copyAddressToClipboard(ItemStack stack) {
        if (address.trim().isEmpty()) return;
        NBTTagCompound data = ClipboardData.fromItem(stack);
        if (ClipboardData.isReadOnly(data)) return;
        for (int page = 0; page < ClipboardData.pageCount(data); page++)
            for (String line : ClipboardData.lines(data, page))
                if (line.equals("#" + address) || line.equals("# " + address)) return;
        for (int page = 0; page < ClipboardData.MAX_PAGES; page++) {
            String[] lines = ClipboardData.lines(data, page); ItemStack[] icons = ClipboardData.icons(data, page);
            for (int row = 0; row < ClipboardData.ROWS; row++) {
                if (!lines[row].isEmpty() || !icons[row].isEmpty()) continue;
                lines[row] = "#" + address;
                ClipboardData.setPage(data, page, lines, ClipboardData.checked(data, page), icons);
                ClipboardData.toItem(stack, data); return;
            }
        }
    }
    public int comparator() {
        int filled = 0;
        for (int i = 0; i < inventory.getSlots(); i++) if (!inventory.getStackInSlot(i).isEmpty()) filled++;
        return filled == 0 ? 0 : 1 + filled * 14 / inventory.getSlots();
    }
    @Override public void update() {
        if (world == null) return;
        if (world.isRemote) {
            previousOpenProgress = openProgress; previousFlagProgress = flagProgress;
            if (animationTicks > 0) animationTicks--;
            float animation = animationTicks > 0 ? Math.min(1F, Math.min((10F-animationTicks) / 3F, animationTicks / 3F)) : 0F;
            openProgress = approach(openProgress, open() ? 1F : animation, .35F);
            flagProgress = approach(flagProgress, tutorialFlag || comparator() > 0 ? 1F : 0F, .1F);
            return;
        }
        if (animationTicks > 0) {
            animationTicks--;
            if (sending && animationTicks == 5 && !animatedPackage.isEmpty()) {
                TileEntity target = chainTarget != null && world.isBlockLoaded(chainTarget) ? world.getTileEntity(chainTarget) : null;
                if (target instanceof ChainConveyorTileEntity wheel && wheel.getSpeed() != 0
                        && (chainEnd == null ? wheel.addLoopPackage(animatedPackage, chainAngle) : wheel.addTravelPackage(animatedPackage, chainEnd, chainAngle))) animatedPackage = ItemStack.EMPTY;
                else { restoreAnimated(); animationTicks = 0; }
                changed();
            }
            if (animationTicks == 0) {
                if (!sending && !animatedPackage.isEmpty()) restoreAnimated();
                animatedPackage = ItemStack.EMPTY; changed();
            }
            return;
        }
        if (world.getTotalWorldTime() % 10 != 0
                || !(world.getBlockState(pos).getBlock() instanceof PackagePortBlock block) || !block.isFrog()) return;
        if (chainTarget != null && world.isBlockLoaded(chainTarget)
                && !(world.getTileEntity(chainTarget) instanceof ChainConveyorTileEntity)) unbindChain();
        if (chainTarget != null && world.isBlockLoaded(chainTarget) && world.getTileEntity(chainTarget) instanceof ChainConveyorTileEntity wheel) {
            if (chainEnd != null && !wheel.connections().contains(chainEnd)) { unbindChain(); return; }
            if (chainEnd != null && wheel.getSpeed() != 0 && (wheel.getSpeed() < 0) != chainReversed
                    && world.isBlockLoaded(chainEnd) && world.getTileEntity(chainEnd) instanceof ChainConveyorTileEntity other) {
                BlockPos oldTarget = chainTarget; float length = wheel.geometry(chainEnd).length();
                wheel.unregisterPort(pos); chainTarget = chainEnd; chainEnd = oldTarget;
                chainAngle = Math.max(0, length - chainAngle); chainReversed = !chainReversed; wheel = other; changed();
            }
            wheel.registerPort(pos);
            if (wheel.getSpeed() != 0 && wheel.hasCapacity() && trySend(wheel)) return;
        }
        TileEntity below = world.getTileEntity(pos.down());
        if (below instanceof PackagePortTileEntity) return;
        IItemHandler target = ItemTransfer.at(world, pos.down(), EnumFacing.UP);
        ItemTransfer.move(automation, target, 1, PackageItem::isPackage, stack -> ItemTransfer.drop(world, pos, stack));
    }
    private boolean trySend(ChainConveyorTileEntity wheel) {
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!PackageItem.isPackage(stack) || matchesIncoming(stack)) continue;
            animatedPackage = inventory.extractItem(i, 1, false); sending = true; animationTicks = 10; changed(); return true;
        }
        IItemHandler below = ItemTransfer.at(world, pos.down(), EnumFacing.UP);
        if (below == null) return false;
        for (int i = 0; i < below.getSlots(); i++) {
            ItemStack stack = below.extractItem(i, 1, true);
            if (!PackageItem.isPackage(stack) || matchesIncoming(stack)) continue;
            ItemStack taken = below.extractItem(i, 1, false);
            if (taken.isEmpty()) continue;
            animatedPackage = taken; sending = true; animationTicks = 10; changed(); return true;
        }
        return false;
    }
    private void restoreAnimated() {
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(inventory, animatedPackage, false);
        if (!remainder.isEmpty()) ItemTransfer.drop(world, pos, remainder);
        animatedPackage = ItemStack.EMPTY;
    }
    public void onRemoved() {
        if (world == null || world.isRemote) return;
        MailboxNetwork.get(world).remove(pos);
        mailStation=null;
        if (chainTarget != null && world.isBlockLoaded(chainTarget) && world.getTileEntity(chainTarget) instanceof ChainConveyorTileEntity wheel) wheel.unregisterPort(pos);
        if (!animatedPackage.isEmpty()) { ItemTransfer.drop(world, pos, animatedPackage); animatedPackage = ItemStack.EMPTY; }
    }
    public void unbindChain() {
        if (world == null || world.isRemote || chainTarget == null) return;
        if (world.isBlockLoaded(chainTarget) && world.getTileEntity(chainTarget) instanceof ChainConveyorTileEntity wheel) wheel.unregisterPort(pos);
        chainTarget = null; chainEnd = null;
        if (!animatedPackage.isEmpty()) restoreAnimated();
        animationTicks = 0; changed();
    }
    @Override public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox() { return new net.minecraft.util.math.AxisAlignedBB(pos).grow(6); }
    private static float approach(float value, float target, float step) {
        return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
    }
    private void changed() {
        markDirty();
        if (world != null && !world.isRemote) {
            if(mailStation!=null&&!applyingMail)MailboxNetwork.get(world).publish(this);
            var state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 2); world.updateComparatorOutputLevel(pos, state.getBlock());
        }
    }
    @Override public boolean hasCapability(Capability<?> cap, @Nullable EnumFacing side) { return cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY || super.hasCapability(cap, side); }
    @Nullable @Override public <T> T getCapability(Capability<T> cap, @Nullable EnumFacing side) {
        return cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY ? CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(automation) : super.getCapability(cap, side);
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag); tag.setTag("Inventory", inventory.serializeNBT()); tag.setString("Address", address);
        if(mailStation!=null)tag.setLong("MailStation",mailStation.toLong());tag.setString("MailStationName",stationName);tag.setLong("MailRevision",mailRevision);
        tag.setBoolean("Accepts", accepts); tag.setBoolean("Open", viewers > 0); tag.setBoolean("Goggles", goggles);
        if (chainTarget != null) { tag.setLong("ChainTargetOffset", chainTarget.subtract(pos).toLong()); tag.setFloat("ChainAngle", chainAngle);
            if (chainEnd != null) tag.setLong("ChainEndOffset", chainEnd.subtract(pos).toLong()); tag.setBoolean("ChainReversed", chainReversed); }
        if (!animatedPackage.isEmpty()) tag.setTag("AnimatedPackage", animatedPackage.writeToNBT(new NBTTagCompound()));
        tag.setInteger("AnimationTicks", animationTicks); tag.setBoolean("Sending", sending);
        if (tutorialFlag) tag.setBoolean("TutorialFlag", true);
        return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag); applyingMail=true;
        try { if (tag.hasKey("Inventory", 10)) inventory.deserializeNBT(tag.getCompoundTag("Inventory")); } finally { applyingMail=false; }
        mailStation=tag.hasKey("MailStation")?BlockPos.fromLong(tag.getLong("MailStation")):null;stationName=tag.getString("MailStationName");mailRevision=tag.getLong("MailRevision");
        address = tag.getString("Address"); if (address.length() > 25) address = address.substring(0, 25);
        accepts = !tag.hasKey("Accepts") || tag.getBoolean("Accepts");
        goggles = tag.getBoolean("Goggles");
        chainTarget = tag.hasKey("ChainTargetOffset") ? pos.add(BlockPos.fromLong(tag.getLong("ChainTargetOffset")))
                : tag.hasKey("ChainTarget") ? BlockPos.fromLong(tag.getLong("ChainTarget")) : null;
        chainEnd = tag.hasKey("ChainEndOffset") ? pos.add(BlockPos.fromLong(tag.getLong("ChainEndOffset")))
                : tag.hasKey("ChainEnd") ? BlockPos.fromLong(tag.getLong("ChainEnd")) : null;
        chainReversed = tag.getBoolean("ChainReversed");
        chainAngle = tag.getFloat("ChainAngle"); animationTicks = Math.max(0, Math.min(10, tag.getInteger("AnimationTicks")));
        tutorialFlag = tag.getBoolean("TutorialFlag");
        animatedPackage = tag.hasKey("AnimatedPackage", 10) ? new ItemStack(tag.getCompoundTag("AnimatedPackage")) : ItemStack.EMPTY;
        sending = tag.getBoolean("Sending");
        if (world != null && world.isRemote) viewers = tag.getBoolean("Open") ? 1 : 0;
    }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) {
        boolean wasOpen = open(); readFromNBT(packet.getNbtCompound());
        if (world != null && wasOpen != open()) world.markBlockRangeForRenderUpdate(pos, pos);
    }
}
