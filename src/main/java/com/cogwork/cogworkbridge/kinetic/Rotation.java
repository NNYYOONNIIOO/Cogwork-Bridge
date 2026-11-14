package com.cogwork.cogworkbridge.kinetic;

import net.minecraft.util.EnumFacing;

/**
 * Rotation direction and speed along an axis.
 *
 * <p>Speeds follow Create conventions: rotations per minute (RPM) as a float,
 * positive or negative. The sign encodes the visual spin direction around the
 * given axis (right-hand rule).
 */
public final class Rotation {

    public static final Rotation NONE = new Rotation(EnumFacing.Axis.Y, 0.0F);

    private final EnumFacing.Axis axis;
    private final float speed;

    private Rotation(EnumFacing.Axis axis, float speed) {
        this.axis = axis;
        this.speed = speed;
    }

    public static Rotation of(EnumFacing.Axis axis, float speed) {
        if (speed == 0.0F) {
            return NONE;
        }
        return new Rotation(axis, speed);
    }

    public EnumFacing.Axis getAxis() {
        return axis;
    }

    public float getSpeed() {
        return speed;
    }

    public boolean isMoving() {
        return speed != 0.0F;
    }

    public Rotation reversed() {
        return speed == 0.0F ? this : new Rotation(axis, -speed);
    }

    /**
     * Re-orients this rotation onto another axis. Used by gearboxes and cogwheels.
     */
    public Rotation onAxis(EnumFacing.Axis newAxis) {
        if (newAxis == axis) {
            return this;
        }
        return new Rotation(newAxis, speed);
    }

    public Rotation withSpeed(float newSpeed) {
        return newSpeed == 0.0F ? NONE : new Rotation(axis, newSpeed);
    }

    @Override
    public String toString() {
        return "Rotation[" + axis + ", " + speed + " rpm]";
    }
}
