package com.cogwork.cogworkbridge.content.contraptions.bearing;

import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.block.BlockShulkerBox;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerBeacon;
import net.minecraft.inventory.ContainerBrewingStand;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.ContainerDispenser;
import net.minecraft.inventory.ContainerFurnace;
import net.minecraft.inventory.ContainerHopper;
import net.minecraft.inventory.ContainerShulkerBox;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.network.play.server.SPacketOpenWindow;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.World;
import net.minecraft.world.IInteractionObject;
import net.minecraft.world.ILockableContainer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Opens a vanilla container backed by a TileEntity carried by a contraption.
 *
 * <p>The server selects the GUI from the captured block state. The captured
 * tile entity is exposed through an inventory wrapper whose usability check
 * does not compare its local coordinates with the player's world position.
 * Unknown modded menus are intentionally left to their own interaction code.</p>
 */
public final class ContraptionGuiProxy implements IInteractionObject {

    private static final java.util.Map<Integer, ActiveGui> ACTIVE_GUIS =
            new java.util.HashMap<>();

    private final ContraptionInventory inventory;
    private final ContainerKind kind;
    private final BearingContraptionEntity contraption;
    private final BlockPos localPos;

    private ContraptionGuiProxy(BearingContraptionEntity contraption,
                                BlockPos localPos, IInventory inventory,
                                ContainerKind kind) {
        this.contraption = contraption;
        this.localPos = localPos.toImmutable();
        this.inventory = new ContraptionInventory(
                inventory, contraption);
        this.kind = kind;
    }

    public static boolean tryOpen(EntityPlayerMP player,
                                  BearingContraptionEntity contraption,
                                  BlockPos localPos, IBlockState state) {
        if (player == null || contraption == null || localPos == null
                || state == null || contraption.getContraption() == null) {
            return false;
        }
        net.minecraft.util.ResourceLocation blockName = state.getBlock().getRegistryName();
        if (blockName != null && !"minecraft".equals(blockName.getNamespace())
                && !"cogworkbridge".equals(blockName.getNamespace())) return false;
        net.minecraft.world.World interactionWorld =
                contraption.getInteractionWorld();
        TileEntity tile = interactionWorld.getTileEntity(localPos);
        if (tile == null) {
            return false;
        }
        if (state.getBlock() instanceof BlockChest chest) {
            ILockableContainer inventory = chest.getContainer(
                    interactionWorld, localPos, false);
            if (inventory == null || inventory.isLocked()
                    || !player.canOpen(inventory.getLockCode())) {
                return false;
            }
            openWindow(player, new ContraptionGuiProxy(
                    contraption, localPos, inventory, ContainerKind.CHEST));
            return true;
        }
        if (tile instanceof IInteractionObject interaction
                && canOpen(interaction, player)) {
            openInteractionObject(player, interaction, contraption,
                    localPos, tile);
            return true;
        }
        if (!(tile instanceof IInventory inventory)
                || inventory.getSizeInventory() <= 0
                || inventory instanceof ILockableContainer lockable
                && (lockable.isLocked()
                || !player.canOpen(lockable.getLockCode()))) {
            return false;
        }
        ContainerKind kind = ContainerKind.from(
                state.getBlock(), inventory);
        if (kind == ContainerKind.NONE) {
            return false;
        }
        openWindow(player, new ContraptionGuiProxy(
                contraption, localPos, inventory, kind));
        return true;
    }

    /**
     * A carried TileEntity is invalidated when its snapshot is written back
     * or moved into a new entity. Its old menu cannot safely keep writing
     * through that TileEntity. Save through the still-valid carrier first,
     * then close the menu before the structure transition destroys it.
     */
    public static void closeForStructureTransition(
            BearingContraptionEntity contraption) {
        List<ActiveGui> matching = new ArrayList<>();
        for (ActiveGui active : new ArrayList<>(ACTIVE_GUIS.values())) {
            if (active.contraption == contraption) {
                matching.add(active);
            }
        }
        for (ActiveGui active : matching) {
            if (active.player.openContainer != active.container) {
                ACTIVE_GUIS.remove(active.player.getEntityId(), active);
                continue;
            }
            try {
                active.flush();
            } catch (RuntimeException exception) {
                com.cogwork.cogworkbridge.CogworkBridge.LOGGER.error(
                        "Failed to persist contraption GUI before transition",
                        exception);
            } finally {
                if (active.player.openContainer == active.container) {
                    active.player.closeContainer();
                }
                ACTIVE_GUIS.remove(active.player.getEntityId(), active);
            }
        }
    }

    public static void closeStale(EntityPlayerMP player) {
        ActiveGui active = ACTIVE_GUIS.get(player.getEntityId());
        if (active == null) {
            return;
        }
        if (player.openContainer != active.container) {
            ACTIVE_GUIS.remove(player.getEntityId(), active);
            return;
        }
        if (!active.isValid()) {
            player.closeContainer();
        }
    }

    /** Keeps a foreign mod's actual Container type visible to its own packets. */
    public static boolean canInteract(Object containerObject, Object playerObject) {
        if (!(containerObject instanceof Container container) || !(playerObject instanceof EntityPlayer player)) return false;
        ActiveGui active = ACTIVE_GUIS.get(player.getEntityId());
        if (active != null && active.player == player && active.container == container && active.foreign) {
            return active.isValid();
        }
        return container.canInteractWith(player);
    }

    /** Run an optional mod's scheduled GUI operation in the carried tile's coordinates. */
    public static Runnable contextualize(Runnable task, Object playerObject) {
        if (!(playerObject instanceof EntityPlayerMP player)) return task;
        return () -> {
            ActiveGui active = ACTIVE_GUIS.get(player.getEntityId());
            if (active == null || active.player != player || !active.foreign || player.openContainer != active.container) {
                task.run();
                return;
            }
            if (!active.isValid()) {
                player.closeScreen();
                return;
            }
            TileEntity tile = active.contraption.getInteractionWorld().getTileEntity(active.localPos);
            if (tile == null || tile.getWorld() == null) return;
            World previousWorld = player.world;
            double x = player.posX, y = player.posY, z = player.posZ;
            net.minecraft.util.math.AxisAlignedBB bounds = player.getEntityBoundingBox();
            net.minecraft.util.math.Vec3d local = active.contraption.worldPointToLocal(
                    new net.minecraft.util.math.Vec3d(x, y, z));
            BlockPos anchor = active.contraption.getAnchor();
            boolean outer = active.contraption.startInteractionSession();
            try {
                player.setWorld(tile.getWorld());
                player.posX = local.x - anchor.getX();
                player.posY = local.y - anchor.getY();
                player.posZ = local.z - anchor.getZ();
                player.setEntityBoundingBox(bounds.offset(player.posX - x, player.posY - y, player.posZ - z));
                task.run();
            } finally {
                player.setWorld(previousWorld);
                player.posX = x;
                player.posY = y;
                player.posZ = z;
                player.setEntityBoundingBox(bounds);
                try {
                    if (outer) active.contraption.commitInteractionChanges();
                } finally {
                    active.contraption.finishInteractionSession();
                }
                if (active.contraption.getContraption() != null) active.flush();
            }
        };
    }

    public static void onContainerClosed(EntityPlayer player, Container container) {
        ActiveGui active = ACTIVE_GUIS.get(player.getEntityId());
        if (active == null || active.player != player || active.container != container) return;
        try {
            if (active.foreign) active.flush();
        } finally {
            ACTIVE_GUIS.remove(player.getEntityId(), active);
        }
    }

    /**
     * Close real-world menus that are about to lose their TileEntity to a
     * contraption capture. Their state has already been copied into the
     * snapshot at this point, so only the now-stale open window is closed.
     */
    public static void closeCapturedBlock(
            net.minecraft.server.MinecraftServer server,
            java.util.Collection<BlockPos> capturedPositions,
            int dimension) {
        if (server == null || capturedPositions == null
                || capturedPositions.isEmpty()) {
            return;
        }
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            if (player.world.provider.getDimension() != dimension) {
                continue;
            }
            Container open = player.openContainer;
            if (open == null || open == player.inventoryContainer) {
                continue;
            }
            for (Slot slot : open.inventorySlots) {
                if (!(slot.inventory instanceof TileEntity tile)
                        || !capturedPositions.contains(tile.getPos())) {
                    continue;
                }
                player.closeContainer();
                break;
            }
        }
    }

    private static boolean canOpen(IInteractionObject interaction,
                                   EntityPlayerMP player) {
        if (interaction instanceof ILockableContainer lockable
                && (lockable.isLocked()
                || !player.canOpen(lockable.getLockCode()))) {
            return false;
        }
        return !(interaction instanceof IInventory inventory)
                || inventory.isUsableByPlayer(player);
    }

    private static void openInteractionObject(
            EntityPlayerMP player, IInteractionObject interaction,
            BearingContraptionEntity contraption, BlockPos localPos,
            TileEntity tile) {
        IInventory carrierInventory = tile instanceof IInventory inventory
                ? new ContraptionInventory(
                        inventory, contraption)
                : player.inventory;
        Container delegate;
        World previousWorld = player.world;
        player.closeContainer();
        ACTIVE_GUIS.remove(player.getEntityId());
        player.getNextWindowId();
        int windowId = player.currentWindowId;
        try {
            player.setWorld(contraption.getInteractionWorld());
            delegate = interaction.createContainer(
                    player.inventory, player);
        } finally {
            player.setWorld(previousWorld);
        }
        CommitContainer container = new CommitContainer(
                delegate, contraption, localPos,
                carrierInventory);
        player.connection.sendPacket(new SPacketOpenWindow(
                windowId, interaction.getGuiID(),
                interaction.getDisplayName(),
                container.inventorySlots.size()));
        container.windowId = windowId;
        player.openContainer = container;
        container.addListener(player);
        player.sendContainerToPlayer(container);
        ACTIVE_GUIS.put(player.getEntityId(),
                new ActiveGui(player, container));
        MinecraftForge.EVENT_BUS.post(
                new PlayerContainerEvent.Open(player, container));
    }

    /**
     * Vanilla displayGui sends an open-window packet without a slot count.
     * The client then creates a generic local menu instead of its specialised
     * furnace, hopper, beacon, or brewing GUI. Send the counted packet and
     * mirror the remaining displayGui lifecycle explicitly.
     */
    private static void openWindow(EntityPlayerMP player,
                                   ContraptionGuiProxy proxy) {
        player.closeContainer();
        ACTIVE_GUIS.remove(player.getEntityId());
        player.getNextWindowId();
        int windowId = player.currentWindowId;
        CommitContainer container = (CommitContainer) proxy.createContainer(
                player.inventory, player);
        player.connection.sendPacket(new SPacketOpenWindow(
                windowId, proxy.getGuiID(), proxy.getDisplayName(),
                proxy.inventory.getSizeInventory()));
        container.windowId = windowId;
        player.openContainer = container;
        container.addListener(player);
        player.sendContainerToPlayer(container);
        ACTIVE_GUIS.put(player.getEntityId(),
                new ActiveGui(player, container));
        MinecraftForge.EVENT_BUS.post(
                new PlayerContainerEvent.Open(player, container));
    }

    /** Open a mod's own Forge menu against its carried tile on both sides. */
    public static void openForeignContainer(EntityPlayerMP player,
                                            BearingContraptionEntity contraption,
                                            BlockPos localPos, Container delegate,
                                            String modId, int guiId) {
        player.closeContainer();
        ACTIVE_GUIS.remove(player.getEntityId());
        player.getNextWindowId();
        int windowId = player.currentWindowId;
        TileEntity tile = contraption.getInteractionWorld().getTileEntity(localPos);
        delegate.windowId = windowId;
        com.cogwork.cogworkbridge.network.ModNetwork.CHANNEL.sendTo(
                new ContraptionForgeGuiPacket(contraption.getEntityId(), localPos, windowId, modId, guiId), player);
        player.openContainer = delegate;
        delegate.addListener(player);
        player.sendContainerToPlayer(delegate);
        ACTIVE_GUIS.put(player.getEntityId(), new ActiveGui(player, delegate, contraption, localPos.toImmutable(), tile, true));
        MinecraftForge.EVENT_BUS.post(new PlayerContainerEvent.Open(player, delegate));
    }

    @Override
    public Container createContainer(InventoryPlayer playerInventory,
                                     EntityPlayer entityPlayer) {
        return new CommitContainer(
                kind.create(playerInventory, inventory, entityPlayer),
                contraption, localPos, inventory);
    }

    @Override
    public String getGuiID() {
        return kind.guiId;
    }

    @Override
    public String getName() {
        return inventory.getName();
    }

    @Override
    public boolean hasCustomName() {
        return inventory.hasCustomName();
    }

    @Override
    public ITextComponent getDisplayName() {
        return inventory.getDisplayName();
    }

    private enum ContainerKind {
        NONE(null),
        CHEST("minecraft:chest"),
        FURNACE("minecraft:furnace"),
        HOPPER("minecraft:hopper"),
        DISPENSER("minecraft:dispenser"),
        BREWING("minecraft:brewing_stand"),
        SHULKER("minecraft:shulker_box"),
        BEACON("minecraft:beacon"),
        GENERIC("minecraft:chest");

        private final String guiId;

        ContainerKind(String guiId) {
            this.guiId = guiId;
        }

        private static ContainerKind from(Block block, IInventory inventory) {
            if (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST
                    || block == Blocks.ENDER_CHEST) {
                return CHEST;
            }
            if (block == Blocks.FURNACE || block == Blocks.LIT_FURNACE) {
                return FURNACE;
            }
            if (block == Blocks.HOPPER) {
                return HOPPER;
            }
            if (block == Blocks.DISPENSER || block == Blocks.DROPPER) {
                return DISPENSER;
            }
            if (block == Blocks.BREWING_STAND) {
                return BREWING;
            }
            if (block instanceof BlockShulkerBox) {
                return SHULKER;
            }
            if (block == Blocks.BEACON) {
                return BEACON;
            }
            int size = inventory.getSizeInventory();
            return size > 0 && size <= 54 && size % 9 == 0
                    ? GENERIC : NONE;
        }

        private Container create(InventoryPlayer playerInventory,
                                 IInventory inventory,
                                 EntityPlayer player) {
            return switch (this) {
                case CHEST -> new ContainerChest(playerInventory, inventory, player);
                case FURNACE -> new ContainerFurnace(playerInventory, inventory);
                case HOPPER -> new ContainerHopper(playerInventory, inventory, player);
                case DISPENSER -> new ContainerDispenser(playerInventory, inventory);
                case BREWING -> new ContainerBrewingStand(playerInventory, inventory);
                case SHULKER -> new ContainerShulkerBox(
                        playerInventory, inventory, player);
                case BEACON -> new ContainerBeacon(playerInventory, inventory);
                case GENERIC -> new GenericContainer(playerInventory, inventory, player);
                case NONE -> throw new IllegalStateException("Unsupported contraption container");
            };
        }
    }

    private static final class GenericContainer extends Container {
        private final IInventory inventory;

        private GenericContainer(InventoryPlayer playerInventory,
                                 IInventory inventory, EntityPlayer player) {
            this.inventory = inventory;
            inventory.openInventory(player);
            int rows = Math.max(1, Math.min(6,
                    (inventory.getSizeInventory() + 8) / 9));
            for (int row = 0; row < rows; row++) {
                for (int column = 0; column < 9; column++) {
                    int slot = column + row * 9;
                    if (slot < inventory.getSizeInventory()) {
                        addSlotToContainer(new Slot(inventory, slot,
                                8 + column * 18, 18 + row * 18));
                    }
                }
            }
            int offset = (rows - 4) * 18;
            for (int row = 0; row < 3; row++) {
                for (int column = 0; column < 9; column++) {
                    addSlotToContainer(new Slot(playerInventory,
                            column + row * 9 + 9, 8 + column * 18,
                            103 + offset + row * 18));
                }
            }
            for (int column = 0; column < 9; column++) {
                addSlotToContainer(new Slot(playerInventory, column,
                        8 + column * 18, 161 + offset));
            }
        }

        @Override
        public boolean canInteractWith(EntityPlayer player) {
            return inventory.isUsableByPlayer(player);
        }

        @Override
        public void onContainerClosed(EntityPlayer player) {
            inventory.closeInventory(player);
        }
    }

    private static final class CommitContainer extends Container {
        private final Container delegate;
        private final BearingContraptionEntity contraption;
        private final BlockPos localPos;
        private final IInventory inventory;
        private final boolean foreign;

        private CommitContainer(Container delegate,
                                BearingContraptionEntity contraption,
                                BlockPos localPos, IInventory inventory) {
            this(delegate, contraption, localPos, inventory, false);
        }

        private CommitContainer(Container delegate,
                                BearingContraptionEntity contraption,
                                BlockPos localPos, IInventory inventory, boolean foreign) {
            this.delegate = delegate;
            this.contraption = contraption;
            this.localPos = localPos;
            this.inventory = inventory;
            this.foreign = foreign;
            this.inventorySlots = delegate.inventorySlots;
            this.inventoryItemStacks = delegate.inventoryItemStacks;
            this.windowId = delegate.windowId;
        }

        @Override
        public boolean canInteractWith(EntityPlayer player) {
            if (foreign) {
                if (contraption.isDead || contraption.getContraption() == null) return false;
                net.minecraft.util.math.Vec3d point = contraption.localBlockCenterToWorld(localPos);
                double reach = ContraptionInteractionMath.interactionReach(player);
                return player.getDistanceSq(point.x, point.y, point.z) <= reach * reach;
            }
            return delegate.canInteractWith(player);
        }

        @Override
        public void addListener(net.minecraft.inventory.IContainerListener listener) {
            // displayGui assigns the wrapper window id after createContainer().
            // Listener packets are emitted by the delegate's field-backed
            // container methods, so both instances must use that same id.
            delegate.windowId = windowId;
            delegate.addListener(listener);
        }

        @Override
        public void removeListener(net.minecraft.inventory.IContainerListener listener) {
            delegate.removeListener(listener);
        }

        @Override
        public void detectAndSendChanges() {
            delegate.detectAndSendChanges();
            if (contraption.getContraption() == null) {
                return;
            }
            inventory.markDirty();
            contraption.persistAndSyncInteractionBlock(localPos);
        }

        @Override
        public net.minecraft.item.ItemStack slotClick(
                int slotId, int dragType,
                net.minecraft.inventory.ClickType clickType,
                EntityPlayer player) {
            return delegate.slotClick(slotId, dragType, clickType, player);
        }

        @Override
        public net.minecraft.item.ItemStack transferStackInSlot(
                EntityPlayer player, int index) {
            return delegate.transferStackInSlot(player, index);
        }

        @Override
        public boolean enchantItem(EntityPlayer player, int id) {
            return delegate.enchantItem(player, id);
        }

        @Override
        public void updateProgressBar(int id, int data) {
            delegate.updateProgressBar(id, data);
        }

        @Override
        public short getNextTransactionID(
                net.minecraft.entity.player.InventoryPlayer playerInventory) {
            return delegate.getNextTransactionID(playerInventory);
        }

        @Override
        public boolean getCanCraft(EntityPlayer player) {
            return delegate.getCanCraft(player);
        }

        @Override
        public void setCanCraft(EntityPlayer player, boolean canCraft) {
            delegate.setCanCraft(player, canCraft);
        }

        @Override
        public void onContainerClosed(EntityPlayer player) {
            delegate.onContainerClosed(player);
            if (contraption.getContraption() != null) {
                inventory.markDirty();
                contraption.persistAndSyncInteractionBlock(localPos);
            }
            ACTIVE_GUIS.remove(player.getEntityId(), this);
        }

        private void flush() {
            if (contraption.getContraption() == null) {
                return;
            }
            delegate.detectAndSendChanges();
            inventory.markDirty();
            contraption.persistAndSyncInteractionBlock(localPos);
        }
    }

    private record ActiveGui(EntityPlayerMP player, Container container, BearingContraptionEntity contraption,
                             BlockPos localPos, TileEntity tile, boolean foreign) {
        private ActiveGui(EntityPlayerMP player, CommitContainer container) {
            this(player, container, container.contraption, container.localPos, null, false);
        }
        private boolean isValid() {
            if (contraption.isDead || contraption.getContraption() == null) return false;
            if (tile != null && (tile.isInvalid() || contraption.getInteractionWorld().getTileEntity(localPos) != tile)) return false;
            net.minecraft.util.math.Vec3d point = contraption.localBlockCenterToWorld(localPos);
            double reach = ContraptionInteractionMath.interactionReach(player);
            return player.getDistanceSq(point.x, point.y, point.z) <= reach * reach;
        }
        private void flush() {
            if (contraption.getContraption() == null) return;
            if (container instanceof CommitContainer commit) commit.flush();
            else {
                if (tile != null) tile.markDirty();
                contraption.persistAndSyncInteractionBlock(localPos);
            }
        }
    }

    private static final class ContraptionInventory implements IInventory {
        private final IInventory delegate;
        private final BearingContraptionEntity contraption;
        private final String name;

        private ContraptionInventory(IInventory delegate,
                                     BearingContraptionEntity contraption) {
            this.delegate = delegate;
            this.contraption = contraption;
            this.name = delegate.getName();
        }

        @Override
        public int getSizeInventory() {
            return delegate.getSizeInventory();
        }

        @Override
        public boolean isEmpty() {
            return delegate.isEmpty();
        }

        @Override
        public net.minecraft.item.ItemStack getStackInSlot(int index) {
            return delegate.getStackInSlot(index);
        }

        @Override
        public net.minecraft.item.ItemStack decrStackSize(int index, int count) {
            return delegate.decrStackSize(index, count);
        }

        @Override
        public net.minecraft.item.ItemStack removeStackFromSlot(int index) {
            return delegate.removeStackFromSlot(index);
        }

        @Override
        public void setInventorySlotContents(int index,
                                             net.minecraft.item.ItemStack stack) {
            delegate.setInventorySlotContents(index, stack);
        }

        @Override
        public int getInventoryStackLimit() {
            return delegate.getInventoryStackLimit();
        }

        @Override
        public void markDirty() {
            delegate.markDirty();
        }

        @Override
        public boolean isUsableByPlayer(EntityPlayer player) {
            if (player == null || contraption == null
                    || contraption.isDead || contraption.getContraption() == null) {
                return false;
            }
            // TileEntity inventories compare against their local moving-chunk
            // coordinates. Validate reach against the rendered world block,
            // while the interaction handler owns the authoritative reach and
            // visibility checks.
            if (player instanceof EntityPlayerMP serverPlayer) {
                double maxDistance = serverPlayer.isCreative()
                        ? 64.0D : 32.0D;
                return contraption.getDistanceSq(player)
                        <= maxDistance * maxDistance;
            }
            return contraption.getDistanceSq(player) <= 32.0D * 32.0D;
        }

        @Override
        public void openInventory(EntityPlayer player) {
            delegate.openInventory(player);
        }

        @Override
        public void closeInventory(EntityPlayer player) {
            delegate.closeInventory(player);
        }

        @Override
        public boolean isItemValidForSlot(int index,
                                          net.minecraft.item.ItemStack stack) {
            return delegate.isItemValidForSlot(index, stack);
        }

        @Override
        public int getField(int id) {
            return delegate.getField(id);
        }

        @Override
        public void setField(int id, int value) {
            delegate.setField(id, value);
        }

        @Override
        public int getFieldCount() {
            return delegate.getFieldCount();
        }

        @Override
        public void clear() {
            delegate.clear();
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean hasCustomName() {
            return delegate.hasCustomName();
        }

        @Override
        public ITextComponent getDisplayName() {
            return delegate.getDisplayName();
        }
    }
}
