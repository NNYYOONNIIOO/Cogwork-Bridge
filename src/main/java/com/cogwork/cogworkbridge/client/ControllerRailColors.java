package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.contraptions.mounted.ControllerRailTileEntity;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.block.BlockRedstoneWire;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.client.event.ColorHandlerEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class ControllerRailColors {
    private ControllerRailColors() { }
    @SubscribeEvent public static void register(ColorHandlerEvent.Block event) {
        event.getBlockColors().registerBlockColorHandler((state, access, pos, tint) -> {
            if (tint != 0) return 0xffffff;
            TileEntity tile = access == null || pos == null ? null : access.getTileEntity(pos);
            int power = tile instanceof ControllerRailTileEntity rail ? rail.signalLevel() : 0;
            return BlockRedstoneWire.colorMultiplier(power);
        }, AllBlocks.CONTROLLER_RAIL);
    }
}
