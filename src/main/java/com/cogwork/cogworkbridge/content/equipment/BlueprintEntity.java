package com.cogwork.cogworkbridge.content.equipment;

import com.cogwork.cogworkbridge.CogworkBridge;
import com.cogwork.cogworkbridge.content.fluids.ListFilterGuiHandler;
import com.cogwork.cogworkbridge.content.fluids.ListFilterItem;
import com.cogwork.cogworkbridge.registry.AllItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.items.ItemHandlerHelper;

public final class BlueprintEntity extends Entity {
    private static final DataParameter<Integer> FACING = EntityDataManager.createKey(BlueprintEntity.class, DataSerializers.VARINT);
    private static final DataParameter<Integer> ORIENTATION = EntityDataManager.createKey(BlueprintEntity.class, DataSerializers.VARINT);
    private static final DataParameter<Integer> SIZE = EntityDataManager.createKey(BlueprintEntity.class, DataSerializers.VARINT);
    @SuppressWarnings("unchecked") private static final DataParameter<ItemStack>[] DISPLAY = new DataParameter[18];
    static { for (int i = 0; i < DISPLAY.length; i++) DISPLAY[i] = EntityDataManager.createKey(BlueprintEntity.class, DataSerializers.ITEM_STACK); }

    private final java.util.List<NonNullList<ItemStack>> sections = new java.util.ArrayList<>();
    private final boolean[] manualIcon = new boolean[9];
    private BlockPos support = BlockPos.ORIGIN;

    public BlueprintEntity(World world) {
        super(world); noClip = true; setNoGravity(true); setSize(.9F, .9F);
        for (int i = 0; i < 9; i++) sections.add(NonNullList.withSize(11, ItemStack.EMPTY));
    }
    public BlueprintEntity(World world, BlockPos support, EnumFacing face, EnumFacing orientation, int size) {
        this(world); this.support = support.toImmutable();
        dataManager.set(FACING, face.getIndex()); dataManager.set(ORIENTATION, orientation.getHorizontalIndex()); dataManager.set(SIZE, size);
        double shift = size == 2 ? .5D : 0D;
        EnumFacing x = axisX(), z = axisZ();
        setPosition(support.getX() + .5D + face.getXOffset() * .55D + (x.getXOffset() + z.getXOffset()) * shift,
                support.getY() + .5D + face.getYOffset() * .55D + (x.getYOffset() + z.getYOffset()) * shift,
                support.getZ() + .5D + face.getZOffset() * .55D + (x.getZOffset() + z.getZOffset()) * shift);
        updateBounds();
    }
    @Override protected void entityInit() {
        dataManager.register(FACING, EnumFacing.UP.getIndex()); dataManager.register(ORIENTATION, EnumFacing.SOUTH.getHorizontalIndex());
        dataManager.register(SIZE, 1);
        for (DataParameter<ItemStack> display : DISPLAY) dataManager.register(display, ItemStack.EMPTY);
    }
    public EnumFacing facing() { return EnumFacing.byIndex(dataManager.get(FACING)); }
    public EnumFacing orientation() { return EnumFacing.byHorizontalIndex(dataManager.get(ORIENTATION)); }
    public int size() { return Math.max(1, Math.min(3, dataManager.get(SIZE))); }
    private static EnumFacing reflectZ(EnumFacing direction) {
        return direction == EnumFacing.NORTH ? EnumFacing.SOUTH : direction == EnumFacing.SOUTH ? EnumFacing.NORTH : direction;
    }
    private EnumFacing axisX() {
        return switch (facing()) {
            case UP -> orientation().rotateYCCW(); case DOWN -> reflectZ(orientation().rotateYCCW());
            case NORTH, SOUTH -> EnumFacing.EAST; case EAST -> EnumFacing.DOWN; case WEST -> EnumFacing.UP;
        };
    }
    private EnumFacing axisZ() {
        return switch (facing()) {
            case UP -> orientation(); case DOWN -> reflectZ(orientation()); case NORTH -> EnumFacing.UP;
            case SOUTH -> EnumFacing.DOWN; case EAST, WEST -> EnumFacing.SOUTH;
        };
    }
    public boolean canPlace() {
        for (int x = 0; x < size(); x++) for (int z = 0; z < size(); z++) {
            int start = (size() - 1) / 2;
            BlockPos pos = support.offset(axisX(), x - start).offset(axisZ(), z - start);
            if (!world.isBlockLoaded(pos) || !world.isSideSolid(pos, facing())) return false;
        }
        return world.getCollisionBoxes(this, getEntityBoundingBox()).isEmpty()
                && world.getEntitiesWithinAABB(BlueprintEntity.class, getEntityBoundingBox(), other -> other != this).isEmpty();
    }
    public ItemStack pattern(int section, int slot) { return sections.get(section).get(slot); }
    public ItemStack display(int section, int slot) { return dataManager.get(DISPLAY[section * 2 + slot]); }
    public void setPattern(int section, int slot, ItemStack stack) {
        if (section < 0 || section >= size() * size() || slot < 0 || slot >= 11) return;
        ItemStack copy = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        if (!copy.isEmpty()) copy.setCount(1);
        sections.get(section).set(slot, copy);
        if (slot == 9) manualIcon[section] = !copy.isEmpty();
        if (slot < 10) inferIcon(section);
        updateDisplay(section);
    }
    private void inferIcon(int section) {
        if (manualIcon[section]) return;
        InventoryCrafting grid = grid(section);
        IRecipe recipe = CraftingManager.findMatchingRecipe(grid, world);
        sections.get(section).set(9, recipe == null ? ItemStack.EMPTY : recipe.getCraftingResult(grid).copy());
    }
    private void updateDisplay(int section) {
        for (int i = 0; i < 2; i++) dataManager.set(DISPLAY[section * 2 + i], sections.get(section).get(9 + i).copy());
    }
    private InventoryCrafting grid(int section) {
        Container dummy = new Container() { @Override public boolean canInteractWith(EntityPlayer player) { return false; } };
        InventoryCrafting grid = new InventoryCrafting(dummy, 3, 3);
        for (int i = 0; i < 9; i++) grid.setInventorySlotContents(i, pattern(section, i).copy());
        return grid;
    }
    private void updateBounds() {
        double half = size() * .5D - .01D;
        EnumFacing x = axisX(), z = axisZ(), face = facing();
        double dx = (Math.abs(x.getXOffset()) + Math.abs(z.getXOffset())) * half + Math.abs(face.getXOffset()) * .04D;
        double dy = (Math.abs(x.getYOffset()) + Math.abs(z.getYOffset())) * half + Math.abs(face.getYOffset()) * .04D;
        double dz = (Math.abs(x.getZOffset()) + Math.abs(z.getZOffset())) * half + Math.abs(face.getZOffset()) * .04D;
        setEntityBoundingBox(new AxisAlignedBB(posX - dx, posY - dy, posZ - dz, posX + dx, posY + dy, posZ + dz));
    }
    @Override public void notifyDataManagerChange(DataParameter<?> key) {
        super.notifyDataManagerChange(key);
        if (FACING.equals(key) || ORIENTATION.equals(key) || SIZE.equals(key)) updateBounds();
    }
    @Override public boolean canBeCollidedWith() { return true; }
    @Override public boolean canBeAttackedWithItem() { return true; }
    @Override public boolean canBePushed() { return false; }
    @Override public void onUpdate() {
        super.onUpdate(); updateBounds();
        if (!world.isRemote && ticksExisted % 20 == 0 && !canPlace()) { dropBlueprint(); setDead(); }
    }
    private void dropBlueprint() {
        if (world.getGameRules().getBoolean("doEntityDrops")) entityDropItem(new ItemStack(AllItems.CRAFTING_BLUEPRINT), 0);
    }
    private int sectionAt(Vec3d relative) {
        EnumFacing x = axisX(), z = axisZ();
        double a = relative.x * x.getXOffset() + relative.y * x.getYOffset() + relative.z * x.getZOffset();
        double b = relative.x * z.getXOffset() + relative.y * z.getYOffset() + relative.z * z.getZOffset();
        int col = Math.max(0, Math.min(size() - 1, (int) Math.floor(a + size() * .5D)));
        int row = Math.max(0, Math.min(size() - 1, (int) Math.floor(b + size() * .5D)));
        return col + row * size();
    }
    @Override public boolean attackEntityFrom(DamageSource source, float amount) {
        if (world.isRemote || isDead) return true;
        if (source.getTrueSource() instanceof EntityPlayer player) {
            Vec3d eyes = player.getPositionEyes(1F);
            RayTraceResult hit = getEntityBoundingBox().calculateIntercept(eyes, eyes.add(player.getLook(1F).scale(6D)));
            int section = hit == null ? 0 : sectionAt(hit.hitVec.subtract(getPositionVector()));
            if (!display(section, 0).isEmpty()) {
                sections.set(section, NonNullList.withSize(11, ItemStack.EMPTY)); manualIcon[section] = false; updateDisplay(section); return true;
            }
            if (!player.capabilities.isCreativeMode) dropBlueprint();
        }
        setDead(); return true;
    }
    @Override public EnumActionResult applyPlayerInteraction(EntityPlayer player, Vec3d vec, EnumHand hand) {
        if (player instanceof net.minecraftforge.common.util.FakePlayer) return EnumActionResult.PASS;
        interact(player, hand, sectionAt(vec)); return EnumActionResult.SUCCESS;
    }
    @Override public boolean processInitialInteract(EntityPlayer player, EnumHand hand) {
        if (player instanceof net.minecraftforge.common.util.FakePlayer) return false;
        Vec3d eyes = player.getPositionEyes(1F);
        RayTraceResult hit = getEntityBoundingBox().calculateIntercept(eyes, eyes.add(player.getLook(1F).scale(6D)));
        interact(player, hand, hit == null ? 0 : sectionAt(hit.hitVec.subtract(getPositionVector())));
        return true;
    }
    private void interact(EntityPlayer player, EnumHand hand, int section) {
        if (world.isRemote) return;
        if (display(section, 0).isEmpty() || player.getHeldItem(hand).getItem() == AllItems.WRENCH)
            player.openGui(CogworkBridge.instance, ListFilterGuiHandler.BLUEPRINT_GUI, world, getEntityId(), section, 0);
        else {
            int total = 0;
            do { int count = craft(player, section, 64 - total); if (count <= 0) break; total += count; } while (player.isSneaking() && total < 64);
        }
    }
    private int craft(EntityPlayer player, int section, int limit) {
        net.minecraftforge.common.ForgeHooks.setCraftingPlayer(player);
        try {
        InventoryCrafting actual = grid(section);
        int[] used = new int[player.inventory.getSizeInventory()];
        for (int i = 0; i < 9; i++) {
            ItemStack expected = pattern(section, i);
            if (expected.isEmpty()) continue;
            int chosen = -1;
            for (int slot = 0; slot < used.length; slot++) {
                ItemStack have = player.inventory.getStackInSlot(slot);
                if (have.getCount() > used[slot] && ListFilterItem.matchesItem(expected, have)) {
                    chosen = slot; used[slot]++; ItemStack one = have.copy(); one.setCount(1); actual.setInventorySlotContents(i, one); break;
                }
            }
            if (chosen < 0) return 0;
        }
        IRecipe recipe = CraftingManager.findMatchingRecipe(actual, world);
        if (recipe == null) return 0;
        ItemStack output = recipe.getCraftingResult(actual);
        if (output.isEmpty() || output.getCount() > limit) return 0;
        NonNullList<ItemStack> remaining = recipe.getRemainingItems(actual);
        for (int slot = 0; slot < used.length; slot++) if (used[slot] > 0) player.inventory.decrStackSize(slot, used[slot]);
        output.onCrafting(world, player, output.getCount());
        net.minecraftforge.fml.common.FMLCommonHandler.instance().firePlayerCraftingEvent(player, output, actual);
        ItemHandlerHelper.giveItemToPlayer(player, output.copy());
        for (ItemStack remainder : remaining) if (!remainder.isEmpty()) ItemHandlerHelper.giveItemToPlayer(player, remainder.copy());
        player.inventory.markDirty(); return output.getCount();
        } finally {
            net.minecraftforge.common.ForgeHooks.setCraftingPlayer(null);
        }
    }
    @Override protected void writeEntityToNBT(NBTTagCompound tag) {
        tag.setInteger("Facing", facing().getIndex()); tag.setInteger("Orientation", orientation().getHorizontalIndex());
        tag.setInteger("Size", size()); tag.setLong("Support", support.toLong());
        NBTTagList saved = new NBTTagList();
        for (int i = 0; i < size() * size(); i++) {
            NBTTagCompound section = new NBTTagCompound(); ItemStackHelper.saveAllItems(section, sections.get(i));
            section.setBoolean("ManualIcon", manualIcon[i]); saved.appendTag(section);
        }
        tag.setTag("Sections", saved);
    }
    @Override protected void readEntityFromNBT(NBTTagCompound tag) {
        dataManager.set(FACING, tag.getInteger("Facing"));
        dataManager.set(ORIENTATION, tag.hasKey("Orientation") ? tag.getInteger("Orientation") : EnumFacing.SOUTH.getHorizontalIndex());
        dataManager.set(SIZE, tag.hasKey("Size") ? Math.max(1, Math.min(3, tag.getInteger("Size"))) : 1);
        EnumFacing face = facing();
        support = tag.hasKey("Support") ? BlockPos.fromLong(tag.getLong("Support"))
                : new BlockPos(posX - face.getXOffset() * .55D, posY - face.getYOffset() * .55D, posZ - face.getZOffset() * .55D);
        NBTTagList saved = tag.getTagList("Sections", 10);
        for (int i = 0; i < sections.size(); i++) {
            NonNullList<ItemStack> items = NonNullList.withSize(11, ItemStack.EMPTY);
            NBTTagCompound section = i < saved.tagCount() ? saved.getCompoundTagAt(i) : i == 0 && !tag.hasKey("Sections") ? tag : new NBTTagCompound();
            ItemStackHelper.loadAllItems(section, items); sections.set(i, items); manualIcon[i] = section.getBoolean("ManualIcon");
            if (i < size() * size()) { inferIcon(i); updateDisplay(i); }
        }
    }
}
