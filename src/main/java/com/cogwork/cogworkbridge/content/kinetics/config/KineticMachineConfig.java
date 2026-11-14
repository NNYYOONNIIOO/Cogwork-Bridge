package com.cogwork.cogworkbridge.content.kinetics.config;

import com.cogwork.cogworkbridge.content.kinetics.motor.CreativeMotorTileEntity;
import com.cogwork.cogworkbridge.content.logistics.arm.MechanicalArmTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.speedController.SpeedControllerTileEntity;
import com.cogwork.cogworkbridge.content.contraptions.bearing.MechanicalBearingTileEntity;
import com.cogwork.cogworkbridge.content.contraptions.actors.roller.MechanicalRollerTileEntity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

public enum KineticMachineConfig {
    CREATIVE_MOTOR(0),
    SPEED_CONTROLLER(1),
    MECHANICAL_BEARING(2),
    MECHANICAL_ROLLER(3),
    MECHANICAL_ARM(4),
    VALVE_HANDLE(5),
    WINDMILL_BEARING(6),
    STEAM_ENGINE(7),
    MECHANICAL_PISTON(8),
    CART_ASSEMBLER(9);

    private final int id;

    KineticMachineConfig(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public int read(TileEntity tile) {
        return switch (this) {
            case CREATIVE_MOTOR -> Math.round(
                    ((CreativeMotorTileEntity) tile).getConfiguredSpeed());
            case SPEED_CONTROLLER ->
                    ((SpeedControllerTileEntity) tile).getTargetSpeed();
            case MECHANICAL_BEARING ->
                    ((MechanicalBearingTileEntity) tile).getRotationMode().ordinal();
            case MECHANICAL_ROLLER ->
                    ((MechanicalRollerTileEntity) tile).getMode().ordinal();
            case MECHANICAL_ARM -> ((MechanicalArmTileEntity) tile).getSelectionMode();
            case VALVE_HANDLE -> ((com.cogwork.cogworkbridge.content.kinetics.crank.ValveHandleTileEntity) tile).getTargetDegrees();
            case WINDMILL_BEARING -> ((com.cogwork.cogworkbridge.content.contraptions.bearing.WindmillBearingTileEntity) tile).isReversed() ? 1 : 0;
            case STEAM_ENGINE -> ((com.cogwork.cogworkbridge.content.kinetics.steam.SteamEngineTileEntity) tile).isReversed() ? 1 : 0;
            case MECHANICAL_PISTON -> ((com.cogwork.cogworkbridge.content.contraptions.piston.MechanicalPistonTileEntity) tile).getMovementMode();
            case CART_ASSEMBLER -> ((com.cogwork.cogworkbridge.content.contraptions.mounted.CartAssemblerTileEntity) tile).getMovementMode();
        };
    }

    public void write(TileEntity tile, int value) {
        switch (this) {
            case CREATIVE_MOTOR ->
                    ((CreativeMotorTileEntity) tile).setConfiguredSpeed(value);
            case SPEED_CONTROLLER ->
                    ((SpeedControllerTileEntity) tile).setTargetSpeed(value);
            case MECHANICAL_BEARING -> {
                MechanicalBearingTileEntity.RotationMode[] modes =
                        MechanicalBearingTileEntity.RotationMode.values();
                int index = Math.max(0, Math.min(modes.length - 1, value));
                ((MechanicalBearingTileEntity) tile).setRotationMode(modes[index]);
            }
            case MECHANICAL_ROLLER -> {
                MechanicalRollerTileEntity.RollingMode[] modes =
                        MechanicalRollerTileEntity.RollingMode.values();
                int index = Math.max(0, Math.min(modes.length - 1, value));
                ((MechanicalRollerTileEntity) tile).setMode(modes[index]);
            }
            case MECHANICAL_ARM -> ((MechanicalArmTileEntity) tile).setSelectionMode(value);
            case VALVE_HANDLE -> ((com.cogwork.cogworkbridge.content.kinetics.crank.ValveHandleTileEntity) tile).setTargetDegrees(value);
            case WINDMILL_BEARING -> ((com.cogwork.cogworkbridge.content.contraptions.bearing.WindmillBearingTileEntity) tile).setReversed(value != 0);
            case STEAM_ENGINE -> ((com.cogwork.cogworkbridge.content.kinetics.steam.SteamEngineTileEntity) tile).setReversed(value != 0);
            case MECHANICAL_PISTON -> ((com.cogwork.cogworkbridge.content.contraptions.piston.MechanicalPistonTileEntity) tile).setMovementMode(Math.max(0, Math.min(2, value)));
            case CART_ASSEMBLER -> ((com.cogwork.cogworkbridge.content.contraptions.mounted.CartAssemblerTileEntity) tile).setMovementMode(Math.max(0, Math.min(2, value)));
        }
    }

    public static KineticMachineConfig forTile(TileEntity tile) {
        if (tile instanceof com.cogwork.cogworkbridge.content.contraptions.mounted.CartAssemblerTileEntity) return CART_ASSEMBLER;
        if (tile instanceof com.cogwork.cogworkbridge.content.contraptions.piston.MechanicalPistonTileEntity) return MECHANICAL_PISTON;
        if (tile instanceof CreativeMotorTileEntity) {
            return CREATIVE_MOTOR;
        }
        if (tile instanceof SpeedControllerTileEntity) {
            return SPEED_CONTROLLER;
        }
        if (tile instanceof com.cogwork.cogworkbridge.content.kinetics.crank.ValveHandleTileEntity) return VALVE_HANDLE;
        if (tile instanceof com.cogwork.cogworkbridge.content.kinetics.steam.SteamEngineTileEntity) return STEAM_ENGINE;
        if (tile instanceof com.cogwork.cogworkbridge.content.contraptions.bearing.WindmillBearingTileEntity) return WINDMILL_BEARING;
        if (tile instanceof MechanicalBearingTileEntity) {
            return MECHANICAL_BEARING;
        }
        if (tile instanceof MechanicalRollerTileEntity) {
            return MECHANICAL_ROLLER;
        }
        if (tile instanceof MechanicalArmTileEntity) return MECHANICAL_ARM;
        return null;
    }

    public static boolean mayConfigure(EntityPlayerMP player, BlockPos pos) {
        return player.world.isBlockLoaded(pos)
                && player.getDistanceSq(pos.getX() + 0.5D,
                        pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64.0D
                && player.canPlayerEdit(pos, EnumFacing.UP,
                        player.getHeldItemMainhand());
    }
}
