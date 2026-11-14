package com.cogwork.cogworkbridge.content.kinetics.deployer;

import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity;
import com.cogwork.cogworkbridge.content.contraptions.bearing.ContraptionItemCollector;
import com.cogwork.cogworkbridge.kinetic.KineticTileEntity;
import com.cogwork.cogworkbridge.content.logistics.transport.TransportedItemHolder;
import com.cogwork.cogworkbridge.content.logistics.transport.TransportedItemProcessor;
import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld;
import com.mojang.authlib.GameProfile;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Powered item actor with a server-side FakePlayer for normal Forge interactions. */
public final class DeployerTileEntity extends KineticTileEntity implements TransportedItemProcessor {
    public enum Mode { USE, PUNCH }
    private enum Motion { WAITING, EXTENDING, RETRACTING }

    private static final int FULL_TRAVEL = 1000;
    private static final double ACTION_DISTANCE = 2.0D;

    private ItemStack heldItem = ItemStack.EMPTY;
    private ItemStack cloggedItem = ItemStack.EMPTY;
    private ItemStack filter = ItemStack.EMPTY;
    private Mode mode = Mode.USE;
    private Motion motion = Motion.WAITING;
    private int timer;
    private float renderExtension;
    private float previousRenderExtension;
    private boolean renderExtensionInitialized;
    private int waitTicks;
    private boolean redstoneLocked;
    private boolean transportMode;
    private ItemStack expectedTransport = ItemStack.EMPTY;
    private DeployerFakePlayer deployerPlayer;
    private WorldServer playerWorld;

    private final IItemHandler itemHandler = new IItemHandler() {
        @Override public int getSlots() { return 2; }
        @Override public ItemStack getStackInSlot(int slot) {
            return slot == 0 ? heldItem : slot == 1 ? cloggedItem : ItemStack.EMPTY;
        }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != 0 || stack.isEmpty() || !com.cogwork.cogworkbridge.content.fluids.ListFilterItem.matchesItem(filter, stack)) return stack;
            if (heldItem.isEmpty()) {
                int moved = Math.min(stack.getCount(),
                        Math.min(getSlotLimit(slot), stack.getMaxStackSize()));
                if (!simulate) {
                    heldItem = stack.copy();
                    heldItem.setCount(moved);
                    contentsChanged();
                }
                ItemStack remainder = stack.copy();
                remainder.shrink(moved);
                return remainder;
            }
            if (!ItemHandlerHelper.canItemStacksStack(heldItem, stack)) return stack;
            int moved = Math.min(stack.getCount(), Math.min(getSlotLimit(slot), heldItem.getMaxStackSize())
                    - heldItem.getCount());
            if (moved <= 0) return stack;
            if (!simulate) {
                heldItem.grow(moved);
                contentsChanged();
            }
            ItemStack remainder = stack.copy();
            remainder.shrink(moved);
            return remainder;
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            ItemStack source = slot == 0 ? heldItem : slot == 1 ? cloggedItem : ItemStack.EMPTY;
            if (amount <= 0 || source.isEmpty()) return ItemStack.EMPTY;
            ItemStack extracted = source.copy();
            extracted.setCount(Math.min(amount, extracted.getCount()));
            if (!simulate) {
                source.shrink(extracted.getCount());
                if (source.isEmpty()) {
                    if (slot == 0) heldItem = ItemStack.EMPTY;
                    else cloggedItem = ItemStack.EMPTY;
                }
                contentsChanged();
            }
            return extracted;
        }
        @Override public int getSlotLimit(int slot) { return 64; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return slot == 0 && com.cogwork.cogworkbridge.content.fluids.ListFilterItem.matchesItem(filter, stack); }
    };

    @Override public float getStressImpact() { return 4.0F; }

    public Mode getMode() { return mode; }
    public ItemStack getHeldItem() { return heldItem; }
    public ItemStack getCloggedItem() { return cloggedItem; }
    public ItemStack getFilter() { return filter; }

    public ItemStack swapHeldItem(ItemStack incoming) {
        ItemStack previous = heldItem.copy();
        heldItem = incoming.isEmpty() ? ItemStack.EMPTY : incoming.copy();
        contentsChanged();
        return previous;
    }

    public void setFilter(ItemStack stack) {
        filter = stack.copy();
        if (!filter.isEmpty()) filter.setCount(1);
        contentsChanged();
    }

    public ItemStack takeCloggedItem() {
        ItemStack result = cloggedItem.copy();
        cloggedItem = ItemStack.EMPTY;
        contentsChanged();
        return result;
    }

    private boolean acceptReturnedItem(ItemStack stack) {
        if (stack.isEmpty()) return true;
        if (cloggedItem.isEmpty()) {
            cloggedItem = stack.copy();
            contentsChanged();
            return true;
        }
        if (!ItemHandlerHelper.canItemStacksStack(cloggedItem, stack)) return false;
        int limit = Math.min(64, cloggedItem.getMaxStackSize());
        if (cloggedItem.getCount() + stack.getCount() > limit) return false;
        cloggedItem.grow(stack.getCount());
        contentsChanged();
        return true;
    }

    public float getExtension(float partialTicks) {
        if (world != null && world.isRemote && getTileData().hasKey("TutorialDeployerExtension")) {
            float current = getTileData().getFloat("TutorialDeployerExtension");
            float previous = getTileData().getFloat("TutorialDeployerPrevious");
            return previous + (current - previous) * MathHelper.clamp(partialTicks, 0F, 1F);
        }
        if (world != null && world.isRemote && renderExtensionInitialized) {
            return previousRenderExtension + (renderExtension - previousRenderExtension)
                    * MathHelper.clamp(partialTicks, 0.0F, 1.0F);
        }
        return extensionFor(motion, timer);
    }

    private static float extensionFor(Motion motion, int timer) {
        if (motion == Motion.WAITING) return 0.0F;
        float progress = MathHelper.clamp((float) timer / FULL_TRAVEL, 0.0F, 1.0F);
        return (motion == Motion.EXTENDING ? 1.0F - progress : progress) * (21.0F / 16.0F);
    }

    private void initializeRenderExtension() {
        renderExtension = previousRenderExtension = extensionFor(motion, timer);
        renderExtensionInitialized = true;
    }

    public void toggleMode() {
        if (world == null || world.isRemote) return;
        mode = mode == Mode.USE ? Mode.PUNCH : Mode.USE;
        sync();
    }

    public void updateRedstone() {
        if (world == null || world.isRemote) return;
        boolean powered = world.isBlockPowered(pos);
        if (powered == redstoneLocked) return;
        redstoneLocked = powered;
        sync();
    }

    @Override
    public void update() {
        super.update();
        if (world == null) return;
        if (!world.isRemote) updateRedstone();
        float speed = workingSpeed();
        if (world.isRemote) {
            if (!renderExtensionInitialized) initializeRenderExtension();
            previousRenderExtension = renderExtension;
            if (speed != 0.0F && motion != Motion.WAITING && timer > 0) {
                timer = Math.max(0, timer - timerSpeed());
            }
            float step = speed == 0.0F ? 0.0F : timerSpeed() * (21.0F / 16.0F) / FULL_TRAVEL;
            renderExtension += MathHelper.clamp(extensionFor(motion, timer) - renderExtension, -step, step);
            return;
        }
        if (speed == 0.0F) return;
        if (motion == Motion.WAITING) {
            if (redstoneLocked) return;
            if (!cloggedItem.isEmpty()) return;
            IBlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof DeployerBlock && state.getValue(DeployerBlock.FACING) == EnumFacing.DOWN) {
                TileEntity target = world.getTileEntity(pos.down(2));
                if (target instanceof TransportedItemHolder holder) {
                    if (holdTransportedItem(holder)) return;
                    if (mode == Mode.USE && !heldItem.isEmpty()) {
                        waitTicks = 0;
                        return;
                    }
                }
            }
            if (++waitTicks >= 10) {
                waitTicks = 0;
                motion = Motion.EXTENDING;
                timer = FULL_TRAVEL;
                sync();
            }
            return;
        }
        timer = Math.max(0, timer - timerSpeed());
        if (timer > 0) return;
        if (motion == Motion.EXTENDING) {
            motion = Motion.RETRACTING;
            timer = FULL_TRAVEL;
            world.playSound(null, pos, net.minecraft.init.SoundEvents.ENTITY_PLAYER_ATTACK_NODAMAGE,
                    net.minecraft.util.SoundCategory.BLOCKS, .5F, .75F);
            if (transportMode) applyTransported();
            else performInteraction();
        } else {
            motion = Motion.WAITING;
            timer = 0;
            transportMode = false;
            expectedTransport = ItemStack.EMPTY;
        }
        sync();
    }

    @Override
    public boolean holdTransportedItem(TransportedItemHolder holder) {
        if (world == null || world.isRemote || workingSpeed() == 0.0F || mode != Mode.USE
                || !cloggedItem.isEmpty()) return false;
        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof DeployerBlock)
                || state.getValue(DeployerBlock.FACING) != EnumFacing.DOWN) return false;
        ItemStack input = holder.getTransportedItem();
        if (input.isEmpty()) return false;
        if (motion != Motion.WAITING && !transportMode) return true;
        if (heldItem.isEmpty()) return !redstoneLocked || motion != Motion.WAITING;
        if (DeployerTransportRecipes.result(input, heldItem).isEmpty()) return false;
        if (transportMode) return ItemStack.areItemsEqual(expectedTransport, input)
                && ItemStack.areItemStackTagsEqual(expectedTransport, input);
        if (motion == Motion.RETRACTING) return true;
        if (motion == Motion.WAITING && redstoneLocked) return false;
        expectedTransport = input.copy();
        expectedTransport.setCount(1);
        transportMode = true;
        if (motion == Motion.WAITING) {
            waitTicks = 0;
            motion = Motion.EXTENDING;
            timer = FULL_TRAVEL;
        }
        markDirty();
        sync();
        return true;
    }

    private void applyTransported() {
        TileEntity target = world.getTileEntity(pos.down(2));
        if (!(target instanceof TransportedItemHolder holder)) return;
        ItemStack input = holder.getTransportedItem();
        if (input.isEmpty() || expectedTransport.isEmpty()
                || !ItemStack.areItemsEqual(expectedTransport, input)
                || !ItemStack.areItemStackTagsEqual(expectedTransport, input)) return;
        if (DeployerTransportRecipes.result(input, heldItem).isEmpty()) return;
        java.util.List<ItemStack> results = DeployerTransportRecipes.finish(input, heldItem, world.rand);
        boolean consumeHeld = DeployerTransportRecipes.consumesHeld(input, heldItem);
        boolean polishing = DeployerTransportRecipes.isPolishing(input, heldItem);
        holder.replaceOneWith(results);
        for (ItemStack output : results) {
            String casing = output.getItem().getRegistryName() == null ? "" : output.getItem().getRegistryName().getPath();
            String progress = switch (casing) { case "andesite_casing", "brass_casing", "copper_casing" -> casing; case "railway_casing" -> "train_casing_00"; default -> null; };
            if (progress != null) com.cogwork.cogworkbridge.content.advancement.AllAdvancements.awardAt(world, pos, progress);
        }
        if (consumeHeld) useHeldItem(); else if (polishing) damageSandPaper();
        if (heldItem.isEmpty()) heldItem = ItemStack.EMPTY;
        contentsChanged();
    }

    private float workingSpeed() {
        float speed = getSpeed();
        if (world instanceof FakeWorld fake && fake.getMobileChunk() != null
                && fake.getMobileChunk().getEntityMovingWorld() instanceof BearingContraptionEntity contraption
                && contraption.isActorDisabled(com.cogwork.cogworkbridge.registry.AllBlocks.DEPLOYER)) return 0F;
        if (speed == 0.0F && world instanceof FakeWorld fake
                && fake.getMobileChunk() != null
                && fake.getMobileChunk().getEntityMovingWorld() instanceof BearingContraptionEntity contraption) {
            speed = contraption.getSpeed();
        }
        return speed;
    }

    private int timerSpeed() {
        return MathHelper.clamp((int) (Math.abs(workingSpeed()) * 2.0F), 8, 512);
    }

    private void performInteraction() {
        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof DeployerBlock)) return;
        EnumFacing facing = state.getValue(DeployerBlock.FACING);
        Vec3d origin = new Vec3d(pos).add(0.5D, 0.5D, 0.5D);
        if (world instanceof FakeWorld fakeWorld
                && fakeWorld.getMobileChunk() != null
                && fakeWorld.getMobileChunk().getEntityMovingWorld()
                instanceof BearingContraptionEntity contraption) {
            BlockPos anchor = contraption.getAnchor();
            Vec3d localOrigin = new Vec3d(anchor.getX() + pos.getX() + 0.5D,
                    anchor.getY() + pos.getY() + 0.5D,
                    anchor.getZ() + pos.getZ() + 0.5D);
            Vec3d worldOrigin = contraption.localToWorld(localOrigin);
            Vec3d localForward = localOrigin.add(new Vec3d(facing.getDirectionVec()));
            Vec3d worldForward = contraption.localToWorld(localForward).subtract(worldOrigin);
            EnumFacing worldFacing = EnumFacing.getFacingFromVector(
                    (float) worldForward.x, (float) worldForward.y, (float) worldForward.z);
            BlockPos target = new BlockPos(contraption.localToWorld(
                    localOrigin.add(new Vec3d(facing.getDirectionVec()).scale(ACTION_DISTANCE))));
            World realWorld = contraption.world;
            if (realWorld instanceof WorldServer && realWorld.isBlockLoaded(target)) {
                if (!cloggedItem.isEmpty()) return;
                if (heldItem.isEmpty()) {
                    heldItem = ContraptionItemCollector.extractItem(contraption, pos, filter);
                }
                interactAt(realWorld, target, worldFacing, worldOrigin);
                contraption.persistAndSyncInteractionBlock(pos);
            }
            return;
        }
        if (mode == Mode.PUNCH) for (int distance = 2; distance <= 4; distance++) {
            BlockPos partner = pos.offset(facing, distance);
            if (!world.isBlockLoaded(partner)) break;
            if (world.getTileEntity(partner) instanceof DeployerTileEntity other) {
                if (other.mode == Mode.PUNCH && other.getSpeed() != 0F
                        && world.getBlockState(partner).getValue(DeployerBlock.FACING) == facing.getOpposite()) {
                    com.cogwork.cogworkbridge.content.advancement.AllAdvancements.awardAt(world, pos, "fist_bump");
                    com.cogwork.cogworkbridge.content.advancement.AllAdvancements.awardAt(world, partner, "fist_bump");
                }
                break;
            }
        }
        interactAt(world, pos.offset(facing, 2), facing, origin);
    }

    private void interactAt(World targetWorld, BlockPos target, EnumFacing facing, Vec3d origin) {
        if (!(targetWorld instanceof WorldServer serverWorld)) return;
        if (mode == Mode.USE && facing == EnumFacing.DOWN
                && targetWorld.getTileEntity(target) instanceof TransportedItemHolder) return;
        if (mode == Mode.USE && heldItem.getItem() instanceof com.cogwork.cogworkbridge.content.equipment.SandPaperItem) {
            for (net.minecraft.entity.item.EntityItem item : targetWorld.getEntitiesWithinAABB(
                    net.minecraft.entity.item.EntityItem.class, new AxisAlignedBB(target).grow(.25D))) {
                if (item.isDead) continue;
                ItemStack result = com.cogwork.cogworkbridge.content.equipment.SandPaperItem.polish(item.getItem());
                if (result.isEmpty()) continue;
                net.minecraft.entity.item.EntityItem output = new net.minecraft.entity.item.EntityItem(targetWorld,
                        item.posX, item.posY, item.posZ, result);
                output.setDefaultPickupDelay();
                if (!targetWorld.spawnEntity(output)) return;
                ItemStack remaining = item.getItem().copy();
                remaining.shrink(1);
                if (remaining.isEmpty()) item.setDead(); else item.setItem(remaining);
                damageSandPaper();
                contentsChanged();
                return;
            }
        }
        DeployerFakePlayer fake = getPlayer(serverWorld);
        fake.setPosition(origin.x, origin.y, origin.z);
        fake.setFacing(facing);
        fake.setHeldItem(EnumHand.MAIN_HAND, heldItem.copy());
        if (mode == Mode.PUNCH) {
            AxisAlignedBB bounds = new AxisAlignedBB(target).grow(0.1D);
            EntityLivingBase victim = targetWorld.getEntitiesWithinAABB(EntityLivingBase.class, bounds,
                    entity -> entity.isEntityAlive() && entity != fake
                            && (!(entity instanceof net.minecraft.entity.player.EntityPlayer player) || !player.isSpectator()))
                    .stream().findFirst().orElse(null);
            if (victim != null) {
                // Fake players do not tick their equipment, so Minecraft never installs the
                // held weapon's attack modifiers on their attribute map automatically.
                var modifiers = fake.getHeldItemMainhand().getAttributeModifiers(net.minecraft.inventory.EntityEquipmentSlot.MAINHAND);
                fake.getAttributeMap().applyAttributeModifiers(modifiers);
                try {
                    fake.attackTargetEntityWithCurrentItem(victim);
                } finally {
                    fake.getAttributeMap().removeAttributeModifiers(modifiers);
                }
            }
        } else {
            EnumFacing hitFace = facing.getOpposite();
            float hitX = 0.5F + hitFace.getXOffset() * 0.5F;
            float hitY = 0.5F + hitFace.getYOffset() * 0.5F;
            float hitZ = 0.5F + hitFace.getZOffset() * 0.5F;
            PlayerInteractionManager manager = fake.interactionManager;
            boolean placingRail = heldItem.getItem() instanceof net.minecraft.item.ItemBlock itemBlock && itemBlock.getBlock() instanceof net.minecraft.block.BlockRailBase;
            EnumActionResult interactionResult = manager.processRightClickBlock(fake, targetWorld,
                    fake.getHeldItemMainhand(), EnumHand.MAIN_HAND, target, hitFace,
                    hitX, hitY, hitZ);
            if (interactionResult == EnumActionResult.SUCCESS && placingRail
                    && world instanceof FakeWorld moving && moving.getMobileChunk().getEntityMovingWorld() instanceof BearingContraptionEntity structure && structure.getMountedCart() != null)
                com.cogwork.cogworkbridge.content.advancement.AllAdvancements.awardAt(world, pos, "self_deploying");
        }
        heldItem = fake.getHeldItemMainhand().copy();
        if (heldItem.isEmpty()) heldItem = ItemStack.EMPTY;
        markDirty();
        sync();
    }

    private void damageSandPaper() {
        if (heldItem.getItem() instanceof com.cogwork.cogworkbridge.content.equipment.SandPaperItem
                && heldItem.attemptDamageItem(1, world.rand, null)) heldItem.shrink(1);
    }

    private void useHeldItem() {
        if (heldItem.isEmpty()) return;
        if (heldItem.isItemStackDamageable()) {
            if (heldItem.attemptDamageItem(1, world.rand, null)) heldItem.shrink(1);
            return;
        }
        ItemStack remainder = heldItem.getItem().getContainerItem(heldItem);
        heldItem.shrink(1);
        if (remainder.isEmpty()) return;
        if (heldItem.isEmpty()) heldItem = remainder;
        else if (!acceptReturnedItem(remainder))
            com.cogwork.cogworkbridge.content.logistics.transport.ItemTransfer.drop(world, pos, remainder);
    }

    private DeployerFakePlayer getPlayer(WorldServer targetWorld) {
        if (deployerPlayer == null || playerWorld != targetWorld) {
            String name = "CogworkBridgeDeployer-" + pos.toLong();
            UUID id = UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
            deployerPlayer = new DeployerFakePlayer(targetWorld,
                    new GameProfile(id, "CogworkBridgeDeployer"), this);
            deployerPlayer.capabilities.isCreativeMode = false;
            playerWorld = targetWorld;
        }
        net.minecraft.entity.player.EntityPlayer owner = com.cogwork.cogworkbridge.content.advancement.AllAdvancements.owner(world, pos);
        if (owner != null) deployerPlayer.getEntityData().setUniqueId("CogworkDeployerOwner", owner.getUniqueID());
        else deployerPlayer.getEntityData().removeTag("CogworkDeployerOwner");
        return deployerPlayer;
    }

    @Override
    public void invalidate() {
        deployerPlayer = null;
        playerWorld = null;
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        deployerPlayer = null;
        playerWorld = null;
        super.onChunkUnload();
    }

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        return capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY
                || super.hasCapability(capability, facing);
    }

    @Nullable
    @Override
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(itemHandler);
        }
        return super.getCapability(capability, facing);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        heldItem = tag.hasKey("DeployerHeld", 10)
                ? new ItemStack(tag.getCompoundTag("DeployerHeld")) : ItemStack.EMPTY;
        cloggedItem = tag.hasKey("DeployerClogged", 10)
                ? new ItemStack(tag.getCompoundTag("DeployerClogged")) : ItemStack.EMPTY;
        filter = tag.hasKey("DeployerFilter", 10)
                ? new ItemStack(tag.getCompoundTag("DeployerFilter")) : ItemStack.EMPTY;
        mode = tag.getBoolean("DeployerPunch") ? Mode.PUNCH : Mode.USE;
        int motionIndex = tag.getInteger("DeployerMotion");
        motion = Motion.values()[MathHelper.clamp(motionIndex, 0, Motion.values().length - 1)];
        timer = MathHelper.clamp(tag.getInteger("DeployerTimer"), 0, FULL_TRAVEL);
        initializeRenderExtension();
        waitTicks = Math.max(0, tag.getInteger("DeployerWait"));
        redstoneLocked = tag.getBoolean("DeployerPowered");
        transportMode = tag.getBoolean("DeployerTransport");
        expectedTransport = tag.hasKey("DeployerTransportInput", 10)
                ? new ItemStack(tag.getCompoundTag("DeployerTransportInput")) : ItemStack.EMPTY;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        writeDeployerData(tag);
        return tag;
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        NBTTagCompound tag = super.getUpdateTag();
        writeDeployerData(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        super.handleUpdateTag(tag);
        readDeployerData(tag);
    }

    private void writeDeployerData(NBTTagCompound tag) {
        tag.removeTag("DeployerHeld");
        tag.removeTag("DeployerClogged");
        tag.removeTag("DeployerFilter");
        if (!heldItem.isEmpty()) tag.setTag("DeployerHeld", heldItem.writeToNBT(new NBTTagCompound()));
        if (!cloggedItem.isEmpty()) tag.setTag("DeployerClogged", cloggedItem.writeToNBT(new NBTTagCompound()));
        if (!filter.isEmpty()) tag.setTag("DeployerFilter", filter.writeToNBT(new NBTTagCompound()));
        tag.setBoolean("DeployerPunch", mode == Mode.PUNCH);
        tag.setInteger("DeployerMotion", motion.ordinal());
        tag.setInteger("DeployerTimer", timer);
        tag.setInteger("DeployerWait", waitTicks);
        tag.setBoolean("DeployerPowered", redstoneLocked);
        tag.setBoolean("DeployerTransport", transportMode);
        tag.removeTag("DeployerTransportInput");
        if (!expectedTransport.isEmpty())
            tag.setTag("DeployerTransportInput", expectedTransport.writeToNBT(new NBTTagCompound()));
    }

    private void readDeployerData(NBTTagCompound tag) {
        heldItem = tag.hasKey("DeployerHeld", 10)
                ? new ItemStack(tag.getCompoundTag("DeployerHeld")) : ItemStack.EMPTY;
        cloggedItem = tag.hasKey("DeployerClogged", 10)
                ? new ItemStack(tag.getCompoundTag("DeployerClogged")) : ItemStack.EMPTY;
        filter = tag.hasKey("DeployerFilter", 10)
                ? new ItemStack(tag.getCompoundTag("DeployerFilter")) : ItemStack.EMPTY;
        mode = tag.getBoolean("DeployerPunch") ? Mode.PUNCH : Mode.USE;
        int motionIndex = tag.getInteger("DeployerMotion");
        motion = Motion.values()[MathHelper.clamp(motionIndex, 0, Motion.values().length - 1)];
        timer = MathHelper.clamp(tag.getInteger("DeployerTimer"), 0, FULL_TRAVEL);
        if (!renderExtensionInitialized) initializeRenderExtension();
        waitTicks = Math.max(0, tag.getInteger("DeployerWait"));
        redstoneLocked = tag.getBoolean("DeployerPowered");
        transportMode = tag.getBoolean("DeployerTransport");
        expectedTransport = tag.hasKey("DeployerTransportInput", 10)
                ? new ItemStack(tag.getCompoundTag("DeployerTransportInput")) : ItemStack.EMPTY;
    }

    private void contentsChanged() {
        markDirty();
        if (world == null || world.isRemote) return;
        if (world instanceof FakeWorld fake
                && fake.getMobileChunk() != null
                && fake.getMobileChunk().getEntityMovingWorld()
                instanceof BearingContraptionEntity contraption) {
            contraption.persistAndSyncInteractionBlock(pos);
        } else {
            sync();
        }
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return new AxisAlignedBB(pos).grow(2.0D);
    }

    private static final class DeployerFakePlayer extends FakePlayer {
        private final DeployerTileEntity deployer;

        private DeployerFakePlayer(WorldServer world, GameProfile profile,
                                   DeployerTileEntity deployer) {
            super(world, profile);
            this.deployer = deployer;
        }

        @Override
        public boolean addItemStackToInventory(ItemStack stack) {
            if (deployer.acceptReturnedItem(stack)) return true;
            Block.spawnAsEntity(world, new BlockPos(this), stack.copy());
            return true;
        }

        private void setFacing(EnumFacing facing) {
            rotationYaw = switch (facing) {
                case SOUTH -> 0.0F;
                case WEST -> 90.0F;
                case NORTH -> 180.0F;
                case EAST -> 270.0F;
                default -> rotationYaw;
            };
            rotationPitch = facing == EnumFacing.UP ? -90.0F
                    : facing == EnumFacing.DOWN ? 90.0F : 0.0F;
        }

        @Override public Vec3d getPositionVector() { return new Vec3d(posX, posY, posZ); }
        // The fake player is not ticked between activations; each mechanical strike
        // therefore has its own full attack cooldown, like an actual player swing.
        @Override public float getCooledAttackStrength(float adjustTicks) { return 1F; }
        @Override public net.minecraft.util.text.ITextComponent getDisplayName() {
            return new net.minecraft.util.text.TextComponentTranslation("cogworkbridge.block.deployer.damage_source_name");
        }
        @Override public boolean isEntityInvulnerable(net.minecraft.util.DamageSource source) { return false; }
    }
}
