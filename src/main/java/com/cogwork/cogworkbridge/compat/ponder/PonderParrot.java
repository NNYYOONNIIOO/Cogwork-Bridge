package com.cogwork.cogworkbridge.compat.ponder;

import net.createmod.ponder.api.element.ParrotPose;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderWorld;
import net.createmod.ponder.foundation.element.ParrotElementImpl;
import net.createmod.ponder.render.PonderWorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.entity.passive.EntityParrot;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.Vec3d;
import java.util.Collections;
import java.util.function.Supplier;

/** Keeps the bird and its props on one scene transform, interpolating position exactly once. */
final class PonderParrot extends ParrotElementImpl {
    private static final PonderWorldRenderer RENDERER = new PonderWorldRenderer();
    private boolean conductor;
    private final boolean wrench;
    private final Vec3d initial;
    private final ParrotPose initialPose;
    private ParrotPose pose;
    private EntityParrot entity;
    private NBTTagCompound restoredEntity;
    private Vec3d previousPosition;
    private Vec3d previousRotation = Vec3d.ZERO;
    private float time;
    PonderParrot(Vec3d pos, Supplier<? extends ParrotPose> pose, boolean wrench) {
        super(pos, pose);
        initial = previousPosition = pos;
        initialPose = this.pose = pose.get();
        this.wrench = wrench;
    }
    void setConductor(boolean value) { conductor = value; }
    @Override public void setPositionOffset(Vec3d value, boolean immediate) {
        previousPosition = immediate ? value : getPositionOffset();
        super.setPositionOffset(value, immediate);
    }
    @Override public void setRotation(Vec3d value, boolean immediate) {
        previousRotation = immediate ? value : getRotation();
        super.setRotation(value, immediate);
    }
    @Override public void setPose(ParrotPose value) {
        if (value != null) { pose = value; super.setPose(value); }
    }
    @Override public void tick(PonderScene scene) {
        previousPosition = getPositionOffset();
        previousRotation = getRotation();
        time = scene.getCurrentTime();
        ensureEntity(scene.getWorld());
        entity.ticksExisted = (int) time;
        entity.prevRotationYaw = entity.renderYawOffset = entity.prevRenderYawOffset = entity.rotationYaw;
        entity.prevRotationPitch = entity.rotationPitch;
        if (pose != null) pose.tick(scene, entity, getPositionOffset());
        entity.renderYawOffset = entity.rotationYaw;
        entity.prevRotationYawHead = entity.rotationYawHead = entity.rotationYaw;
    }
    @Override public void reset(PonderScene scene) {
        super.reset(scene);
        conductor = false;
        time = 0;
        entity = null;
        restoredEntity = null;
        setPose(initialPose);
        setPositionOffset(initial, true);
        setRotation(Vec3d.ZERO, true);
    }
    private void ensureEntity(PonderWorld world) {
        if (entity != null && entity.world == world) return;
        entity = new EntityParrot(world);
        entity.setVariant(Math.floorMod(world.getAnchor().hashCode(), 5));
        if (restoredEntity != null) { entity.readFromNBT(restoredEntity); restoredEntity = null; }
        entity.prevRotationYaw = entity.renderYawOffset = entity.prevRenderYawOffset = entity.rotationYaw;
        entity.prevRotationPitch = entity.rotationPitch;
        entity.prevRotationYawHead = entity.rotationYawHead = entity.rotationYaw;
    }
    private static Vec3d interpolate(Vec3d previous, Vec3d current, float partial) {
        return previous.add(current.subtract(previous).scale(partial));
    }
    @Override public void renderLast(PonderWorld world, float partial) {
        ensureEntity(world);
        Vec3d at = interpolate(previousPosition, getPositionOffset(), partial)
                .add(fadeVector.scale(1 - getFade(partial)));
        Vec3d angle = interpolate(previousRotation, getRotation(), partial);
        // The generic renderer interpolates these again; all must hold the already interpolated point.
        entity.setPosition(at.x, at.y, at.z);
        entity.prevPosX = entity.lastTickPosX = at.x;
        entity.prevPosY = entity.lastTickPosY = at.y;
        entity.prevPosZ = entity.lastTickPosZ = at.z;
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(at.x, at.y, at.z);
            GlStateManager.rotate((float) angle.y, 0, 1, 0);
            GlStateManager.rotate((float) angle.x, 1, 0, 0);
            GlStateManager.rotate((float) angle.z, 0, 0, 1);
            RENDERER.renderEntities(Collections.singleton(entity), at, partial);
            GlStateManager.rotate(-(entity.prevRotationYaw + (entity.rotationYaw - entity.prevRotationYaw) * partial), 0, 1, 0);
            if (conductor) {
                GlStateManager.pushMatrix();
                try {
                    GlStateManager.translate(0, .6, 0);
                    GlStateManager.scale(.5, .5, .5);
                    PonderModels.hat();
                } finally { GlStateManager.popMatrix(); }
            }
            if (wrench) {
                GlStateManager.translate(-.1, .2, -.35);
                GlStateManager.rotate((float) Math.sin((time + partial) * .2F) * 10F, 0, 0, 1);
                GlStateManager.rotate(90, 1, 0, 0);
                GlStateManager.scale(1.5, 1.5, 1.5);
                Minecraft.getMinecraft().getRenderItem().renderItem(PonderData.item("create:wrench"), ItemCameraTransforms.TransformType.GROUND);
            }
        } finally { GlStateManager.popMatrix(); }
    }
    @Override public Object captureState() {
        NBTTagCompound data = entity == null ? restoredEntity : entity.writeToNBT(new NBTTagCompound());
        return new Object[]{super.captureState(), conductor, time, previousPosition, previousRotation, pose,
                data == null ? null : data.copy()};
    }
    @Override public void restoreState(Object state) {
        Object[] saved = (Object[]) state;
        super.restoreState(saved[0]);
        conductor = (Boolean) saved[1];
        time = (Float) saved[2];
        previousPosition = (Vec3d) saved[3];
        previousRotation = (Vec3d) saved[4];
        pose = (ParrotPose) saved[5];
        restoredEntity = saved[6] == null ? null : ((NBTTagCompound) saved[6]).copy();
        entity = null;
    }
}
