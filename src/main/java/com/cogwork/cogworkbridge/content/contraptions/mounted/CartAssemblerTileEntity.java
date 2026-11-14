package com.cogwork.cogworkbridge.content.contraptions.mounted;

import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraption;
import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity;
import com.cogwork.cogworkbridge.content.contraptions.bearing.CarriedBearingAssembly;
import com.cogwork.cogworkbridge.content.contraptions.bearing.ContraptionGuiProxy;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityMinecart;
import net.minecraft.entity.item.EntityMinecartChest;
import net.minecraft.entity.item.EntityMinecartFurnace;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import com.cogwork.cogworkbridge.core.CogworkTileEntity;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CartAssemblerTileEntity extends CogworkTileEntity implements net.minecraft.util.ITickable {
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private int railType = 1;
    private int movementMode;
    private boolean powered;
    private int signalLevel;
    private boolean backwards;
    private EntityMinecart pendingCart;
    private static final net.minecraft.block.Block[] RAILS = {Blocks.RAIL, Blocks.GOLDEN_RAIL, Blocks.DETECTOR_RAIL, Blocks.ACTIVATOR_RAIL,
            com.cogwork.cogworkbridge.registry.AllBlocks.CONTROLLER_RAIL};
    public static int railTypeFor(net.minecraft.block.Block block) {
        for (int i = 0; i < RAILS.length; i++) if (RAILS[i] == block) return i;
        return -1;
    }
    public int getRailType() { return railType; }
    public ItemStack getRailDrop() { return new ItemStack(RAILS[railType]); }
    public boolean isPowered() { return powered; }
    public void setBackwards(boolean value) { backwards = value; markDirty(); }
    public IBlockState getRailState(IBlockState assembler) {
        net.minecraft.block.BlockRailBase rail = (net.minecraft.block.BlockRailBase) RAILS[railType];
        IBlockState state = rail.getDefaultState().withProperty(rail.getShapeProperty(), assembler.getValue(CartAssemblerBlock.SHAPE));
        return rail instanceof ControllerRailBlock ? state.withProperty(ControllerRailBlock.BACKWARDS, backwards) : state;
    }
    public int getMovementMode() { return movementMode; }
    public void setMovementMode(int mode) {
        movementMode = Math.floorMod(mode, 3);
        markDirty();
        if (world != null && !world.isRemote) world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
    }
    public void setRailType(int type) {
        int oldType = railType;
        boolean oldPowered = powered;
        int oldSignal = signalLevel;
        railType = Math.max(0, Math.min(RAILS.length - 1, type));
        if (world != null && !world.isRemote) {
            signalLevel = effectiveSignal();
            powered = signalLevel > 0;
        }
        markDirty();
        if (world != null && !world.isRemote) {
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 2);
            if (oldType != railType || oldPowered != powered || oldSignal != signalLevel) notifyRailNeighbors();
        }
    }

    private boolean effectivePower() {
        return effectiveSignal() > 0;
    }

    private int effectiveSignal() {
        return CartAssemblerRailPower.signalStrength(world, pos, world.getBlockState(pos));
    }

    private void notifyRailNeighbors() {
        net.minecraft.block.Block block = world.getBlockState(pos).getBlock();
        world.notifyNeighborsOfStateChange(pos, block, false);
        world.notifyNeighborsOfStateChange(pos.down(), block, false);
    }
    public boolean changeRail(EntityPlayer player, ItemStack held) {
        if (held.isEmpty()) return false;
        for (int i = 0; i < RAILS.length; i++) {
            if (held.getItem() != Item.getItemFromBlock(RAILS[i]) || railType == i) continue;
            if (!world.isRemote) {
                ItemStack old = new ItemStack(RAILS[railType]);
                setRailType(i);
                if (!player.capabilities.isCreativeMode) {
                    held.shrink(1);
                    net.minecraftforge.items.ItemHandlerHelper.giveItemToPlayer(player, old);
                }
            }
            return true;
        }
        return false;
    }
    private boolean shouldDisassemble() {
        boolean signal = effectivePower();
        return railType == 2 || (railType == 3 ? signal : !signal);
    }
    private boolean otherCartReady(BearingContraptionEntity entity, EntityMinecart cart) {
        if (!entity.hasCoupledCart()) return true;
        EntityMinecart other = entity.getOtherMountedCart(cart.getUniqueID());
        if (other == null) return false;
        BlockPos rail = new BlockPos(other.posX, other.posY, other.posZ);
        TileEntity tile = world.getTileEntity(rail);
        if (!(tile instanceof CartAssemblerTileEntity)) tile = world.getTileEntity(rail.down());
        return tile instanceof CartAssemblerTileEntity assembler && assembler.shouldDisassemble();
    }
    private boolean availableCart(EntityMinecart cart) {
        if (cart.isDead || !cart.getPassengers().isEmpty()
                || !cart.canBeRidden() && !(cart instanceof EntityMinecartChest) && !(cart instanceof EntityMinecartFurnace)) return false;
        for (net.minecraft.entity.Entity raw : world.loadedEntityList)
            if (raw instanceof BearingContraptionEntity entity && !entity.isDead && entity.isMountedTo(cart.getUniqueID())) return false;
        return true;
    }
    public void accept(EntityMinecart cart) {
        if (cart != null && !cart.isDead && world != null && !world.isRemote) pendingCart = cart;
    }
    private void processCart(EntityMinecart cart) {
        if (cart == null || cart.isDead || world == null || world.isRemote) return;
        if (!cart.canBeRidden() && !(cart instanceof EntityMinecartChest) && !(cart instanceof EntityMinecartFurnace)) return;
        long now = world.getTotalWorldTime();
        if (now - cooldowns.getOrDefault(cart.getUniqueID(), Long.MIN_VALUE / 2) < 8) return;
        cooldowns.put(cart.getUniqueID(), now);
        BearingContraptionEntity mounted = null;
        for (net.minecraft.entity.Entity raw : world.loadedEntityList)
            if (raw instanceof BearingContraptionEntity entity && !entity.isDead && entity.isMountedTo(cart.getUniqueID())) { mounted = entity; break; }
        boolean powered = effectivePower();
        boolean assemble = railType == 2 ? mounted == null : railType != 3 && powered;
        boolean disassemble = railType == 2 ? mounted != null : railType == 3 ? powered : !powered;
        boolean accelerate = railType == 1 && powered || railType == 2 && mounted == null && cart.getPassengers().isEmpty();
        if (railType == 1 && !powered) {
            if (cart instanceof EntityMinecartFurnace furnace) furnace.pushX = furnace.pushZ = 0D;
            cart.motionX = (pos.getX() + .5D - cart.posX) / 16D;
            cart.motionY = 0D;
            cart.motionZ = (pos.getZ() + .5D - cart.posZ) / 16D;
        } else if (accelerate) {
            double magnitude = Math.sqrt(cart.motionX * cart.motionX + cart.motionZ * cart.motionZ);
            if (magnitude > 1D / 128D) {
                float speed = ((CartAssemblerBlock) world.getBlockState(pos).getBlock()).getRailMaxSpeed(world, cart, pos);
                cart.motionX = cart.motionX / magnitude * speed;
                cart.motionZ = cart.motionZ / magnitude * speed;
            }
        }
        if (mounted != null) {
            if (disassemble && otherCartReady(mounted, cart)) {
                EntityMinecart other = mounted.getOtherMountedCart(cart.getUniqueID());
                mounted.restoreAndDiscard();
                if (mounted.isDead && cart instanceof EntityMinecartFurnace furnace) {
                    furnace.pushX = cart.motionX;
                    furnace.pushZ = cart.motionZ;
                }
                if (mounted.isDead && other instanceof EntityMinecartFurnace furnace) {
                    furnace.pushX = other.motionX;
                    furnace.pushZ = other.motionZ;
                }
            }
            return;
        }
        if (!assemble || !cart.getPassengers().isEmpty()) return;
        BearingContraption structure = BearingContraption.captureMounted(world, pos, pos.up(), positions -> { });
        if (structure == null || structure.isEmpty()) return;
        BlockPos otherRail = null;
        EntityMinecart otherCart = null;
        for (BearingContraption.Snapshot snapshot : structure.blocks().values()) {
            if (!(snapshot.state().getBlock() instanceof CartAssemblerBlock)) continue;
            BlockPos other = snapshot.pos();
            if (otherRail != null || other.getY() != pos.getY()
                    || other.getX() != pos.getX() && other.getZ() != pos.getZ()
                    || other.distanceSq(pos) > 32D * 32D) return;
            for (EntityMinecart candidate : world.getEntitiesWithinAABB(EntityMinecart.class,
                    new net.minecraft.util.math.AxisAlignedBB(other).grow(.1D, .5D, .1D))) {
                if (candidate != cart && availableCart(candidate)) { otherCart = candidate; break; }
            }
            if (otherCart == null) return;
            otherRail = other;
        }
        if (otherCart != null && (!(world instanceof net.minecraft.world.WorldServer serverWorld)
                || !MinecartCouplingData.get(world).canAddStructure(serverWorld, cart, otherCart))) return;
        ContraptionGuiProxy.closeCapturedBlock(world.getMinecraftServer(), structure.blocks().keySet(), world.provider.getDimension());
        boolean eastWest = world.getBlockState(pos).getValue(CartAssemblerBlock.SHAPE)
                == net.minecraft.block.BlockRailBase.EnumRailDirection.EAST_WEST;
        structure.replaceAtLocal(pos.subtract(pos.up()),
                com.cogwork.cogworkbridge.registry.AllBlocks.MINECART_ANCHOR.getDefaultState().withProperty(
                        MinecartAnchorBlock.AXIS, eastWest ? net.minecraft.util.EnumFacing.Axis.X : net.minecraft.util.EnumFacing.Axis.Z), null);
        if (otherRail != null) {
            boolean otherEastWest = world.getBlockState(otherRail).getValue(CartAssemblerBlock.SHAPE)
                    == net.minecraft.block.BlockRailBase.EnumRailDirection.EAST_WEST;
            structure.replaceAtLocal(otherRail.subtract(pos.up()),
                    com.cogwork.cogworkbridge.registry.AllBlocks.MINECART_ANCHOR.getDefaultState().withProperty(
                            MinecartAnchorBlock.AXIS, otherEastWest ? net.minecraft.util.EnumFacing.Axis.X : net.minecraft.util.EnumFacing.Axis.Z), null);
        }
        CarriedBearingAssembly carried = CarriedBearingAssembly.prepare(world, structure, positions ->
                ContraptionGuiProxy.closeCapturedBlock(world.getMinecraftServer(), positions, world.provider.getDimension()));
        double oldX = cart.posX, oldY = cart.posY, oldZ = cart.posZ;
        double otherX = otherCart == null ? 0D : otherCart.posX;
        double otherY = otherCart == null ? 0D : otherCart.posY;
        double otherZ = otherCart == null ? 0D : otherCart.posZ;
        if (otherCart != null) {
            cart.setPosition(pos.getX() + .5D, pos.getY(), pos.getZ() + .5D);
            otherCart.setPosition(otherRail.getX() + .5D, otherRail.getY(), otherRail.getZ() + .5D);
        }
        BearingContraptionEntity entity = BearingContraptionEntity.createMounted(world, pos.up(), structure, cart,
                otherCart, movementMode, eastWest);
        entity.prepareNestedChildren();
        if (!world.spawnEntity(entity)) {
            cart.setPosition(oldX, oldY, oldZ);
            if (otherCart != null) otherCart.setPosition(otherX, otherY, otherZ);
            return;
        }
        if (cart instanceof EntityMinecartFurnace furnace) furnace.pushX = furnace.pushZ = 0D;
        com.cogwork.cogworkbridge.content.decoration.SeatBlock.transferOccupants(entity, structure);
            com.cogwork.cogworkbridge.content.advancement.AllAdvancements.assembled(world, pos, structure);
        if (otherCart instanceof EntityMinecartFurnace furnace) furnace.pushX = furnace.pushZ = 0D;
        java.util.Set<BlockPos> protectedRails = new java.util.HashSet<>();
        protectedRails.add(pos);
        if (otherRail != null) protectedRails.add(otherRail);
        carried.removeFromWorld(); structure.removeFromWorld(world, true, protectedRails);
        entity.sendSnapshotToTrackingPlayers(); entity.sendNestedSnapshots();
    }
    @Override public void update() {
        if (world == null || world.isRemote) return;
        int next = effectiveSignal();
        if (next != signalLevel || (next > 0) != powered) {
            signalLevel = next;
            powered = next > 0;
            markDirty();
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 2);
            notifyRailNeighbors();
        }
        EntityMinecart cart = pendingCart;
        pendingCart = null;
        if (cart != null) processCart(cart);
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) { super.writeToNBT(tag); tag.setInteger("RailType", railType); tag.setBoolean("RailPowered", powered); tag.setInteger("SignalLevel", signalLevel); tag.setBoolean("Backwards", backwards); tag.setInteger("MovementMode", movementMode); return tag; }
    @Override public void readFromNBT(NBTTagCompound tag) { super.readFromNBT(tag); railType = tag.hasKey("RailType") ? Math.max(0, Math.min(RAILS.length - 1, tag.getInteger("RailType"))) : 1; powered = tag.getBoolean("RailPowered"); signalLevel = tag.hasKey("SignalLevel") ? Math.max(0, Math.min(15, tag.getInteger("SignalLevel"))) : powered ? 15 : 0; backwards = tag.getBoolean("Backwards"); movementMode = Math.floorMod(tag.getInteger("MovementMode"), 3); }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public net.minecraft.network.play.server.SPacketUpdateTileEntity getUpdatePacket() {
        return new net.minecraft.network.play.server.SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }
    @Override public void onDataPacket(net.minecraft.network.NetworkManager network, net.minecraft.network.play.server.SPacketUpdateTileEntity packet) {
        int previous = railType;
        boolean wasPowered = powered;
        readFromNBT(packet.getNbtCompound());
        if (world != null && world.isRemote && (railType != previous || powered != wasPowered)) world.markBlockRangeForRenderUpdate(pos, pos);
    }
}
