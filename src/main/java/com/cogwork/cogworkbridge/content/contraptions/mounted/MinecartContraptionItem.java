package com.cogwork.cogworkbridge.content.contraptions.mounted;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraption;
import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity;
import com.cogwork.cogworkbridge.registry.AllItems;
import com.cogwork.cogworkbridge.registry.ModCreativeTab;
import net.minecraft.block.BlockRailBase;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityMinecart;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

/** A portable minecart and the live contraption attached to it. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class MinecartContraptionItem extends Item {
    private static final String DATA = "MountedContraption";
    private static final int MAX_ITEM_NBT_BYTES = 1_000_000;
    private final EntityMinecart.Type type;

    public MinecartContraptionItem(EntityMinecart.Type type) {
        this.type = type;
        setMaxStackSize(1);
        setCreativeTab(ModCreativeTab.INSTANCE);
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        handleEntityInteraction(event, event.getTarget());
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        handleEntityInteraction(event, event.getTarget());
    }

    private static void handleEntityInteraction(PlayerInteractEvent event, Entity target) {
        EntityPlayer player = event.getEntityPlayer();
        if (!(target instanceof EntityMinecart cart) || cart.isDead
                || player.getHeldItem(event.getHand()).getItem() != AllItems.WRENCH) return;
        BearingContraptionEntity mounted = findMounted(cart);
        if (mounted == null) return;
        if (!player.world.isRemote) pickupFromStructure(player, mounted);
        event.setCancellationResult(EnumActionResult.SUCCESS);
        event.setCanceled(true);
    }

    @Nullable
    private static BearingContraptionEntity findMounted(EntityMinecart cart) {
        for (Entity entity : cart.world.loadedEntityList) {
            if (entity instanceof BearingContraptionEntity mounted && !mounted.isDead
                    && mounted.isMountedTo(cart.getUniqueID())) return mounted;
        }
        return null;
    }

    /** Called after the server validates the hit on an individual carried block. */
    public static boolean pickupFromStructure(EntityPlayer player, BearingContraptionEntity hit) {
        BearingContraptionEntity mounted = hit;
        while (mounted.getParentContraption() != null) mounted = mounted.getParentContraption();
        EntityMinecart primary = mounted.getMountedCart();
        if (primary == null) return false;
        if (player.world.isRemote) return true;
        EntityMinecart other = mounted.getOtherMountedCart(primary.getUniqueID());
        Item item = itemFor(primary.getType());
        if (item == null || !player.capabilities.allowEdit || !primary.getPassengers().isEmpty()
                || mounted.hasCoupledCart() && (other == null || itemFor(other.getType()) == null
                || !other.getPassengers().isEmpty()) || !mounted.preparePortablePickup()) {
            player.sendStatusMessage(new TextComponentTranslation("cogworkbridge.minecart_pickup_unavailable"), true);
            return true;
        }

        NBTTagCompound data = new NBTTagCompound();
        // The snapshot writer shares nested compound objects with the live structure.
        NBTTagCompound structure = mounted.getContraption().writeToNBT(new NBTTagCompound()).copy();
        detachSavedChildren(structure);
        data.setTag("Structure", structure);
        data.setTag("Cart", primary.writeToNBT(new NBTTagCompound()));
        data.setFloat("Angle", mounted.getAngle());
        data.setInteger("RotationMode", mounted.getMountedRotationMode());
        if (other != null) {
            data.setTag("OtherCart", other.writeToNBT(new NBTTagCompound()));
            data.setInteger("OtherType", other.getType().getId());
            data.setDouble("OtherX", other.posX - primary.posX);
            data.setDouble("OtherY", other.posY - primary.posY);
            data.setDouble("OtherZ", other.posZ - primary.posZ);
        }
        ItemStack result = new ItemStack(item);
        NBTTagCompound root = new NBTTagCompound();
        root.setTag(DATA, data);
        result.setTagCompound(root);
        if (primary.hasCustomName()) result.setStackDisplayName(primary.getCustomNameTag());
        if (!fitsPacket(result)) {
            player.sendStatusMessage(new TextComponentTranslation("cogworkbridge.minecart_pickup_too_big"), true);
            return true;
        }

        mounted.discardWithoutRestore();
        removeCartWithoutDrops(primary);
        if (other != null) removeCartWithoutDrops(other);
        ItemHandlerHelper.giveItemToPlayer(player, result);
        return true;
    }

    private static boolean fitsPacket(ItemStack stack) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            CompressedStreamTools.write(stack.serializeNBT(), new DataOutputStream(bytes));
            return bytes.size() <= MAX_ITEM_NBT_BYTES;
        } catch (IOException | RuntimeException ex) {
            return false;
        }
    }

    /** New child entities must not wait for UUIDs belonging to discarded children. */
    private static void detachSavedChildren(NBTTagCompound structure) {
        NBTTagList blocks = structure.getTagList("Blocks", 10);
        for (int i = 0; i < blocks.tagCount(); i++) {
            NBTTagCompound tile = blocks.getCompoundTagAt(i).getCompoundTag("Tile");
            if (!tile.hasKey("SavedContraption", 10)) continue;
            tile.removeTag("ContraptionEntityUuidMost");
            tile.removeTag("ContraptionEntityUuidLeast");
            tile.setInteger("ContraptionEntity", -1);
            detachSavedChildren(tile.getCompoundTag("SavedContraption"));
        }
    }

    @Nullable
    private static Item itemFor(EntityMinecart.Type type) {
        if (type == EntityMinecart.Type.RIDEABLE) return AllItems.MINECART_CONTRAPTION;
        if (type == EntityMinecart.Type.CHEST) return AllItems.CHEST_MINECART_CONTRAPTION;
        if (type == EntityMinecart.Type.FURNACE) return AllItems.FURNACE_MINECART_CONTRAPTION;
        return null;
    }

    @Nullable
    private static EntityMinecart.Type typeForId(int id) {
        if (id == EntityMinecart.Type.RIDEABLE.getId()) return EntityMinecart.Type.RIDEABLE;
        if (id == EntityMinecart.Type.CHEST.getId()) return EntityMinecart.Type.CHEST;
        if (id == EntityMinecart.Type.FURNACE.getId()) return EntityMinecart.Type.FURNACE;
        return null;
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
                                      EnumFacing side, float hitX, float hitY, float hitZ) {
        ItemStack stack = player.getHeldItem(hand);
        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockRailBase rail)
                || !player.canPlayerEdit(pos, side, stack) || !world.isBlockModifiable(player, pos)
                || !stack.hasTagCompound() || !stack.getTagCompound().hasKey(DATA, 10)) return EnumActionResult.FAIL;
        NBTTagCompound data = stack.getTagCompound().getCompoundTag(DATA);
        if (!data.hasKey("Structure", 10) || !data.hasKey("Cart", 10)) return EnumActionResult.FAIL;
        BlockRailBase.EnumRailDirection direction = rail.getRailDirection(world, pos, state, null);
        if (world.isRemote) return EnumActionResult.SUCCESS;
        BlockPos anchor = pos.up();
        if (BearingContraptionEntity.hasActiveAt(anchor.up())) return EnumActionResult.FAIL;
        BearingContraption structure = BearingContraption.readFromNBT(data.getCompoundTag("Structure")).translatedTo(anchor);
        if (structure.isEmpty()) return EnumActionResult.FAIL;
        double x = pos.getX() + .5D, y = pos.getY() + .0625D + (direction.isAscending() ? .5D : 0D), z = pos.getZ() + .5D;
        EntityMinecart cart = restoreCart(world, type, data.getCompoundTag("Cart"), x, y, z);
        if (stack.hasDisplayName()) cart.setCustomNameTag(stack.getDisplayName());
        EntityMinecart other = null;
        if (data.hasKey("OtherCart", 10)) {
            EntityMinecart.Type otherType = typeForId(data.getInteger("OtherType"));
            double ox = x + data.getDouble("OtherX"), oy = y + data.getDouble("OtherY"), oz = z + data.getDouble("OtherZ");
            BlockPos otherRail = new BlockPos(ox, oy, oz);
            if (otherType == null || !world.isBlockLoaded(otherRail)
                    || !BlockRailBase.isRailBlock(world, otherRail)) return EnumActionResult.FAIL;
            other = restoreCart(world, otherType, data.getCompoundTag("OtherCart"), ox, oy, oz);
        }
        if (!world.spawnEntity(cart)) return EnumActionResult.FAIL;
        if (other != null && !world.spawnEntity(other)) {
            removeCartWithoutDrops(cart);
            return EnumActionResult.FAIL;
        }
        boolean eastWest = direction == BlockRailBase.EnumRailDirection.EAST_WEST
                || direction == BlockRailBase.EnumRailDirection.ASCENDING_EAST
                || direction == BlockRailBase.EnumRailDirection.ASCENDING_WEST;
        BearingContraptionEntity mounted = BearingContraptionEntity.createMounted(world, anchor, structure,
                cart, other, data.getInteger("RotationMode"), eastWest);
        mounted.setPortableMountedAngle(data.getFloat("Angle"));
        if (!world.spawnEntity(mounted)) {
            mounted.discardWithoutRestore();
            removeCartWithoutDrops(cart);
            if (other != null) removeCartWithoutDrops(other);
            return EnumActionResult.FAIL;
        }
        mounted.prepareNestedChildren();
        mounted.sendSnapshotToTrackingPlayers();
        mounted.sendNestedSnapshots();
        if (!player.capabilities.isCreativeMode) stack.shrink(1);
        return EnumActionResult.SUCCESS;
    }

    private static EntityMinecart restoreCart(World world, EntityMinecart.Type type, NBTTagCompound saved,
                                               double x, double y, double z) {
        EntityMinecart cart = EntityMinecart.create(world, x, y, z, type);
        cart.readFromNBT(saved.copy());
        cart.setUniqueId(UUID.randomUUID());
        cart.dimension = world.provider.getDimension();
        cart.setPosition(x, y, z);
        cart.prevPosX = cart.lastTickPosX = x;
        cart.prevPosY = cart.lastTickPosY = y;
        cart.prevPosZ = cart.lastTickPosZ = z;
        cart.motionX = cart.motionY = cart.motionZ = 0D;
        return cart;
    }

    private static void removeCartWithoutDrops(EntityMinecart cart) {
        cart.setDropItemsWhenDead(false);
        cart.setDead();
    }
}
