package com.cogwork.cogworkbridge.content.fluids;

import com.cogwork.cogworkbridge.CogworkBridge;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;

import javax.annotation.Nullable;

public final class ListFilterGuiHandler implements IGuiHandler {
    public static final int FILTER_GUI = 1;
    public static final int ATTRIBUTE_GUI = 2;
    public static final int BRASS_FUNNEL_GUI = 3;
    public static final int LINKED_CONTROLLER_GUI = 4;
    public static final int PACKAGE_GUI = 5;
    public static final int STOCK_TICKER_GUI = 6;
    public static final int PACKAGE_PORT_GUI = 7;
    public static final int REDSTONE_REQUESTER_GUI = 8;
    public static final int BLUEPRINT_GUI = 9;
    public static final int TOOLBOX_GUI = 10;
    public static final int SCHEMATIC_TABLE_GUI = 11;
    public static final int SCHEMATICANNON_GUI = 12;

    @Nullable
    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id == SCHEMATIC_TABLE_GUI || id == SCHEMATICANNON_GUI) {
            BlockPos pos = new BlockPos(x, y, z);
            if (world.getTileEntity(pos) instanceof com.cogwork.cogworkbridge.content.schematics.SchematicInventoryTile tile
                    && (id == SCHEMATICANNON_GUI) == (tile instanceof com.cogwork.cogworkbridge.content.schematics.SchematicannonTile)
                    && player.getDistanceSq(pos) <= 64D)
                return new com.cogwork.cogworkbridge.content.schematics.SchematicContainer(player.inventory, world, pos, tile);
            return null;
        }
        if (id == TOOLBOX_GUI) {
            BlockPos pos = new BlockPos(x, y, z);
            return world.getTileEntity(pos) instanceof com.cogwork.cogworkbridge.content.equipment.ToolboxTileEntity toolbox && player.getDistanceSq(pos) <= 64D
                    ? new com.cogwork.cogworkbridge.content.equipment.ToolboxContainer(player.inventory, toolbox, player) : null;
        }
        if (id == BLUEPRINT_GUI)
            return world.getEntityByID(x) instanceof com.cogwork.cogworkbridge.content.equipment.BlueprintEntity blueprint && player.getDistanceSq(blueprint) <= 64D
                    && y >= 0 && y < blueprint.size() * blueprint.size()
                    ? new com.cogwork.cogworkbridge.content.equipment.BlueprintContainer(player.inventory, blueprint, y) : null;
        if (id == REDSTONE_REQUESTER_GUI) {
            BlockPos pos = new BlockPos(x, y, z);
            return player.getDistanceSq(pos) <= 64 && world.getTileEntity(pos) instanceof com.cogwork.cogworkbridge.content.logistics.packager.RedstoneRequesterTileEntity requester
                    ? new com.cogwork.cogworkbridge.content.logistics.packager.RedstoneRequesterContainer(player.inventory, world, pos, requester) : null;
        }
        if (id == PACKAGE_PORT_GUI) {
            BlockPos pos = new BlockPos(x, y, z);
            return player.getDistanceSq(pos) <= 64 && world.getTileEntity(pos) instanceof com.cogwork.cogworkbridge.content.logistics.port.PackagePortTileEntity port
                    ? new com.cogwork.cogworkbridge.content.logistics.port.PackagePortContainer(player.inventory, world, pos, port) : null;
        }
        if (id == STOCK_TICKER_GUI) {
            BlockPos pos = new BlockPos(x, y, z);
            return (world.getTileEntity(pos) instanceof com.cogwork.cogworkbridge.content.logistics.packager.StockTickerTileEntity
                    || world.getTileEntity(pos) instanceof com.cogwork.cogworkbridge.content.logistics.packager.RedstoneRequesterTileEntity)
                    && player.getDistanceSq(pos) <= 64 ? new com.cogwork.cogworkbridge.content.logistics.packager.StockTickerContainer(world, pos) : null;
        }
        if (id == PACKAGE_GUI && x == player.inventory.currentItem && player.getHeldItemMainhand().getItem() instanceof com.cogwork.cogworkbridge.content.logistics.packagebox.PackageFilterItem)
            return new com.cogwork.cogworkbridge.content.logistics.packagebox.PackageFilterContainer(player.inventory);
        if (id == LINKED_CONTROLLER_GUI && x == player.inventory.currentItem
                && player.getHeldItemMainhand().getItem() instanceof com.cogwork.cogworkbridge.content.redstone.LinkedControllerItem)
            return new com.cogwork.cogworkbridge.content.redstone.LinkedControllerContainer(player.inventory);
        if (id == BRASS_FUNNEL_GUI) {
            BlockPos pos = new BlockPos(x, y, z);
            return world.getTileEntity(pos) instanceof com.cogwork.cogworkbridge.content.logistics.funnel.BrassFunnelTileEntity
                    && player.getDistanceSq(pos) <= 64
                    ? new com.cogwork.cogworkbridge.content.logistics.funnel.BrassFunnelConfigContainer(world, pos)
                    : null;
        }
        if (id == ATTRIBUTE_GUI && x == player.inventory.currentItem
                && player.getHeldItemMainhand().getItem() instanceof AttributeFilterItem)
            return new AttributeFilterContainer(player.inventory);
        if (id != FILTER_GUI || x != player.inventory.currentItem
                || !(player.getHeldItemMainhand().getItem() instanceof ListFilterItem)) return null;
        return new ListFilterContainer(player.inventory);
    }

    @Nullable
    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id == SCHEMATIC_TABLE_GUI || id == SCHEMATICANNON_GUI)
            return CogworkBridge.proxy.createSchematicScreen(player.inventory, world, new BlockPos(x, y, z), id == SCHEMATICANNON_GUI);
        if (id == TOOLBOX_GUI) return CogworkBridge.proxy.createToolboxScreen(player.inventory, world, new BlockPos(x, y, z));
        if (id == BLUEPRINT_GUI) return CogworkBridge.proxy.createBlueprintScreen(player.inventory, world, x, y);
        if (id == REDSTONE_REQUESTER_GUI) return CogworkBridge.proxy.createRedstoneRequesterScreen(player.inventory, world, new BlockPos(x, y, z));
        if (id == PACKAGE_PORT_GUI) return CogworkBridge.proxy.createPackagePortScreen(player.inventory, world, new BlockPos(x, y, z));
        if (id == STOCK_TICKER_GUI) return CogworkBridge.proxy.createStockTickerScreen(world, new BlockPos(x, y, z));
        if (id == PACKAGE_GUI && x == player.inventory.currentItem && player.getHeldItemMainhand().getItem() instanceof com.cogwork.cogworkbridge.content.logistics.packagebox.PackageFilterItem)
            return CogworkBridge.proxy.createPackageFilterScreen(player.inventory);
        if (id == LINKED_CONTROLLER_GUI && x == player.inventory.currentItem
                && player.getHeldItemMainhand().getItem() instanceof com.cogwork.cogworkbridge.content.redstone.LinkedControllerItem)
            return CogworkBridge.proxy.createLinkedControllerScreen(player.inventory);
        if (id == BRASS_FUNNEL_GUI)
            return CogworkBridge.proxy.createFunnelConfigScreen(new BlockPos(x, y, z));
        if (id == ATTRIBUTE_GUI && x == player.inventory.currentItem
                && player.getHeldItemMainhand().getItem() instanceof AttributeFilterItem)
            return CogworkBridge.proxy.createAttributeFilterScreen(player.inventory);
        if (id != FILTER_GUI || x != player.inventory.currentItem
                || !(player.getHeldItemMainhand().getItem() instanceof ListFilterItem)) return null;
        return CogworkBridge.proxy.createFilterScreen(player.inventory);
    }
}
