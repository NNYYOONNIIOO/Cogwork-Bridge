package com.cogwork.cogworkbridge.content.processing.crafter;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.IStringSerializable;

public enum MechanicalCrafterDirection implements IStringSerializable {
    LEFT("left"), UP("up"), RIGHT("right"), DOWN("down");

    private final String name;

    MechanicalCrafterDirection(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    public EnumFacing relativeTo(EnumFacing facing) {
        if (facing == EnumFacing.UP) {
            return switch (this) {
                case UP -> EnumFacing.SOUTH;
                case DOWN -> EnumFacing.NORTH;
                case LEFT -> EnumFacing.WEST;
                case RIGHT -> EnumFacing.EAST;
            };
        }
        if (facing == EnumFacing.DOWN) {
            return switch (this) {
                case UP -> EnumFacing.NORTH;
                case DOWN -> EnumFacing.SOUTH;
                case LEFT -> EnumFacing.WEST;
                case RIGHT -> EnumFacing.EAST;
            };
        }
        return switch (this) {
            case UP -> EnumFacing.UP;
            case DOWN -> EnumFacing.DOWN;
            case LEFT -> facing.rotateYCCW();
            case RIGHT -> facing.rotateY();
        };
    }

    public static MechanicalCrafterDirection relativeTo(EnumFacing facing, EnumFacing side) {
        for (MechanicalCrafterDirection direction : values()) {
            if (direction.relativeTo(facing) == side) return direction;
        }
        return DOWN;
    }
}
