package com.cogwork.cogworkbridge.content.processing.press;

import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity;
import com.cogwork.cogworkbridge.content.processing.basin.BasinTileEntity;
import com.cogwork.cogworkbridge.content.logistics.transport.TransportedItemHolder;
import com.cogwork.cogworkbridge.content.logistics.transport.TransportedItemProcessor;
import com.cogwork.cogworkbridge.kinetic.KineticTileEntity;
import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.WorldServer;

public final class MechanicalPressTileEntity extends KineticTileEntity implements TransportedItemProcessor {
    private static final String UPSTREAM_PREFIX = "upstream:";
    private record BasinPlan(com.cogwork.cogworkbridge.content.processing.UpstreamProcessingRecipes.MixingRecipe recipe, int[] counts) { }
    private float progress;
    private float previousProgress;
    private float renderOffset;
    private float previousRenderOffset;
    private boolean renderOffsetInitialized;
    private boolean running;
    private boolean transportMode;
    private boolean worldMode;
    private ItemStack expectedInput = ItemStack.EMPTY;
    private String recipeId = "";
    private int inputCount;

    @Override public float getStressImpact() { return 8.0F; }

    public float workingSpeed() {
        float speed = getSpeed();
        if (speed == 0.0F && world instanceof FakeWorld fake
                && fake.getMobileChunk().getEntityMovingWorld() instanceof BearingContraptionEntity contraption) {
            speed = contraption.getSpeed();
        }
        return speed;
    }

    @Override
    public void update() {
        super.update();
        if (world == null) return;
        previousProgress = progress;
        float speed = Math.abs(workingSpeed());
        if (world.isRemote) {
            if (!renderOffsetInitialized) initializeRenderOffset();
            previousRenderOffset = renderOffset;
            if (running && speed != 0.0F) progress = Math.min(2000.0F, progress + speed);
            float step = speed * 1.25F / 1000.0F;
            renderOffset += MathHelper.clamp(offsetFor(progress) - renderOffset, -step, step);
            return;
        }
        if (speed == 0.0F) return;
        if (!running) {
            if (world.getTotalWorldTime() % 10L != 0L) return;
            TileEntity target = world.getTileEntity(pos.down(2));
            if (target instanceof TransportedItemHolder holder) {
                holdTransportedItem(holder);
                return;
            }
            if (!(target instanceof BasinTileEntity basin)) {
                tryStartInWorld();
                return;
            }
            BasinPlan upstream = findUpstream(basin, null);
            if (upstream != null) {
                expectedInput = ItemStack.EMPTY; recipeId = UPSTREAM_PREFIX + upstream.recipe().id(); inputCount = 0;
                transportMode = worldMode = false; progress = previousProgress = 0.0F; running = true;
                markDirty(); sync(); return;
            }
            CompactingCraft.Plan plan = CompactingCraft.find(world, basin);
            if (plan == null) return;
            expectedInput = plan.input().copy();
            recipeId = plan.recipe().toString();
            inputCount = plan.count();
            transportMode = worldMode = false;
            progress = previousProgress = 0.0F;
            running = true;
            markDirty();
            sync();
            return;
        }
        progress = Math.min(2000.0F, progress + speed);
        if (previousProgress < 1000.0F && progress >= 1000.0F) {
            world.playSound(null, pos, transportMode ? net.minecraft.init.SoundEvents.BLOCK_CLOTH_HIT
                            : net.minecraft.init.SoundEvents.BLOCK_ANVIL_LAND,
                    net.minecraft.util.SoundCategory.BLOCKS, transportMode ? .6F : .15F,
                    .75F + Math.min(speed, 256F) / 1024F);
            world.playSound(null, pos, net.minecraft.init.SoundEvents.ENTITY_ITEM_BREAK,
                    net.minecraft.util.SoundCategory.BLOCKS, .25F, transportMode ? .75F : 1F);
            if (transportMode) applyTransported(); else if (worldMode) applyInWorld(); else applyCompacting();
            markDirty();
            sync();
        }
        if (progress >= 2000.0F) {
            running = false;
            transportMode = worldMode = false;
            progress = previousProgress = 0.0F;
            expectedInput = ItemStack.EMPTY;
            recipeId = "";
            inputCount = 0;
            markDirty();
            sync();
        } else if (world.getTotalWorldTime() % 20L == 0L) {
            markDirty();
        }
    }

    private BasinTileEntity findBasin() {
        TileEntity tile = world.getTileEntity(pos.down(2));
        return tile instanceof BasinTileEntity basin ? basin : null;
    }

    private void tryStartInWorld() {
        for (EntityItem entity : world.getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(pos.down()).grow(-.125D))) {
            if (!entity.isEntityAlive() || !entity.onGround || PressingRecipes.result(entity.getItem()).isEmpty()) continue;
            expectedInput = ItemStack.EMPTY;
            recipeId = "";
            inputCount = 0;
            transportMode = false;
            worldMode = true;
            progress = previousProgress = 0.0F;
            running = true;
            markDirty();
            sync();
            return;
        }
    }

    private void applyInWorld() {
        TileEntity target = world.getTileEntity(pos.down(2));
        if (target instanceof TransportedItemHolder || target instanceof BasinTileEntity) return;
        for (EntityItem entity : world.getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(pos.down()))) {
            if (!entity.isEntityAlive() || !entity.onGround) continue;
            ItemStack input = entity.getItem().copy();
            if (PressingRecipes.result(input).isEmpty()) continue;
            ItemStack result = PressingRecipes.finish(input, world.rand);
            if (result.isEmpty()) continue;
            if (input.getCount() == 1) {
                entity.setItem(result);
            } else {
                EntityItem output = new EntityItem(world, entity.posX, entity.posY, entity.posZ, result);
                output.setDefaultPickupDelay();
                output.motionX = (world.rand.nextDouble() - .5D) * .1D;
                output.motionY = (world.rand.nextDouble() - .5D) * .1D;
                output.motionZ = (world.rand.nextDouble() - .5D) * .1D;
                if (!world.spawnEntity(output)) continue;
                ItemStack remaining = input.copy();
                remaining.shrink(1);
                entity.setItem(remaining);
            }
            if (world instanceof WorldServer server) {
                server.spawnParticle(EnumParticleTypes.ITEM_CRACK, entity.posX, entity.posY + .125D, entity.posZ,
                        15, .125D, 0D, .125D, .1D, Item.getIdFromItem(input.getItem()), input.getMetadata());
            }
            onItemPressed(result);
            return;
        }
    }

    @Override public boolean holdTransportedItem(TransportedItemHolder holder) {
        if (world == null || world.isRemote) return false;
        ItemStack input = holder.getTransportedItem();
        if (PressingRecipes.result(input).isEmpty()) return false;
        if (running) return transportMode && ItemStack.areItemsEqual(expectedInput, input)
                && ItemStack.areItemStackTagsEqual(expectedInput, input);
        if (workingSpeed() == 0.0F) return false;
        expectedInput = input.copy();
        expectedInput.setCount(1);
        recipeId = "";
        inputCount = 1;
        transportMode = true;
        worldMode = false;
        progress = previousProgress = 0.0F;
        running = true;
        markDirty();
        sync();
        return true;
    }

    private void applyTransported() {
        TileEntity target = world.getTileEntity(pos.down(2));
        if (!(target instanceof TransportedItemHolder holder)) return;
        ItemStack input = holder.getTransportedItem();
        if (input.isEmpty() || !ItemStack.areItemsEqual(expectedInput, input)
                || !ItemStack.areItemStackTagsEqual(expectedInput, input)) return;
        ItemStack result = PressingRecipes.finish(input, world.rand);
        if (!result.isEmpty()) {
            holder.replaceOneWith(result);
            onItemPressed(result);
        }
    }

    private void onItemPressed(ItemStack result) {
        if (result.getItem() == Item.getItemFromBlock(com.cogwork.cogworkbridge.registry.AllBlocks.TRACK))
            com.cogwork.cogworkbridge.content.advancement.AllAdvancements.count(this, "TracksCreated", result.getCount(), 1000, "track_crafting_factory");
        com.cogwork.cogworkbridge.content.advancement.AllAdvancements.awardAt(world, pos, "mechanical_press");
    }

    private void applyCompacting() {
        BasinTileEntity basin = findBasin();
        if (basin != null && recipeId.startsWith(UPSTREAM_PREFIX)) {
            BasinPlan plan = findUpstream(basin, recipeId.substring(UPSTREAM_PREFIX.length()));
            if (plan == null) return;
            var recipe = plan.recipe();
            if (!basin.processBatch(plan.counts(), java.util.Collections.singletonList(recipe.itemOutput()), false)) return;
            for (net.minecraftforge.fluids.FluidStack fluid : recipe.fluids()) basin.drainInputFluid(fluid, true);
            if (recipe.fluidOutput() != null) basin.fillOutput(recipe.fluidOutput(), true);
            com.cogwork.cogworkbridge.content.advancement.AllAdvancements.awardAt(world, pos, "compacting");
            return;
        }
        CompactingCraft.Plan plan = basin == null ? null : CompactingCraft.find(world, basin);
        if (plan == null || plan.count() != inputCount || !plan.recipe().toString().equals(recipeId)
                || !ItemStack.areItemsEqual(expectedInput, plan.input())
                || !ItemStack.areItemStackTagsEqual(expectedInput, plan.input())) return;
        if (basin.processItems(plan.slot(), plan.input(), plan.count(), plan.results(), false)) {
            com.cogwork.cogworkbridge.content.advancement.AllAdvancements.awardAt(world, pos, "compacting");
        }
    }
    private BasinPlan findUpstream(BasinTileEntity basin, String wanted) {
        TileEntity heater = world.getTileEntity(pos.down(3));
        int heat = heater instanceof com.cogwork.cogworkbridge.content.processing.burner.BlazeBurnerTileEntity burner ? burner.getProcessingHeat() : 0;
        for (var recipe : com.cogwork.cogworkbridge.content.processing.UpstreamProcessingRecipes.compacting()) {
            if (wanted != null && !wanted.equals(recipe.id()) || heat < recipe.heat()) continue;
            int[] counts = com.cogwork.cogworkbridge.content.processing.mixer.MixingRecipes.matchItems(basin, recipe.items());
            if (counts == null) continue;
            boolean available = true;
            for (net.minecraftforge.fluids.FluidStack fluid : recipe.fluids())
                if (basin.drainInputFluid(fluid, false) != fluid.amount) { available = false; break; }
            if (!available || recipe.fluidOutput() != null && basin.fillOutput(recipe.fluidOutput(), false) != recipe.fluidOutput().amount
                    || !basin.processBatch(counts, java.util.Collections.singletonList(recipe.itemOutput()), true)) continue;
            return new BasinPlan(recipe, counts);
        }
        return null;
    }

    public float headOffset(float partialTicks) {
        if (world != null && world.isRemote && renderOffsetInitialized) {
            return previousRenderOffset + (renderOffset - previousRenderOffset)
                    * MathHelper.clamp(partialTicks, 0.0F, 1.0F);
        }
        float phase = MathHelper.clamp(previousProgress + (progress - previousProgress) * partialTicks, 0.0F, 2000.0F);
        return offsetFor(phase);
    }

    private float offsetFor(float phase) {
        float factor = phase <= 1000.0F ? phase / 1000.0F : (2000.0F - phase) / 1000.0F;
        return (worldMode ? 1F : 1.25F) * factor;
    }

    private void initializeRenderOffset() {
        renderOffset = previousRenderOffset = offsetFor(progress);
        renderOffsetInitialized = true;
    }

    @Override public AxisAlignedBB getRenderBoundingBox() { return new AxisAlignedBB(pos).grow(2.0D); }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setFloat("PressProgress", progress);
        tag.setBoolean("PressRunning", running);
        tag.setBoolean("PressTransport", transportMode);
        tag.setBoolean("PressWorld", worldMode);
        tag.setString("PressRecipe", recipeId);
        tag.setInteger("PressCount", inputCount);
        if (!expectedInput.isEmpty()) tag.setTag("PressInput", expectedInput.writeToNBT(new NBTTagCompound()));
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        progress = previousProgress = MathHelper.clamp(tag.getFloat("PressProgress"), 0.0F, 2000.0F);
        running = tag.getBoolean("PressRunning");
        transportMode = tag.getBoolean("PressTransport");
        worldMode = tag.getBoolean("PressWorld");
        initializeRenderOffset();
        recipeId = tag.getString("PressRecipe");
        inputCount = tag.getInteger("PressCount");
        expectedInput = tag.hasKey("PressInput", 10) ? new ItemStack(tag.getCompoundTag("PressInput")) : ItemStack.EMPTY;
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        NBTTagCompound tag = super.getUpdateTag();
        tag.setFloat("PressProgress", progress);
        tag.setBoolean("PressRunning", running);
        tag.setBoolean("PressWorld", worldMode);
        return tag;
    }

    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        super.handleUpdateTag(tag);
        progress = previousProgress = MathHelper.clamp(tag.getFloat("PressProgress"), 0.0F, 2000.0F);
        worldMode = tag.getBoolean("PressWorld");
        if (!renderOffsetInitialized) initializeRenderOffset();
        running = tag.getBoolean("PressRunning");
    }
}
