package com.cogwork.cogworkbridge.compat.ponder;

import com.cogwork.cogworkbridge.kinetic.KineticTileEntity;
import com.cogwork.cogworkbridge.content.logistics.belt.BeltTileEntity;
import com.cogwork.cogworkbridge.content.logistics.factory.FactoryPanelTileEntity;
import com.cogwork.cogworkbridge.content.logistics.tunnel.BeltTunnelTileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraftforge.fluids.capability.*;
import net.minecraftforge.items.*;
import java.lang.reflect.*;
import java.util.*;
import static com.cogwork.cogworkbridge.compat.ponder.PonderValues.*;

/** Maps upstream behaviour objects to actual Cogwork tiles, without touching live gameplay worlds. */
final class PonderTile {
    final PonderProgram p;
    final TileEntity tile;
    final String view;
    final int slot;
    PonderTile(PonderProgram p, TileEntity tile) { this(p, tile, "", -1); }
    PonderTile(PonderProgram p, TileEntity tile, String view, int slot) { this.p = p; this.tile = tile; this.view = view; this.slot = slot; }
    private String alias(String name) {
        String type = tile.getClass().getSimpleName();
        if (type.equals("MechanicalArmTileEntity")) return switch (name) { case "heldItem" -> "held"; case "targetedPoint", "targetedPointIndex" -> "target"; default -> name; };
        if (type.equals("PackagerTileEntity") || type.equals("RepackagerTileEntity")) return switch (name) { case "heldBox" -> "box"; case "previouslyUnwrapped" -> "incomingVisual"; case "animationTicks" -> "animation"; case "animationInward" -> "animationInward"; default -> name; };
        if (type.equals("DeployerTileEntity") && name.equals("heldItem")) return "heldItem";
        if (name.equals("inputInv")) return "inventory";
        if (type.equals("ElevatorPulleyTileEntity") && (name.equals("offset") || name.equals("cableOffset"))) return "offset";
        if (name.equals("previousCableOffset")) return "previousOffset";
        if (name.equals("offset")) return "cableOffset";
        if (name.equals("angle")) return "angleDegrees";
        if (type.equals("BogeyTile") && name.equals("wheelAngle")) return "angle";
        if (name.equals("forceFlag")) return "trainPresent";
        return name;
    }
    private Field reflected(String name) {
        for (Class<?> type = tile.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(alias(name)); field.setAccessible(true); return field; }
            catch (NoSuchFieldException ignored) {}
        }
        return null;
    }
    boolean hasField(String name) { return reflected(name) != null; }
    Object field(String name) {
        if (view.equals("panel")) {
            FactoryPanelTileEntity panel = (FactoryPanelTileEntity) tile;
            return switch (name) {
                case "count" -> panel.target(slot); case "active" -> panel.active(slot); case "filter" -> panel.filter(slot);
                case "targetedBy", "targetedByLinks", "bulb" -> new PonderTile(p, tile, name, slot);
                default -> throw new IllegalArgumentException("Panel field " + name);
            };
        }
        if (name.equals("panels") && tile instanceof FactoryPanelTileEntity) return new PonderTile(p, tile, "panels", -1);
        if (name.equals("connections") && hasField(name)) return new PonderTile(p, tile, "connections", -1);
        if (name.equals("priceTag")) return new PonderTile(p, tile, "priceTag", -1);
        if (name.equals("manuallyAddedItems") && hasField("displayed")) return new PonderTile(p, tile, "displayed", -1);
        if (name.equals("inputInv")) return inventory();
        if (name.equals("filtering") || name.equals("priceTag") || name.equals("pressingBehaviour") || name.equals("goggles") && !hasField(name)) return new PonderTile(p, tile, name, -1);
        Field field = reflected(name);
        if (field == null) throw new IllegalArgumentException("Unmapped field " + tile.getClass().getSimpleName() + "." + name);
        try { return field.get(tile); } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }
    void assign(String name, Object value) {
        if (name.equals("forceFlag") && tile instanceof com.cogwork.cogworkbridge.content.logistics.port.PackagePortTileEntity) {
            p.mutate(tile, tag -> tag.setBoolean("TutorialFlag", truth(value))); return;
        }
        if (view.equals("panel")) { assignPanel(name, value); return; }
        if (view.equals("connection") && name.equals("arrowBendMode")) {
            List<List<FactoryPanelTileEntity.Connection>> inputs = (List<List<FactoryPanelTileEntity.Connection>>) new PonderTile(p, tile).field("inputs");
            List<FactoryPanelTileEntity.Connection> list = inputs.get(slot);
            for (int i = 0; i < list.size(); i++) { var old = list.get(i); list.set(i, new FactoryPanelTileEntity.Connection(old.pos(), old.slot(), old.amount(), (int) num(value))); }
            return;
        }
        if (name.equals("requestData") && value instanceof Map<?, ?> map && tile instanceof com.cogwork.cogworkbridge.content.logistics.table.TableClothTileEntity) {
            if (map.get("encodedRequest") instanceof List<?> list) p.mutate(tile, tag -> {
                NBTTagCompound shop = tag.getCompoundTag("Shop");
                shop.setTag("Items", com.cogwork.cogworkbridge.content.logistics.packager.StockOrder.write(
                        (List<com.cogwork.cogworkbridge.content.logistics.packager.StockOrder.Line>) list));
                shop.setBoolean("Valid", truth(map.get("isValid"))); tag.setTag("Shop", shop);
            });
            return;
        }
        if (name.equals("requestData") && value instanceof Map<?, ?> map && tile instanceof com.cogwork.cogworkbridge.content.logistics.packager.RedstoneRequesterTileEntity requester) {
            Object order = map.get("encodedRequest");
            if (order instanceof List<?> list) requester.configure((List<com.cogwork.cogworkbridge.content.logistics.packager.StockOrder.Line>) list, "");
            return;
        }
        if (view.equals("priceTag") && name.equals("count")) {
            if (hasField("priceCount")) new PonderTile(p, tile).assign("priceCount", value);
            else throw new IllegalArgumentException("No price quantity");
            return;
        }
        if (name.equals("scriptedResult") && tile instanceof com.cogwork.cogworkbridge.content.processing.crafter.MechanicalCrafterTileEntity crafter) { crafter.setTutorialResult(stack(value)); return; }
        Field field = reflected(name);
        if (field == null) throw new IllegalArgumentException("Unmapped assignment " + tile.getClass().getSimpleName() + "." + name);
        try { field.set(tile, p.convert(value, field.getType())); } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
    Object call(String method, Object... args) {
        if (view.equals("displayed") && method.equals("add")) {
            Object value = args[0];
            if (value instanceof List<?> attached) value = attached.get(attached.size() - 1);
            ((List<ItemStack>) field("displayed")).add(stack(value).copy());
            return true;
        }
        if (view.equals("connections")) {
            Map<BlockPos, ItemStack> connections = (Map<BlockPos, ItemStack>) reflected("connections").getType().cast(readField("connections"));
            BlockPos target = tile.getPos().add(pos(args[0]));
            if (method.equals("add")) { connections.put(target, ItemStack.EMPTY); return true; }
            if (method.equals("remove")) return connections.remove(target);
        }
        if (view.equals("panels") && method.equals("get")) {
            int index = switch (tail(String.valueOf(args[0])).toLowerCase(Locale.ROOT)) { case "top_left" -> 0; case "top_right" -> 1; case "bottom_left" -> 2; default -> 3; };
            return new PonderTile(p, tile, "panel", index);
        }
        if (view.equals("panel")) return panelCall(method, args);
        if (view.equals("targetedBy") || view.equals("targetedByLinks")) {
            if (method.equals("get")) return new PonderTile(p, tile, "connection", slot);
        }
        if (method.equals("getLevel") || method.equals("getWorld")) return tile.getWorld();
        if (method.equals("getBlockPos") || method.equals("getPos")) return tile.getPos();
        if (method.equals("getInventory")) return inventory();
        if (method.equals("getCapability") && tile instanceof com.cogwork.cogworkbridge.content.processing.crafter.MechanicalCrafterTileEntity) return inventory();
        if (method.equals("getTankInventory") || method.equals("getPrimaryHandler")) return fluids();
        if (method.equals("getBehaviour") || method.equals("getPressingBehaviour")) return new PonderTile(p, tile, method, -1);
        if (method.equals("boxTransfer")) { assign("animatedPackage", args[0]); assign("animationTicks", 10); assign("sending", args.length > 1 && truth(args[1])); return null; }
        if (view.equals("bulb") && method.equals("setValue")) { new PonderTile(p, tile, "panel", slot).assignPanel("satisfied", num(args[0]) > 0); return null; }
        if (view.equals("getBehaviour") && method.equals("setValue")) { assign("mode", args[0]); return null; }
        if (method.equals("getCapability")) return String.valueOf(args[0]).toLowerCase(Locale.ROOT).contains("fluid") ? fluids() : inventory();
        if (method.equals("setFilter")) {
            if (view.equals("priceTag")) { new PonderTile(p, tile).assign("price", stack(args[args.length - 1]).copy()); return null; }
            if (tile instanceof com.cogwork.cogworkbridge.content.logistics.table.TableClothTileEntity) { assign("price", stack(args[args.length - 1]).copy()); return null; }
            if (tile instanceof com.cogwork.cogworkbridge.content.trains.track.RailwayTargetTile.Observer) { assign("filter", stack(args[args.length - 1]).copy()); return null; }
            if (tile instanceof com.cogwork.cogworkbridge.content.contraptions.actors.roller.MechanicalRollerTileEntity) { assign("material", stack(args[args.length - 1]).copy()); return null; }
            if (tile instanceof com.cogwork.cogworkbridge.content.kinetics.saw.MechanicalSawTileEntity) {
                tile.getTileData().setTag("TutorialFilter", stack(args[args.length - 1]).serializeNBT()); return null;
            }
            if (tile instanceof com.cogwork.cogworkbridge.content.logistics.tunnel.BrassTunnelTileEntity tunnel) { tunnel.setFilter(direction(args[0]), stack(args[1])); return null; }
            if (tile instanceof com.cogwork.cogworkbridge.content.redstone.DetectorTileEntity detector) { detector.applyClipboardFilter(stack(args[args.length - 1])); return null; }
            if (tile instanceof com.cogwork.cogworkbridge.content.fluids.SmartFluidPipeTileEntity pipe) { pipe.setFilterItem(stack(args[args.length - 1])); return null; }
            return p.invoke(tile, "setFilter", args[args.length - 1]);
        }
        if (method.equals("allowInsertion")) {
            // Demonstration input has already been isolated; capability insertion is always permitted here.
            return this;
        }
        if (method.equals("startProcessingBasin")) { assign("running", true); assign("runningTicks", 0); return null; }
        if (method.equals("start") && tile instanceof com.cogwork.cogworkbridge.content.processing.press.MechanicalPressTileEntity) { assign("running", true); assign("progress", 0); return null; }
        if (method.equals("setAnimatedSpeed")) {
            if (tile instanceof KineticTileEntity) p.mutate(tile, tag -> tag.setFloat("Speed", (float) num(args[0])));
            else if (tile instanceof com.cogwork.cogworkbridge.content.contraptions.actors.harvester.MechanicalHarvesterTileEntity
                    || tile instanceof com.cogwork.cogworkbridge.content.contraptions.actors.roller.MechanicalRollerTileEntity)
                p.mutate(tile, tag -> tag.setFloat("TutorialAnimatedSpeed", (float) num(args[0])));
            else if (hasField("animationSpeed")) assign("animationSpeed", args[0]);
            else throw new IllegalArgumentException("No animated shaft for " + tile.getClass().getSimpleName());
            return null;
        }
        if (method.equals("makePressingParticleEffect") || method.equals("makeCompactingParticleEffect")) {
            tile.getWorld().spawnParticle(net.minecraft.util.EnumParticleTypes.CLOUD, tile.getPos().getX() + .5, tile.getPos().getY() - .3, tile.getPos().getZ() + .5, 0, .02, 0); return null;
        }
        if (method.equals("flap")) { flap(args.length > 0 && truth(args[0])); return null; }
        if (method.equals("pulse")) {
            if (tile instanceof com.cogwork.cogworkbridge.content.display.DisplayLinkTileEntity) { assign("observedPower", true); tile.getWorld().markBlockRangeForRenderUpdate(tile.getPos(), tile.getPos()); return null; }
            if (hasField("flashTicks")) assign("flashTicks", 10);
            else if (hasField("pulseTicks")) assign("pulseTicks", 10);
            else if (hasField("animation")) assign("animation", 1);
            else throw new IllegalArgumentException("No link pulse animation");
            return null;
        }
        if (method.equals("startAnimation") && hasField("animationInward")) {
            assign("animationInward", args[0]); assign("animation", 20);
            if (args.length > 1) assign("incomingVisual", args[1]);
            return null;
        }
        if (method.equals("applyMaterialIfValid")) {
            Object material = args[0] instanceof net.minecraft.block.state.IBlockState ? args[0] : net.minecraft.block.Block.getBlockFromItem(stack(args[0]).getItem()).getStateFromMeta(stack(args[0]).getMetadata());
            if (tile instanceof com.cogwork.cogworkbridge.content.kinetics.waterwheel.WaterWheelTileEntity
                    || tile instanceof com.cogwork.cogworkbridge.content.kinetics.waterwheel.LargeWaterWheelTileEntity) {
                p.mutate(tile, tag -> {
                    tag.setTag("Material", NBTUtil.writeBlockState(new NBTTagCompound(), (net.minecraft.block.state.IBlockState) material));
                    PonderData.waterWheelMaterial(tag);
                });
                return true;
            }
            if (hasField("material")) assign("material", material);
            else p.mutate(tile, tag -> tag.setTag("Material", NBTUtil.writeBlockState(new NBTTagCompound(), (net.minecraft.block.state.IBlockState) material)));
            return true;
        }
        if (method.equals("onSpeedChanged")) {
            if (hasField("updateConnectivity")) assign("updateConnectivity", false);
            // The scene's flow instruction recomputes pressure using the real network rules.
            return null;
        }
        if (method.equals("activateDeferred")) { assign("lid", 0); assign("previousLid", 0); assign("triggered", true); return null; }
        if (method.equals("checkCompletedRecipe")) {
            if (tile instanceof com.cogwork.cogworkbridge.content.processing.crafter.MechanicalCrafterTileEntity crafter) crafter.startTutorialRecipe();
            return null;
        }
        if (method.equals("ejectWholeGrid")) {
            if (tile instanceof com.cogwork.cogworkbridge.content.processing.crafter.MechanicalCrafterTileEntity crafter) crafter.clearTutorialRecipe();
            else clearItems();
            return null;
        }
        return p.invoke(tile, method, args);
    }
    private void assignPanel(String name, Object value) {
        FactoryPanelTileEntity panel = (FactoryPanelTileEntity) tile;
        if (name.equals("active")) { if (truth(value)) panel.add(slot, new UUID(0, 1)); else array("network", slot, null); return; }
        if (!panel.active(slot)) panel.add(slot, new UUID(0, 1));
        switch (name) {
            case "count" -> array("target", slot, (int) num(value));
            case "satisfied" -> array("current", slot, truth(value) ? panel.target(slot) : 0);
            case "promised" -> array("promised", slot, (int) num(value));
            case "redstonePowered" -> array("current", slot, truth(value) ? panel.target(slot) : 0);
            case "passive" -> { if (truth(value)) array("target", slot, 0); }
            default -> throw new IllegalArgumentException("Factory panel assignment " + name);
        }
    }
    private Object panelCall(String method, Object[] args) {
        FactoryPanelTileEntity panel = (FactoryPanelTileEntity) tile;
        if (!panel.active(slot)) panel.add(slot, new UUID(0, 1));
        return switch (method) {
            case "setFilter" -> { panel.setFilter(slot, stack(args[args.length - 1])); yield null; }
            case "disconnectAll" -> { panel.clearConnections(slot); yield null; }
            case "addConnection" -> {
                Construct position = (Construct) args[0];
                List<List<FactoryPanelTileEntity.Connection>> inputs = (List<List<FactoryPanelTileEntity.Connection>>) new PonderTile(p, tile).field("inputs");
                BlockPos source = pos(position.args()[0]); int sourceSlot = panelSlot(position.args()[1]);
                inputs.get(slot).removeIf(connection -> connection.pos().equals(source) && connection.slot() == sourceSlot);
                inputs.get(slot).add(new FactoryPanelTileEntity.Connection(source, sourceSlot, 1, -1));
                yield null;
            }
            default -> throw new IllegalArgumentException("Factory panel call " + method);
        };
    }
    private void array(String field, int index, Object value) {
        Object array = readField(field);
        Array.set(array, index, value);
    }
    private Object readField(String name) {
        Field field = reflected(name);
        if (field == null) throw new IllegalArgumentException("No field " + name);
        try { return field.get(tile); } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }
    private static int panelSlot(Object symbol) { return switch (tail(String.valueOf(symbol)).toLowerCase(Locale.ROOT)) { case "top_left" -> 0; case "top_right" -> 1; case "bottom_left" -> 2; default -> 3; }; }
    Object inventory() {
        if (tile instanceof com.cogwork.cogworkbridge.content.processing.crafter.MechanicalCrafterTileEntity crafter) return crafter.tutorialInventory();
        if (tile instanceof IItemHandler handler) return handler;
        if (tile instanceof com.cogwork.cogworkbridge.content.processing.basin.BasinTileEntity basin) return basin.getInputItems();
        IItemHandler handler = tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        if (handler == null && hasField("inventory")) return field("inventory");
        if (handler == null) throw new IllegalArgumentException("No item handler for " + tile.getClass().getSimpleName());
        return handler;
    }
    Object fluids() {
        IFluidHandler handler = tile.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, null);
        if (handler == null) throw new IllegalArgumentException("No fluid handler for " + tile.getClass().getSimpleName());
        return handler;
    }
    void insert(ItemStack stack, EnumFacing insertion) {
        if (tile instanceof BeltTileEntity) { assign("carried", stack.copy()); assign("position", .5F); assign("previousPosition", .5F); return; }
        if (hasField("presented")) { assign("presented", stack.copy()); return; }
        if (hasField("heldItem") && tile.getClass().getSimpleName().contains("Drain")) { assign("heldItem", stack.copy()); assign("travelDirection", insertion.getOpposite()); return; }
        IItemHandler handler = (IItemHandler) inventory();
        ItemHandlerHelper.insertItemStacked(handler, stack.copy(), false);
    }
    ItemStack insertFromBelt(ItemStack stack, EnumFacing insertion) {
        if (tile instanceof com.cogwork.cogworkbridge.content.logistics.funnel.FunnelTileEntity funnel) {
            if (funnel.isPowered()) return stack;
            EnumFacing face = tile.getWorld().getBlockState(tile.getPos()).getValue(
                    com.cogwork.cogworkbridge.content.logistics.funnel.FunnelBlock.FACING);
            TileEntity attached = tile.getWorld().getTileEntity(tile.getPos().offset(face.getOpposite()));
            if (attached == null) return stack;
            ItemStack remaining = new PonderTile(p, attached).insertFromBelt(stack, face);
            if (remaining.getCount() < stack.getCount()) flap(true);
            return remaining;
        }
        IItemHandler handler = tile instanceof com.cogwork.cogworkbridge.content.processing.crafter.MechanicalCrafterTileEntity crafter
                ? crafter.tutorialInventory() : tile instanceof IItemHandler inventory ? inventory
                : tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, insertion);
        return handler == null ? stack : ItemHandlerHelper.insertItemStacked(handler, stack.copy(), false);
    }
    void clearItems() {
        if (tile instanceof BeltTileEntity belt) belt.clearTutorialItems();
        if (hasField("carried")) { assign("carried", ItemStack.EMPTY); return; }
        if (hasField("presented")) { assign("presented", ItemStack.EMPTY); return; }
        IItemHandler handler = (IItemHandler) inventory();
        for (int i = 0; i < handler.getSlots(); i++) handler.extractItem(i, Integer.MAX_VALUE, false);
    }
    void flap(boolean outward) {
        if (tile instanceof BeltTunnelTileEntity tunnel) { for (EnumFacing side : EnumFacing.HORIZONTALS) tunnel.flap(side); return; }
        if (hasField("previousFlap")) assign("previousFlap", 0);
        assign("flap", outward ? 1F : -1F);
    }
    void cycle(String property) {
        if (property.equals("pointing") && hasField("pointing")) {
            Enum<?> value = (Enum<?>) field("pointing"); Object[] values = value.getDeclaringClass().getEnumConstants();
            String next = ((Enum<?>) values[(value.ordinal() + 1) % values.length]).name().toLowerCase(Locale.ROOT);
            PonderData.visualProperty(p, tile, property, PonderData.crafterPointing(next).getName());
        } else if (property.equals("state") && hasField("activeStep")) assign("activeStep", ((int) num(field("activeStep")) + 2) % 6 - 1);
        else if (property.equals("top_shaft") && hasField("openFaces")) {
            EnumFacing.Axis axis = tile.getWorld().getBlockState(tile.getPos()).getValue(com.cogwork.cogworkbridge.kinetic.KineticBlock.AXIS);
            EnumFacing side = EnumFacing.getFacingFromAxis(EnumFacing.AxisDirection.POSITIVE, axis);
            assign("openFaces", (int) num(field("openFaces")) ^ 1 << side.getIndex());
        } else if (property.equals("size") && hasField("size")) assign("size", ((int) num(field("size")) + 1) % 3);
        else if (property.equals("heat_level")) {
            String[] levels = {"none", "smouldering", "fading", "kindled", "seething"};
            int index = Arrays.asList(levels).indexOf(String.valueOf(PonderData.visualValue(tile, property)));
            PonderData.visualProperty(p, tile, property, levels[(index + 1) % levels.length]);
        } else if (property.equals("level") && tile instanceof com.cogwork.cogworkbridge.content.redstone.DetectorTileEntity) {
            PonderData.visualProperty(p, tile, property, ((int) num(field("amount")) + 1) % 6);
        } else if (property.equals("shape") && tile instanceof com.cogwork.cogworkbridge.content.fluids.whistle.WhistleExtensionTileEntity) assign("doubled", !truth(field("doubled")));
        else if (property.equals("type") && hasField("crossSignal")) assign("crossSignal", !truth(field("crossSignal")));
        else if (property.equals("casing") && tile instanceof BeltTileEntity belt) assign("casing", belt.casing() == BeltTileEntity.CasingType.NONE ? BeltTileEntity.CasingType.ANDESITE : BeltTileEntity.CasingType.NONE);
        else if (property.startsWith("sticky_") || Set.of("powered", "powering", "receiver", "extended", "extracting", "enabled", "assembling", "open", "visible", "linked", "has_be", "calling").contains(property))
            PonderData.visualProperty(p, tile, property, !truth(PonderData.visualValue(tile, property)));
        else if (property.equals("axis_along_first_coordinate") || property.equals("axis_along_first")) {
            if (tile.getBlockType() instanceof com.cogwork.cogworkbridge.content.kinetics.saw.MechanicalSawBlock) {
                var state = tile.getWorld().getBlockState(tile.getPos());
                var axis = com.cogwork.cogworkbridge.content.kinetics.saw.MechanicalSawBlock.SHAFT_AXIS;
                var next = state.withProperty(axis, state.getValue(axis) == EnumFacing.Axis.X ? EnumFacing.Axis.Z : EnumFacing.Axis.X);
                tile.getWorld().setBlockState(tile.getPos(), next, 3);
                var current = tile.getWorld().getTileEntity(tile.getPos());
                if (current != null) p.mutate(current, tag -> tag.setByte("KineticAxis", (byte)
                        ((com.cogwork.cogworkbridge.kinetic.KineticBlock) next.getBlock()).getAxis(next).ordinal()));
            } else if (hasField("axisAlongFirst")) assign("axisAlongFirst", !truth(field("axisAlongFirst")));
            else throw new IllegalArgumentException("No axis selector");
        } else if (hasField(property)) { Object value = field(property); assign(property, value instanceof Boolean ? !truth(value) : num(value) + 1); }
        else throw new IllegalArgumentException("Unmapped cycle property " + property);
    }
    void signal(String value) {
        if (hasField("signalState")) assign("signalState", value.equalsIgnoreCase("red") ? 1 : value.equalsIgnoreCase("green") ? 2 : 0);
        else if (hasField("red")) assign("red", value.equalsIgnoreCase("red"));
        else if (hasField("signal")) assign("signal", value);
        else throw new IllegalArgumentException("No signal state");
    }
    static int ordinal(String name) {
        String last = tail(name).toLowerCase(Locale.ROOT);
        if (name.contains("SelectionMode")) return List.of("prefer_nearest", "randomize", "round_robin", "forced_round_robin", "split", "forced_split", "synchronize").indexOf(last);
        if (last.startsWith("search_") || last.startsWith("move_") || last.startsWith("moving_")) return switch (last) { case "search_inputs", "search_input" -> 0; case "move_to_input", "move_input", "moving_input" -> 1; case "search_outputs", "search_output" -> 2; default -> 3; };
        int color = Arrays.asList(PonderData.COLORS).indexOf(last);
        if (color >= 0) return color;
        try { return EnumFacing.valueOf(last.toUpperCase(Locale.ROOT)).ordinal(); } catch (IllegalArgumentException e) { throw new IllegalArgumentException("Unknown enum ordinal " + name); }
    }
    static void methodReference(PonderProgram p, TileEntity tile, Construct reference) {
        com.google.gson.JsonObject json = (com.google.gson.JsonObject) reference.args()[0];
        String method = json.getAsJsonObject("method").get("member").getAsString();
        new PonderTile(p, tile).call(method);
    }
    static void connectCrafters(PonderProgram p, BlockPos first, BlockPos second) {
        TileEntity tile = p.scene.getScene().getWorld().getTileEntity(first);
        TileEntity other = p.scene.getScene().getWorld().getTileEntity(second);
        if (tile instanceof com.cogwork.cogworkbridge.content.processing.crafter.MechanicalCrafterTileEntity crafter
                && other instanceof com.cogwork.cogworkbridge.content.processing.crafter.MechanicalCrafterTileEntity connected) crafter.linkTutorialInputs(connected);
    }
    static void conductor(net.createmod.ponder.api.element.ParrotElement element, boolean conductor) {
        if (element instanceof PonderParrot parrot) parrot.setConductor(conductor);
        else throw new IllegalArgumentException("Unexpected parrot implementation");
    }
}
