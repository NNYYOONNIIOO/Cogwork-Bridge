package com.cogwork.cogworkbridge.content.equipment;

import com.cogwork.cogworkbridge.registry.AllItems;
import com.cogwork.cogworkbridge.registry.ModCreativeTab;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/** Hand polishing reserves one input until use completes, and returns it if interrupted. */
public final class SandPaperItem extends Item {
    private static final String INPUT = "CogworkBridgePolishingInput";
    private static final Map<Item, Function<ItemStack, ItemStack>> RECIPES = new LinkedHashMap<>();
    private static final ThreadLocal<Boolean> FIRST_PERSON_USE_RENDER = ThreadLocal.withInitial(() -> false);

    public SandPaperItem() {
        setCreativeTab(ModCreativeTab.INSTANCE);
        setMaxStackSize(1);
        setMaxDamage(8);
    }

    @Override public EnumActionResult onItemUseFirst(EntityPlayer player, World world,
            net.minecraft.util.math.BlockPos pos, net.minecraft.util.EnumFacing side,
            float x, float y, float z, EnumHand hand) {
        var state = world.getBlockState(pos);
        var next = xy177.farmersfuturedelight.common.block.CopperWeathering.getPrevious(state);
        if (next == null) next = xy177.farmersfuturedelight.common.block.CopperWeathering.getUnwaxed(state);
        if (next == null || next.equals(state)) return EnumActionResult.PASS;
        ItemStack paper = player.getHeldItem(hand);
        if (!player.canPlayerEdit(pos, side, paper) || !world.isBlockModifiable(player, pos)) return EnumActionResult.FAIL;
        if (!world.isRemote && world.setBlockState(pos, next, 11)) {
            world.playSound(null, pos, com.cogwork.cogworkbridge.registry.AllSounds.SANDING_LONG,
                    SoundCategory.BLOCKS, 1F, 1F + (world.rand.nextFloat() * .5F - 1F) / 5F);
            xy177.farmersfuturedelight.common.block.CopperWeathering.spawnBlockParticles(world, pos, state);
            paper.damageItem(1, player);
            player.addStat(net.minecraft.stats.StatList.getObjectUseStats(this));
        }
        return EnumActionResult.SUCCESS;
    }

    @Override public EnumActionResult onItemUse(EntityPlayer player, World world,
            net.minecraft.util.math.BlockPos pos, EnumHand hand, net.minecraft.util.EnumFacing side,
            float x, float y, float z) { return onItemUseFirst(player, world, pos, side, x, y, z, hand); }

    /** Allows other code to add polishing recipes without changing the use action. */
    public static void registerRecipe(Item input, Function<ItemStack, ItemStack> result) {
        if (input != null && result != null) RECIPES.put(input, result);
    }

    public static ItemStack polish(ItemStack input) {
        if (input.isEmpty()) return ItemStack.EMPTY;
        var upstream = com.cogwork.cogworkbridge.content.processing.UpstreamProcessingRecipes.find("sandpaper_polishing", input);
        if (upstream != null && !upstream.outputs().isEmpty()) return upstream.outputs().get(0).stack().copy();
        if (input.getItem() == AllItems.ROSE_QUARTZ) return new ItemStack(AllItems.POLISHED_ROSE_QUARTZ);
        Function<ItemStack, ItemStack> recipe = RECIPES.get(input.getItem());
        if (recipe == null) return ItemStack.EMPTY;
        ItemStack result = recipe.apply(input.copy());
        return result == null ? ItemStack.EMPTY : result.copy();
    }

    @Override public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack paper = player.getHeldItem(hand);
        if (!pending(paper).isEmpty()) {
            player.setActiveHand(hand);
            return new ActionResult<>(EnumActionResult.SUCCESS, paper);
        }
        EnumHand otherHand = hand == EnumHand.MAIN_HAND ? EnumHand.OFF_HAND : EnumHand.MAIN_HAND;
        ItemStack other = player.getHeldItem(otherHand);
        EntityItem ground = polish(other).isEmpty() ? findGroundInput(world, player) : null;
        if (polish(other).isEmpty() && ground == null) return new ActionResult<>(EnumActionResult.PASS, paper);
        if (!world.isRemote) {
            ItemStack held = ground == null ? other : ground.getItem();
            ItemStack input = held.splitStack(1);
            store(paper, input);
            if (ground != null) {
                if (held.isEmpty()) ground.setDead(); else ground.setItem(held);
            }
        }
        player.setActiveHand(hand);
        return new ActionResult<>(EnumActionResult.SUCCESS, paper);
    }

    private EntityItem findGroundInput(World world, EntityPlayer player) {
        RayTraceResult hit = rayTrace(world, player, false);
        Vec3d point = hit == null ? player.getPositionEyes(1F).add(player.getLook(1F).scale(2D)) : hit.hitVec;
        for (EntityItem item : world.getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(point, point).grow(1D))) {
            if (!item.isDead && player.getDistanceSq(item) <= 9D && !polish(item.getItem()).isEmpty()) return item;
        }
        return null;
    }

    @Override public int getMaxItemUseDuration(ItemStack stack) { return 32; }
    /** Use the vanilla eating transform only while rendering the active hand.
     * Returning EAT during the gameplay tick would also produce eating sounds and paper particles. */
    public static void setFirstPersonUseRender(boolean rendering) {
        if (rendering) FIRST_PERSON_USE_RENDER.set(true); else FIRST_PERSON_USE_RENDER.remove();
    }

    @Override public EnumAction getItemUseAction(ItemStack stack) {
        return FIRST_PERSON_USE_RENDER.get() ? EnumAction.EAT : EnumAction.NONE;
    }

    @Override public void onUsingTick(ItemStack stack, EntityLivingBase user, int count) {
        ItemStack input = pending(stack);
        if (input.isEmpty()) return;
        if (user.world.isRemote) {
            // Match the upstream item-use particle position: below the eye, rather
            // than exactly on the camera's forward ray where it obscures the view.
            Vec3d motion = new Vec3d((user.getRNG().nextFloat() - .5D) * .1D,
                    user.getRNG().nextFloat() * .1D + .1D, 0D)
                    .rotatePitch(-user.rotationPitch * ((float) Math.PI / 180F))
                    .rotateYaw(-user.rotationYaw * ((float) Math.PI / 180F));
            Vec3d point = new Vec3d((user.getRNG().nextFloat() - .5D) * .3D,
                    -user.getRNG().nextFloat() * .6D - .3D, .6D)
                    .rotatePitch(-user.rotationPitch * ((float) Math.PI / 180F))
                    .rotateYaw(-user.rotationYaw * ((float) Math.PI / 180F))
                    .add(user.posX, user.posY + user.getEyeHeight(), user.posZ);
            user.world.spawnParticle(EnumParticleTypes.ITEM_CRACK, point.x, point.y, point.z,
                    motion.x, motion.y + .05D, motion.z,
                    Item.getIdFromItem(input.getItem()), input.getMetadata());
        }
        if (!user.world.isRemote && count % 7 == 0) {
            user.world.playSound(null, user.posX, user.posY, user.posZ, SoundEvents.BLOCK_SAND_BREAK,
                    SoundCategory.PLAYERS, .25F, .8F + user.getRNG().nextFloat() * .4F);
        }
    }

    @Override public ItemStack onItemUseFinish(ItemStack paper, World world, EntityLivingBase user) {
        if (world.isRemote || !(user instanceof EntityPlayer player)) return paper;
        ItemStack input = take(paper);
        if (input.isEmpty()) return paper;
        ItemStack result = polish(input);
        if (result.isEmpty()) result = input;
        else paper.damageItem(1, player);
        give(player, result);
        return paper;
    }

    @Override public void onPlayerStoppedUsing(ItemStack paper, World world, EntityLivingBase user, int timeLeft) {
        if (!world.isRemote && user instanceof EntityPlayer player) give(player, take(paper));
    }

    @Override public void onUpdate(ItemStack paper, World world, net.minecraft.entity.Entity owner, int slot, boolean selected) {
        if (world.isRemote || pending(paper).isEmpty() || !(owner instanceof EntityPlayer player)) return;
        if (!player.isHandActive() || player.getActiveItemStack() != paper) give(player, take(paper));
    }

    @Override public boolean onEntityItemUpdate(EntityItem entity) {
        if (!entity.world.isRemote) {
            ItemStack input = take(entity.getItem());
            if (!input.isEmpty()) {
                EntityItem returned = new EntityItem(entity.world, entity.posX, entity.posY, entity.posZ, input);
                returned.setDefaultPickupDelay();
                if (!entity.world.spawnEntity(returned)) store(entity.getItem(), input);
            }
        }
        return false;
    }

    public static java.util.List<ItemStack> registeredInputs() {
        java.util.List<ItemStack> inputs = new java.util.ArrayList<>();
        inputs.add(new ItemStack(AllItems.ROSE_QUARTZ));
        for (Item item : RECIPES.keySet()) inputs.add(new ItemStack(item));
        return inputs;
    }

    private static ItemStack pending(ItemStack paper) {
        NBTTagCompound tag = paper.getTagCompound();
        return tag != null && tag.hasKey(INPUT, 10) ? new ItemStack(tag.getCompoundTag(INPUT)) : ItemStack.EMPTY;
    }

    public static ItemStack polishingInput(ItemStack paper) { return pending(paper); }

    private static void store(ItemStack paper, ItemStack input) {
        NBTTagCompound tag = paper.hasTagCompound() ? paper.getTagCompound() : new NBTTagCompound();
        tag.setTag(INPUT, input.writeToNBT(new NBTTagCompound()));
        paper.setTagCompound(tag);
    }

    private static ItemStack take(ItemStack paper) {
        ItemStack input = pending(paper);
        NBTTagCompound tag = paper.getTagCompound();
        if (tag != null) {
            tag.removeTag(INPUT);
            if (tag.isEmpty()) paper.setTagCompound(null);
        }
        return input;
    }

    private static void give(EntityPlayer player, ItemStack item) {
        if (!item.isEmpty() && !player.inventory.addItemStackToInventory(item)) player.dropItem(item, false);
    }
}
