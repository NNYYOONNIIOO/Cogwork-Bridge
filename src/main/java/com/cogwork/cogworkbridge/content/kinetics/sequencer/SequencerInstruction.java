package com.cogwork.cogworkbridge.content.kinetics.sequencer;

import net.minecraft.nbt.NBTTagCompound;

/** A single, bounded instruction in a gearshift program. */
public record SequencerInstruction(Type type, int value, int speed) {
    public static final int MAX_STEPS = 5;

    public enum Type {
        TURN_ANGLE(360, 90), TURN_DISTANCE(128, 5), DELAY(600, 10), AWAIT(0, 0), END(0, 0);

        public final int maxValue;
        public final int defaultValue;

        Type(int maxValue, int defaultValue) {
            this.maxValue = maxValue;
            this.defaultValue = defaultValue;
        }

        public boolean moves() {
            return this == TURN_ANGLE || this == TURN_DISTANCE;
        }
    }

    public SequencerInstruction {
        type = type == null ? Type.END : type;
        value = type.maxValue == 0 ? 0 : Math.max(1, Math.min(type.maxValue, value));
        if (speed != -2 && speed != -1 && speed != 1 && speed != 2) {
            speed = 1;
        }
    }

    public static SequencerInstruction of(Type type) {
        return new SequencerInstruction(type, type.defaultValue, 1);
    }

    public NBTTagCompound write() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("Type", type.name());
        tag.setInteger("Value", value);
        tag.setInteger("Speed", speed);
        return tag;
    }

    public static SequencerInstruction read(NBTTagCompound tag) {
        Type type;
        try {
            type = Type.valueOf(tag.getString("Type"));
        } catch (IllegalArgumentException ignored) {
            type = Type.END;
        }
        return new SequencerInstruction(type, tag.getInteger("Value"), tag.getInteger("Speed"));
    }
}
