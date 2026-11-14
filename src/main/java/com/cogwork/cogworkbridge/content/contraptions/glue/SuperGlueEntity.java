package com.cogwork.cogworkbridge.content.contraptions.glue;

import com.cogwork.cogworkbridge.registry.AllEntities;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Invisible, persistent super-glue area used by the future contraption assembler. */
public class SuperGlueEntity extends Entity {

    private GlueBox glueBox = GlueBox.of(0, 0, 0, 1, 1, 1);
    private Set<GridPos> volume = Set.of();

    public SuperGlueEntity(World world) {
        super(world);
        setSize(0.0F, 0.0F);
        noClip = true;
        // This marker spans the glue box but must never reserve its cells from
        // vanilla block placement. A serialized entity naturally inherits the
        // default false value, so true only reproduced a session-only bug.
        preventEntitySpawning = false;
    }

    public SuperGlueEntity(World world, GlueBox glueBox) {
        this(world);
        setGlueBox(glueBox);
    }

    public SuperGlueEntity(World world, GlueBox glueBox, Set<GridPos> volume) {
        this(world);
        setGlueVolume(glueBox, volume);
    }

    public GlueBox getGlueBox() {
        return glueBox;
    }

    public void setGlueBox(GlueBox glueBox) {
        this.glueBox = glueBox;
        this.volume = new LinkedHashSet<>(glueBox.positions());
        updateBoundingBox();
    }

    public void setGlueVolume(GlueBox glueBox, Set<GridPos> volume) {
        this.glueBox = glueBox;
        this.volume = new LinkedHashSet<>();
        for (GridPos pos : volume) {
            if (glueBox.contains(pos)) {
                this.volume.add(pos);
            }
        }
        updateBoundingBox();
    }

    private void updateBoundingBox() {
        setEntityBoundingBox(toAabb(glueBox));
        resetPositionToBB();
    }

    public boolean contains(BlockPos pos) {
        return contains(AllEntities.gridPosition(pos));
    }

    public boolean contains(GridPos pos) {
        return volume.contains(pos);
    }

    /** Every non-empty cell still inside the persisted geometric box. */
    public boolean canBondAt(GridPos pos) {
        return glueBox.contains(pos) && volume.contains(pos);
    }

    public boolean canBondAt(BlockPos pos) {
        return canBondAt(AllEntities.gridPosition(pos));
    }

    public Set<GridPos> getGlueVolume() {
        return Collections.unmodifiableSet(volume);
    }

    /** Adds a newly placed block while keeping air cells out of the rigid group. */
    public boolean addMember(GridPos pos) {
        return glueBox.contains(pos) && volume.add(pos);
    }

    /** Removes a block so an emptied cell cannot bridge the remaining glued blocks. */
    public boolean removeMember(GridPos pos) {
        return volume.remove(pos);
    }

    public Vec3d center() {
        return new Vec3d(
                (glueBox.minX() + glueBox.maxX()) * 0.5D,
                (glueBox.minY() + glueBox.maxY()) * 0.5D,
                (glueBox.minZ() + glueBox.maxZ()) * 0.5D);
    }

    public static AxisAlignedBB toAabb(GlueBox box) {
        return new AxisAlignedBB(box.minX(), box.minY(), box.minZ(),
                box.maxX(), box.maxY(), box.maxZ());
    }

    public static GlueBox fromAabb(AxisAlignedBB bounds) {
        return GlueBox.of(floor(bounds.minX), floor(bounds.minY), floor(bounds.minZ),
                ceil(bounds.maxX), ceil(bounds.maxY), ceil(bounds.maxZ));
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        motionX = 0.0D;
        motionY = 0.0D;
        motionZ = 0.0D;
    }

    @Override
    protected void entityInit() {
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean canBePushed() {
        return false;
    }

    @Override
    public boolean canBeAttackedWithItem() {
        return false;
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound compound) {
        NBTTagCompound bounds = new NBTTagCompound();
        bounds.setInteger("MinX", glueBox.minX());
        bounds.setInteger("MinY", glueBox.minY());
        bounds.setInteger("MinZ", glueBox.minZ());
        bounds.setInteger("MaxX", glueBox.maxX());
        bounds.setInteger("MaxY", glueBox.maxY());
        bounds.setInteger("MaxZ", glueBox.maxZ());
        compound.setTag("GlueBox", bounds);
        NBTTagList positions = new NBTTagList();
        for (GridPos pos : volume) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setInteger("X", pos.x());
            entry.setInteger("Y", pos.y());
            entry.setInteger("Z", pos.z());
            positions.appendTag(entry);
        }
        compound.setTag("GlueVolume", positions);
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound compound) {
        NBTTagCompound bounds = compound.getCompoundTag("GlueBox");
        GlueBox loadedBox;
        if (bounds.hasKey("MinX")) {
            loadedBox = GlueBox.of(
                    bounds.getInteger("MinX"), bounds.getInteger("MinY"), bounds.getInteger("MinZ"),
                    bounds.getInteger("MaxX"), bounds.getInteger("MaxY"), bounds.getInteger("MaxZ"));
        } else {
            loadedBox = glueBox;
        }

        Set<GridPos> stored = new LinkedHashSet<>();
        NBTTagList positions = compound.getTagList("GlueVolume", 10);
        for (int i = 0; i < positions.tagCount(); i++) {
            NBTTagCompound entry = positions.getCompoundTagAt(i);
            GridPos pos = new GridPos(entry.getInteger("X"),
                    entry.getInteger("Y"), entry.getInteger("Z"));
            if (loadedBox.contains(pos)) {
                stored.add(pos);
            }
        }
        if (!compound.hasKey("GlueVolume", 9)) {
            stored.addAll(loadedBox.positions());
        }
        setGlueVolume(loadedBox, stored);
    }

    @Override
    protected boolean shouldSetPosAfterLoading() {
        // The vanilla zero-size entity box would replace the loaded glue bounds.
        return false;
    }

    @Override
    public boolean shouldRenderInPass(int pass) {
        return false;
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    private static int ceil(double value) {
        return (int) Math.ceil(value);
    }
}
