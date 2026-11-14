package com.cogwork.cogworkbridge.compat.ponder;

import com.google.gson.*;
import net.createmod.ponder.api.scene.Selection;
import net.createmod.ponder.api.element.ParrotPose;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraftforge.fluids.*;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import java.util.*;

/** A bounded interpreter for scene data and captured upstream callbacks; never loads upstream classes. */
final class PonderValues {
    record Symbol(String value) {
        String last() { return value.substring(value.lastIndexOf('.') + 1); }
        @Override public String toString() { return last().toLowerCase(Locale.ROOT); }
    }
    record Construct(String name, Object[] args) {}
    record Transported(ItemStack stack) {}
    private static final class Returned extends RuntimeException {
        final Object value;
        Returned(Object value) { super(null, null, false, false); this.value = value; }
    }
    final PonderProgram program;
    private final Map<Integer, Object> objects = new HashMap<>();
    PonderValues(PonderProgram program) { this.program = program; }
    class Callback {
        final JsonObject json;
        Callback(JsonObject json) { this.json = json; }
        Object apply(Object... parameters) {
            Map<String, Object> scope = new HashMap<>();
            if (json.has("captures")) json.getAsJsonObject("captures").entrySet().forEach(e -> scope.put(e.getKey(), value(e.getValue())));
            JsonArray names = json.getAsJsonArray("params");
            for (int i = 0; i < names.size() && i < parameters.length; i++) scope.put(names.get(i).getAsString(), parameters[i]);
            try { return ast(json.get("body"), scope); } catch (Returned result) { return result.value; }
        }
    }
    Object[] arguments(JsonArray array) {
        Object[] result = new Object[array.size()];
        for (int i = 0; i < result.length; i++) result[i] = value(array.get(i));
        return result;
    }
    Object methodReference(Construct reference, Object... args) {
        JsonObject json = (JsonObject) reference.args[0];
        String method = json.getAsJsonObject("method").get("member").getAsString();
        JsonObject owner = json.getAsJsonObject("owner");
        String name = string(owner, "member", string(owner, "name", ""));
        if (method.equals("new")) return construct(name, args);
        if (args.length > 0) return call(args[0], method, Arrays.copyOfRange(args, 1, args.length));
        throw new IllegalArgumentException("Method reference without receiver");
    }
    Object value(JsonElement element) {
        if (element == null || element.isJsonNull()) return null;
        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isBoolean()) return primitive.getAsBoolean();
            if (primitive.isNumber()) return literal(primitive.getAsString());
            return primitive.getAsString();
        }
        if (element.isJsonArray()) return new ArrayList<>(Arrays.asList(arguments(element.getAsJsonArray())));
        JsonObject json = element.getAsJsonObject();
        String type = string(json, "type", "");
        JsonArray vector = json.has("v") && json.get("v").isJsonArray() ? json.getAsJsonArray("v") : null;
        return switch (type) {
            case "vec" -> new Vec3d(number(vector.get(0)), number(vector.get(1)), number(vector.get(2)));
            case "pos" -> new BlockPos(number(vector.get(0)), number(vector.get(1)), number(vector.get(2)));
            case "box" -> new AxisAlignedBB(number(vector.get(0)), number(vector.get(1)), number(vector.get(2)), number(vector.get(3)), number(vector.get(4)), number(vector.get(5)));
            case "enum", "symbol" -> symbol(string(json, "v", ""));
            case "selection" -> {
                Selection selection = program.util.select().position(0, 0, 0);
                selection = selection.substract(program.util.select().position(0, 0, 0));
                for (JsonElement pos : vector) { JsonArray p = pos.getAsJsonArray(); selection = selection.add(program.util.select().position(p.get(0).getAsInt(), p.get(1).getAsInt(), p.get(2).getAsInt())); }
                yield selection;
            }
            case "block", "state" -> {
                Map<String, Object> properties = new LinkedHashMap<>();
                if (json.has("properties")) json.getAsJsonObject("properties").entrySet().forEach(e -> properties.put(e.getKey(), value(e.getValue())));
                yield PonderData.state(json.get("id").getAsString(), properties);
            }
            case "item" -> {
                ItemStack stack = PonderData.item(json.get("id").getAsString());
                if (!stack.isEmpty()) {
                    stack.setCount(json.has("count") ? json.get("count").getAsInt() : 1);
                    if (json.has("components") && json.get("id").getAsString().endsWith(":clipboard")) {
                        NBTTagCompound data = new NBTTagCompound(); data.setBoolean("Written", true);
                        com.cogwork.cogworkbridge.content.equipment.clipboard.ClipboardData.toItem(stack, data);
                    }
                }
                yield stack;
            }
            case "link" -> program.links.get(json.get("id").getAsInt());
            case "lambda" -> new Callback(json);
            case "class" -> type(string(json, "v", ""));
            case "construct" -> {
                Integer id = json.has("objectId") ? json.get("objectId").getAsInt() : null;
                if (id != null && objects.containsKey(id)) yield objects.get(id);
                // Arguments can construct other cached objects; HashMap.computeIfAbsent forbids this recursion.
                Object result = construct(string(json, "className", ""), arguments(json.getAsJsonArray("args")));
                if (id != null) objects.put(id, result);
                yield result;
            }
            case "call" -> {
                Object result = call(value(json.get("target")), string(json, "name", ""), arguments(json.getAsJsonArray("args")));
                if (result instanceof ItemStack stack && json.has("address")) com.cogwork.cogworkbridge.content.logistics.packagebox.PackageItem.setAddress(stack, json.get("address").getAsString());
                yield result;
            }
            case "field" -> field(value(json.get("target")), string(json, "name", ""));
            case "binary" -> binary(string(json, "op", ""), value(json.get("a")), value(json.get("b")));
            case "unary" -> unary(string(json, "op", ""), value(json.get("v")));
            case "mutable" -> new Object[]{value(json.get("v"))};
            case "nbt" -> new NBTTagCompound();
            case "methodref" -> new Construct("methodref", new Object[]{json});
            case "api" -> switch (string(json, "v", "")) { case "scene" -> program.scene; case "world" -> program.scene.getScene().getWorld(); case "util" -> program.util; default -> new Symbol(string(json, "v", "")); };
            default -> throw new IllegalArgumentException("Unsupported scene value " + type);
        };
    }
    Object symbol(String value) {
        if (value.equals("PackagerBlockEntity.CYCLE")) return 20;
        if (value.equals("CommonComponents.EMPTY")) return "";
        if (Set.of("AllBlocks.DYED_VALVE_HANDLES", "AllBlocks.DYED_SAILS", "AllBlocks.NIXIE_TUBES",
                "AllBlocks.PACKAGE_POSTBOXES", "AllBlocks.TABLE_CLOTHS").contains(value)) return new Symbol(value);
        if (value.equals("Vec3.ZERO")) return Vec3d.ZERO;
        if (value.equals("ItemStack.EMPTY")) return ItemStack.EMPTY;
        if (value.startsWith("AllBlocks.") || value.startsWith("Blocks.")) return PonderData.state(
                (value.startsWith("All") ? "create:" : "minecraft:") + tail(value).toLowerCase(Locale.ROOT), Map.of());
        if (value.startsWith("AllItems.") || value.startsWith("Items.")) return PonderData.item(
                (value.startsWith("All") ? "create:" : "minecraft:") + tail(value).toLowerCase(Locale.ROOT));
        return new Symbol(value);
    }
    static Class<?> type(String name) {
        return switch (name) {
            case "ItemEntity" -> EntityItem.class;
            case "Entity" -> Entity.class;
            case "Blaze" -> net.minecraft.entity.monster.EntityBlaze.class;
            case "Sheep" -> net.minecraft.entity.passive.EntitySheep.class;
            case "SuperGlueEntity" -> com.cogwork.cogworkbridge.content.contraptions.glue.SuperGlueEntity.class;
            case "ParrotElement" -> net.createmod.ponder.api.element.ParrotElement.class;
            case "BeltBlockEntity" -> com.cogwork.cogworkbridge.content.logistics.belt.BeltTileEntity.class;
            case "NixieTubeBlockEntity" -> com.cogwork.cogworkbridge.content.display.NixieTubeTileEntity.class;
            case "DepotBlockEntity" -> com.cogwork.cogworkbridge.content.logistics.depot.DepotTileEntity.class;
            case "WaterWheelBlockEntity" -> com.cogwork.cogworkbridge.content.kinetics.waterwheel.WaterWheelTileEntity.class;
            case "AnalogLeverBlockEntity" -> com.cogwork.cogworkbridge.content.redstone.AnalogLeverTileEntity.class;
            case "StressGaugeBlockEntity" -> com.cogwork.cogworkbridge.content.kinetics.gauge.GaugeTileEntity.class;
            case "MillstoneBlockEntity" -> com.cogwork.cogworkbridge.content.processing.millstone.MillstoneTileEntity.class;
            case "MechanicalMixerBlockEntity" -> com.cogwork.cogworkbridge.content.processing.mixer.MechanicalMixerTileEntity.class;
            case "BasinBlockEntity" -> com.cogwork.cogworkbridge.content.processing.basin.BasinTileEntity.class;
            case "MechanicalPressBlockEntity" -> com.cogwork.cogworkbridge.content.processing.press.MechanicalPressTileEntity.class;
            case "DeployerBlockEntity" -> com.cogwork.cogworkbridge.content.kinetics.deployer.DeployerTileEntity.class;
            case "SpoutBlockEntity" -> com.cogwork.cogworkbridge.content.fluids.FluidSpoutTileEntity.class;
            case "EjectorBlockEntity" -> com.cogwork.cogworkbridge.content.logistics.ejector.EjectorTileEntity.class;
            case "MechanicalCrafterBlockEntity" -> com.cogwork.cogworkbridge.content.processing.crafter.MechanicalCrafterTileEntity.class;
            case "SmartChuteBlockEntity" -> com.cogwork.cogworkbridge.content.logistics.chute.SmartChuteTileEntity.class;
            case "FunnelBlockEntity" -> com.cogwork.cogworkbridge.content.logistics.funnel.FunnelTileEntity.class;
            case "BrassTunnelBlockEntity" -> com.cogwork.cogworkbridge.content.logistics.tunnel.BrassTunnelTileEntity.class;
            case "StickerBlockEntity" -> com.cogwork.cogworkbridge.content.contraptions.chassis.StickerTileEntity.class;
            case "HarvesterBlockEntity" -> com.cogwork.cogworkbridge.content.contraptions.actors.harvester.MechanicalHarvesterTileEntity.class;
            case "PortableItemInterfaceBlockEntity", "PortableStorageInterfaceBlockEntity" -> com.cogwork.cogworkbridge.content.logistics.portable.PortableStorageInterfaceTileEntity.class;
            case "SawBlockEntity" -> com.cogwork.cogworkbridge.content.kinetics.saw.MechanicalSawTileEntity.class;
            case "ContraptionControlsBlockEntity" -> com.cogwork.cogworkbridge.content.contraptions.controls.ContraptionControlsTileEntity.class;
            case "RollerBlockEntity" -> com.cogwork.cogworkbridge.content.contraptions.actors.roller.MechanicalRollerTileEntity.class;
            case "FluidTankBlockEntity" -> com.cogwork.cogworkbridge.content.fluids.FluidTankTileEntity.class;
            case "ItemDrainBlockEntity" -> com.cogwork.cogworkbridge.content.fluids.ItemDrainTileEntity.class;
            case "FluidValveBlockEntity" -> com.cogwork.cogworkbridge.content.fluids.FluidValveTileEntity.class;
            case "SmartFluidPipeBlockEntity" -> com.cogwork.cogworkbridge.content.fluids.SmartFluidPipeTileEntity.class;
            case "CreativeFluidTankBlockEntity" -> com.cogwork.cogworkbridge.content.fluids.CreativeFluidTankTileEntity.class;
            case "HosePulleyBlockEntity" -> com.cogwork.cogworkbridge.content.fluids.HosePulleyTileEntity.class;
            case "PortableFluidInterfaceBlockEntity" -> com.cogwork.cogworkbridge.content.fluids.PortableFluidInterfaceTileEntity.class;
            case "PulseExtenderBlockEntity", "PulseRepeaterBlockEntity", "PulseTimerBlockEntity" -> com.cogwork.cogworkbridge.content.redstone.PulseDiodeTileEntity.class;
            case "RedstoneLinkBlockEntity" -> com.cogwork.cogworkbridge.content.redstone.RedstoneLinkTileEntity.class;
            case "SmartObserverBlockEntity" -> com.cogwork.cogworkbridge.content.redstone.DetectorTileEntity.class;
            case "ChainConveyorBlockEntity" -> com.cogwork.cogworkbridge.content.kinetics.chainconveyor.ChainConveyorTileEntity.class;
            case "FrogportBlockEntity", "PostboxBlockEntity" -> com.cogwork.cogworkbridge.content.logistics.port.PackagePortTileEntity.class;
            case "PackagerBlockEntity" -> com.cogwork.cogworkbridge.content.logistics.packager.PackagerTileEntity.class;
            case "TableClothBlockEntity" -> com.cogwork.cogworkbridge.content.logistics.table.TableClothTileEntity.class;
            case "FactoryPanelBlockEntity" -> com.cogwork.cogworkbridge.content.logistics.factory.FactoryPanelTileEntity.class;
            case "TrackObserverBlockEntity" -> com.cogwork.cogworkbridge.content.trains.track.RailwayTargetTile.Observer.class;
            case "BlockEntity" -> TileEntity.class;
            default -> throw new IllegalArgumentException("Unmapped scene class " + name);
        };
    }
    Object ast(JsonElement element, Map<String, Object> env) {
        if (element == null || element.isJsonNull()) return null;
        if (element.isJsonArray()) { Object result = null; for (JsonElement statement : element.getAsJsonArray()) result = ast(statement, env); return result; }
        JsonObject json = element.getAsJsonObject();
        String node = string(json, "node", "");
        Object result = switch (node) {
            case "Literal" -> literal(string(json, "value", "null"));
            case "MemberReference" -> field(resolve(string(json, "qualifier", ""), env), string(json, "member", ""));
            case "MethodInvocation" -> call(resolve(string(json, "qualifier", ""), env), string(json, "member", ""), astArgs(json, env));
            case "ClassCreator" -> construct(json.getAsJsonObject("type").get("name").getAsString(), astArgs(json, env));
            case "BinaryOperation" -> {
                String op = string(json, "operator", "");
                Object a = ast(json.get("operandl"), env);
                yield op.equals("&&") && !truth(a) ? false : op.equals("||") && truth(a) ? true : binary(op, a, ast(json.get("operandr"), env));
            }
            case "Cast" -> {
                Object value = ast(json.get("expression"), env);
                String type = string(json.getAsJsonObject("type"), "name", "");
                if (value instanceof Number n && Set.of("byte", "short", "int", "long").contains(type)) yield n.longValue();
                if (value instanceof Number n && Set.of("float", "double").contains(type)) yield n.doubleValue();
                yield value;
            }
            case "TernaryExpression" -> ast(json.get(truth(ast(json.get("condition"), env)) ? "if_true" : "if_false"), env);
            case "StatementExpression" -> ast(json.get("expression"), env);
            case "BlockStatement" -> ast(json.get("statements"), env);
            case "IfStatement" -> ast(json.get(truth(ast(json.get("condition"), env)) ? "then_statement" : "else_statement"), env);
            case "ReturnStatement" -> throw new Returned(ast(json.get("expression"), env));
            case "LocalVariableDeclaration" -> {
                for (JsonElement d : json.getAsJsonArray("declarators")) { JsonObject declaration = d.getAsJsonObject(); env.put(declaration.get("name").getAsString(), ast(declaration.get("initializer"), env)); }
                yield null;
            }
            case "Assignment" -> {
                JsonObject left = json.getAsJsonObject("expressionl");
                Object value = ast(json.get("value"), env);
                String qualifier = string(left, "qualifier", ""), member = string(left, "member", ""), op = string(json, "type", "=");
                if (!op.equals("=")) value = binary(op.substring(0, op.length() - 1), ast(left, env), value);
                if (qualifier.isEmpty()) env.put(member, value); else assign(resolve(qualifier, env), member, value);
                yield value;
            }
            case "LambdaExpression" -> {
                JsonObject lambda = new JsonObject(); JsonArray names = new JsonArray();
                for (JsonElement param : json.getAsJsonArray("parameters")) names.add(string(param.getAsJsonObject(), "name", string(param.getAsJsonObject(), "member", "")));
                lambda.add("params", names); lambda.add("body", json.get("body")); lambda.add("captures", new JsonObject());
                yield new Callback(lambda) { @Override Object apply(Object... args) {
                    Map<String, Object> captured = new HashMap<>(env); for (int i = 0; i < args.length && i < names.size(); i++) captured.put(names.get(i).getAsString(), args[i]);
                    try { return ast(json.get("body"), captured); } catch (Returned r) { return r.value; }
                }};
            }
            default -> throw new IllegalArgumentException("Unsupported callback syntax " + node);
        };
        if (json.has("selectors")) for (JsonElement selector : json.getAsJsonArray("selectors")) {
            JsonObject next = selector.getAsJsonObject();
            result = string(next, "node", "").equals("MemberReference") ? field(result, string(next, "member", "")) : call(result, string(next, "member", ""), astArgs(next, env));
        }
        if (json.has("prefix_operators")) for (JsonElement prefix : json.getAsJsonArray("prefix_operators")) result = unary(prefix.getAsString(), result);
        return result;
    }
    Object[] astArgs(JsonObject json, Map<String, Object> env) {
        JsonArray args = json.has("arguments") ? json.getAsJsonArray("arguments") : new JsonArray();
        Object[] values = new Object[args.size()]; for (int i = 0; i < values.length; i++) values[i] = ast(args.get(i), env); return values;
    }
    Object resolve(String path, Map<String, Object> env) {
        if (path.isEmpty()) return env;
        String[] parts = path.split("\\.");
        Object value = env.get(parts[0]);
        if (value == null && !env.containsKey(parts[0])) value = switch (parts[0]) {
            case "scene", "builder" -> program.scene;
            case "util" -> program.util;
            default -> symbol(parts[0]);
        };
        for (int i = 1; i < parts.length; i++) value = field(value, parts[i]);
        return value;
    }
    Object field(Object object, String name) {
        if (object instanceof Map<?, ?> map) return map.get(name);
        if (object instanceof Symbol symbol) return symbol(symbol.value + "." + name);
        if (object instanceof Vec3d v) return switch (name) { case "x" -> v.x; case "y" -> v.y; case "z" -> v.z; default -> throw new IllegalArgumentException(name); };
        if (object instanceof PonderTile tile) return tile.field(name);
        if (object instanceof TileEntity tile) return new PonderTile(program, tile).field(name);
        if (object instanceof Entity e) return switch (name) { case "yRotO" -> e.prevRotationYaw; case "xRotO" -> e.prevRotationPitch; case "walkAnimation" -> new Construct("WalkAnimation", new Object[]{e}); default -> throw new IllegalArgumentException(name); };
        throw new IllegalArgumentException("Unsupported field " + object + "." + name);
    }
    void assign(Object target, String field, Object value) {
        if (target instanceof PonderTile tile) tile.assign(field, value);
        else if (target instanceof TileEntity tile) new PonderTile(program, tile).assign(field, value);
        else if (target instanceof Map map) map.put(field, value);
        else if (target instanceof Entity e) {
            switch (field) {
                case "yRotO" -> e.prevRotationYaw = (float) num(value);
                case "xo" -> e.prevPosX = num(value); case "yo" -> e.prevPosY = num(value); case "zo" -> e.prevPosZ = num(value);
                case "yHeadRotO" -> ((net.minecraft.entity.EntityLivingBase) e).prevRotationYawHead = (float) num(value);
                case "yHeadRot" -> ((net.minecraft.entity.EntityLivingBase) e).rotationYawHead = (float) num(value);
                case "box" -> ((EntityItem) e).setItem(stack(value));
                default -> throw new IllegalArgumentException("Entity assignment " + field);
            }
        }
        else throw new IllegalArgumentException("Unsupported assignment " + target + "." + field);
    }
    Object construct(String name, Object[] args) {
        return switch (name) {
            case "ItemStack" -> args[0] instanceof ItemStack stack ? copy(stack, args.length > 1 ? (int) num(args[1]) : 1)
                    : args[0] instanceof IBlockState state ? copy(PonderData.item(state.getBlock().getRegistryName().toString()), args.length > 1 ? (int) num(args[1]) : 1) : ItemStack.EMPTY;
            case "BlockPos", "Vec3i" -> args.length == 1 ? new BlockPos(vec(args[0])) : new BlockPos(num(args[0]), num(args[1]), num(args[2]));
            case "Vec3" -> new Vec3d(num(args[0]), num(args[1]), num(args[2]));
            case "AABB" -> args.length == 1 ? new AxisAlignedBB(pos(args[0])) : new AxisAlignedBB(vec(args[0]), vec(args[1]));
            case "CompoundTag" -> new NBTTagCompound();
            case "FluidStack" -> new FluidStack(fluid(args[0]), (int) num(args[1]));
            case "TransportedItemStack" -> new Transported(stack(args[0]));
            case "ArrayList", "LinkedList" -> new ArrayList<>();
            case "MutableObject" -> new Object[]{args.length == 0 ? null : args[0]};
            case "Mutable", "RequestData", "RedstoneRequesterBlockEntity" -> new HashMap<String, Object>();
            case "BigItemStack" -> new com.cogwork.cogworkbridge.content.logistics.packager.StockOrder.Line(stack(args[0]), args.length > 1 ? (int) num(args[1]) : stack(args[0]).getCount());
            case "FacePointOfInterestPose" -> new ParrotPose.FacePointOfInterestPose();
            case "FlappyPose" -> new ParrotPose.FlappyPose();
            case "DancePose" -> new ParrotPose.DancePose();
            case "FaceCursorPose" -> new ParrotPose.FaceCursorPose();
            case "PackageEntity" -> new com.cogwork.cogworkbridge.content.logistics.packagebox.PackageEntity(
                    program.scene.getScene().getWorld(), args.length > 3 ? num(args[1]) : 0, args.length > 3 ? num(args[2]) : 0, args.length > 3 ? num(args[3]) : 0,
                    args.length > 4 ? stack(args[4]) : PonderInstructions.parcel(new Object[0]));
            case "ChainConveyorParrotElement" -> new PonderParrot(vec(args[0]), () -> (ParrotPose) methodReference((Construct) args[1]), true);
            case "ElementLinkImpl" -> new net.createmod.ponder.foundation.element.ElementLinkImpl<>(net.createmod.ponder.api.element.ParrotElement.class);
            case "CreateParrotInstruction" -> new net.createmod.ponder.foundation.instruction.CreateParrotInstruction(
                    (int) num(args[0]), direction(args[1]), (net.createmod.ponder.api.element.ParrotElement) args[2]);
            default -> new Construct(name, args);
        };
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    Object call(Object target, String method, Object... args) {
        if (target instanceof PonderTile tile) return tile.call(method, args);
        if (target instanceof TileEntity tile) return new PonderTile(program, tile).call(method, args);
        if (target instanceof NBTTagCompound nbt) {
            if (method.startsWith("put") || method.startsWith("set")) {
                String key = String.valueOf(args[0]); Object value = args[1];
                switch (method) {
                    case "putInt", "setInteger" -> nbt.setInteger(key, (int) num(value));
                    case "putFloat", "setFloat" -> nbt.setFloat(key, (float) num(value));
                    case "putBoolean", "setBoolean" -> nbt.setBoolean(key, truth(value));
                    case "putString", "setString" -> nbt.setString(key, String.valueOf(value));
                    default -> nbt.setTag(key, toNbt(value));
                }
                return null;
            }
            return switch (method) { case "getInt" -> nbt.getInteger(String.valueOf(args[0])); case "getFloat" -> nbt.getFloat(String.valueOf(args[0])); case "getCompound" -> nbt.getCompoundTag(String.valueOf(args[0])); case "getString" -> nbt.getString(String.valueOf(args[0])); case "remove" -> { nbt.removeTag(String.valueOf(args[0])); yield null; } default -> throw new IllegalArgumentException("NBT." + method); };
        }
        if (target instanceof Callback callback && (method.equals("accept") || method.equals("apply") || method.equals("get"))) return callback.apply(args);
        if (target instanceof EnumFacing direction) {
            if (method.equals("getOpposite")) return direction.getOpposite();
            if (method.equals("getStepX")) return direction.getXOffset();
            if (method.equals("getStepY")) return direction.getYOffset();
            if (method.equals("getStepZ")) return direction.getZOffset();
            if (method.equals("getAxis")) return direction.getAxis();
        }
        if (target instanceof Object[] mutable) {
            if (method.equals("getValue") || method.equals("get")) return mutable[0];
            if (method.equals("setValue") || method.equals("set")) { mutable[0] = args[0]; return null; }
        }
        if (target instanceof ItemStack stack) return switch (method) {
            case "get", "asStack", "copy" -> stack.copy();
            case "copyWithCount" -> copy(stack, (int) num(args[0]));
            case "getHoverName" -> stack.getDisplayName();
            case "saveOptional", "save", "serializeNBT" -> stack.writeToNBT(new NBTTagCompound());
            case "setCount" -> { stack.setCount((int) num(args[0])); yield null; }
            case "getCount" -> stack.getCount();
            default -> throw new IllegalArgumentException("ItemStack." + method);
        };
        if (target instanceof Transported transported && method.equals("serializeNBT")) {
            NBTTagCompound nbt = new NBTTagCompound(); nbt.setTag("Item", transported.stack.writeToNBT(new NBTTagCompound())); return nbt;
        }
        if (target instanceof IBlockState state) {
            String property = args.length == 0 ? "" : tail(String.valueOf(args[0])).toLowerCase(Locale.ROOT);
            if (method.equals("setValue")) {
                if (program.editingPos != null) { TileEntity tile = program.scene.getScene().getWorld().getTileEntity(program.editingPos); if (tile != null) PonderData.visualProperty(program, tile, property, args[1]); }
                return PonderData.property(state, property, args[1]);
            }
            if (method.equals("cycle")) {
                for (IProperty p : state.getPropertyKeys()) if (p.getName().equals(property)) return state.cycleProperty(p);
                if (program.editingPos != null) { TileEntity tile = program.scene.getScene().getWorld().getTileEntity(program.editingPos); if (tile != null) new PonderTile(program, tile).cycle(property); }
                return state;
            }
            if (method.equals("getValue")) {
                for (IProperty p : state.getPropertyKeys()) if (p.getName().equals(property)) return state.getValue(p);
                return program.editingPos == null ? false : PonderData.visualValue(program.scene.getScene().getWorld().getTileEntity(program.editingPos), property);
            }
            if (method.equals("hasProperty")) {
                if (state.getPropertyKeys().stream().anyMatch(p -> p.getName().equals(property))) return true;
                TileEntity tile = program.editingPos == null ? null : program.scene.getScene().getWorld().getTileEntity(program.editingPos);
                return tile != null && tile.getTileData().getCompoundTag("TutorialState").hasKey(property);
            }
            if (Set.of("getDefaultState", "defaultBlockState", "get").contains(method)) return state;
            if (method.equals("asStack")) return PonderData.item(state.getBlock().getRegistryName().toString());
            if (method.equals("getShape")) return new AxisAlignedBB(BlockPos.ORIGIN);
            if (method.equals("getAxisState")) return PonderData.property(state, "axis", args[0]);
        }
        if (target instanceof Map map) {
            if (method.equals("get")) return map.get(args[0]);
            if (method.equals("toImmutable")) return new HashMap<>(map);
        }
        if (target instanceof Vec3d || target instanceof BlockPos) {
            Vec3d v = vec(target);
            boolean block = target instanceof BlockPos;
            Vec3d next;
            if (method.equals("getX")) return v.x;
            if (method.equals("getY")) return v.y;
            if (method.equals("getZ")) return v.z;
            if (method.equals("toImmutable")) return target;
            if (method.equals("scale")) next = v.scale(num(args[0]));
            else if (method.equals("normalize")) next = v.normalize();
            else if (method.equals("add") || method.equals("offset")) next = v.add(args.length == 1 ? vec(args[0]) : new Vec3d(num(args[0]), num(args[1]), num(args[2])));
            else if (method.equals("subtract")) next = v.subtract(vec(args[0]));
            else {
                EnumFacing facing = direction(method.equals("relative") ? args[0] : new Symbol(method.equals("above") ? "up" : method.equals("below") ? "down" : method));
                double distance = args.length > 1 ? num(args[1]) : args.length == 1 && !method.equals("relative") ? num(args[0]) : 1;
                next = v.add(facing.getXOffset() * distance, facing.getYOffset() * distance, facing.getZOffset() * distance);
            }
            return block ? new BlockPos(next) : next;
        }
        if (target instanceof AxisAlignedBB box) return switch (method) {
            case "bounds" -> box;
            case "move" -> args.length == 1 ? box.offset(vec(args[0])) : box.offset(num(args[0]), num(args[1]), num(args[2]));
            case "inflate" -> args.length == 1 ? box.grow(num(args[0])) : box.grow(num(args[0]), num(args[1]), num(args[2]));
            case "contract" -> box.contract(num(args[0]), num(args[1]), num(args[2]));
            default -> throw new IllegalArgumentException("Box." + method);
        };
        if (target instanceof Entity entity) {
            switch (method) {
                case "discard", "remove" -> entity.setDead();
                case "setDeltaMovement" -> { Vec3d motion = args.length == 1 ? vec(args[0]) : new Vec3d(num(args[0]), num(args[1]), num(args[2])); entity.motionX = motion.x; entity.motionY = motion.y; entity.motionZ = motion.z; }
                case "setPos", "setPosRaw" -> { Vec3d p = args.length == 1 ? vec(args[0]) : new Vec3d(num(args[0]), num(args[1]), num(args[2])); entity.setPosition(p.x, p.y, p.z); }
                case "setYRot" -> entity.rotationYaw = (float) num(args[0]);
                case "getYRot" -> { return entity.rotationYaw; }
                case "getY" -> { return entity.posY; }
                case "getX" -> { return entity.posX; }
                case "getZ" -> { return entity.posZ; }
                case "setItem" -> { if (entity instanceof EntityItem item) item.setItem(stack(args[0])); }
                case "setColor" -> { if (entity instanceof net.minecraft.entity.passive.EntitySheep sheep) sheep.setFleeceColor(net.minecraft.item.EnumDyeColor.valueOf(tail(String.valueOf(args[0])).toUpperCase(Locale.ROOT))); }
                default -> throw new IllegalArgumentException("Entity." + method);
            }
            return null;
        }
        if (target instanceof IItemHandler handler) {
            if (method.equals("insertItem")) return handler.insertItem((int) num(args[0]), stack(args[1]), truth(args[2]));
            if (method.equals("setStackInSlot") || method.equals("setItem")) {
                int slot = (int) num(args[0]); ItemStack stack = stack(args[1]).copy();
                if (handler instanceof IItemHandlerModifiable modifiable) modifiable.setStackInSlot(slot, stack);
                else { handler.extractItem(slot, Integer.MAX_VALUE, false); handler.insertItem(slot, stack, false); }
                return null;
            }
        }
        if (target instanceof IFluidHandler handler) {
            if (method.equals("fill")) return handler.fill((FluidStack) args[0], args.length < 2 || !String.valueOf(args[1]).equals("simulate"));
            if (method.equals("drain")) return handler.drain((int) num(args[0]), args.length < 2 || !String.valueOf(args[1]).equals("simulate"));
        }
        if (target instanceof Fluid fluid) {
            if (Set.of("get", "getSource", "getFluidType").contains(method)) return fluid;
            if (method.equals("getBucket")) return FluidUtil.getFilledBucket(new FluidStack(fluid, 1000));
        }
        if (target instanceof List list) {
            if (method.equals("add")) return list.add(args[0]);
            if (method.equals("remove")) return list.remove(args[0]);
            if (method.equals("get") || method.equals("getValue")) return list.get(args.length == 0 ? 1 : (int) num(args[0]));
            if (method.equals("size")) return list.size();
        }
        if (target instanceof Symbol symbol) {
            String owner = tail(symbol.value);
            if (symbol.value.startsWith("AllFluids.")) return fluid(new Symbol(owner));
            if (symbol.value.startsWith("AllBlocks.") && method.equals("get")) {
                String block = PonderData.colored(symbol.value, args[0]);
                return PonderData.state(block, Map.of());
            }
            if (symbol.value.equals("NeoForgeMod.MILK") && method.equals("get")) return fluid(new Symbol("milk"));
            if (owner.equals("FluidHelper") && method.equals("convertToStill")) return args[0];
            if (owner.equals("FluidHelper") && method.equals("copyStackWithAmount")) return new FluidStack(fluid(args[0]), (int) num(args[1]));
            if (owner.equals("FluidFX") && method.equals("getFluidParticle")) return new Construct("FluidParticle", args);
            if (owner.equals("RandomSource") && method.equals("create")) return new Random(0);
            if (owner.equals("VecHelper") && method.equals("offsetRandomly")) return args[0];
            if ((owner.equals("Vec3") || owner.equals("VecHelper")) && Set.of("atCenterOf", "getCenterOf", "atLowerCornerOf").contains(method)) return method.equals("atLowerCornerOf") ? vec(args[0]) : vec(args[0]).add(.5, .5, .5);
            if (owner.equals("PackageItem") && method.equals("containing")) return PonderInstructions.parcel(args);
            if (owner.equals("PackageOrderWithCrafts") && method.equals("simple")) return args[0];
            if (owner.equals("PackageStyles") && method.equals("getDefaultBox")) return PonderInstructions.parcel(new Object[0]);
            if (owner.equals("Component") && method.equals("literal")) return String.valueOf(args[0]);
            if (symbol.value.equals("Component.Serializer") && method.equals("toJson")) return new com.google.gson.Gson().toJson(Map.of("text", String.valueOf(args[0])));
            if (owner.equals("CreateLang") && method.equals("translateDirect")) return net.minecraft.client.resources.I18n.format("create." + args[0]);
            if (owner.equals("AllShapes.CASING_13PX") || symbol.value.equals("AllShapes.CASING_13PX")) return new AxisAlignedBB(0, 0, 0, 1, 13.0 / 16, 1);
            if (owner.equals("List") || owner.equals("ImmutableList") || owner.equals("Couple")) return new ArrayList<>(Arrays.asList(args));
            if (owner.equals("IntAttached") && method.equals("with")) return new ArrayList<>(Arrays.asList(args));
            if (owner.equals("NBTHelper") && method.equals("writeEnum")) { NBTTagCompound nbt = (NBTTagCompound) args[0]; nbt.setString(String.valueOf(args[1]), String.valueOf(args[2]).toUpperCase(Locale.ROOT)); return null; }
            if (owner.equals("NBTHelper") && method.equals("writeCompoundList")) {
                NBTTagList result = new NBTTagList();
                for (Object item : (List<?>) args[0]) result.appendTag(toNbt(((Callback) args[1]).apply(item)));
                return result;
            }
            if (owner.equals("IntAttached") && method.equals("of")) return new ArrayList<>(Arrays.asList(args));
            if (owner.equals("SandPaperItem") && method.equals("spawnParticles")) {
                program.scene.getScene().getWorld().spawnParticle(EnumParticleTypes.CRIT, num(args[1]), num(args[2]), num(args[3]), 0, .02, 0);
                return null;
            }
            if (owner.equals("ItemHandlerHelper") && method.equals("insertItemStacked")) return net.minecraftforge.items.ItemHandlerHelper.insertItemStacked((IItemHandler) args[0], stack(args[1]), truth(args[2]));
            if (symbol.value.startsWith("EntityType.") && method.equals("create")) return EntityList.createEntityByIDFromName(new ResourceLocation("minecraft", owner.toLowerCase(Locale.ROOT)), program.scene.getScene().getWorld());
            if (method.equals("getOpposite")) return direction(symbol).getOpposite();
            if (method.equals("getStepX")) return direction(symbol).getXOffset();
            if (method.equals("getStepZ")) return direction(symbol).getZOffset();
            if (method.equals("getAxis")) return direction(symbol).getAxis();
            if (method.equals("ordinal")) return PonderTile.ordinal(symbol.value);
            if (method.equals("get")) return symbol;
        }
        if (target instanceof String text && method.equals("append")) return text + args[0];
        if (target instanceof String text && method.equals("getString")) return text;
        if (target instanceof net.minecraft.world.World world) {
            if (method.equals("getBlockEntity")) return world.getTileEntity(pos(args[0]));
            if (method.equals("getCapability")) {
                TileEntity tile = world.getTileEntity(pos(args[1]));
                return tile == null ? null : new PonderTile(program, tile).fluids();
            }
            if (method.equals("addParticle")) {
                world.spawnParticle(PonderInstructions.particle(String.valueOf(args[0])), num(args[1]), num(args[2]), num(args[3]), num(args[4]), num(args[5]), num(args[6]));
                return null;
            }
        }
        if (target instanceof net.createmod.ponder.foundation.PonderScene && method.equals("linkElement")) {
            program.scene.getScene().linkElement((net.createmod.ponder.api.element.PonderElement) args[0], (net.createmod.ponder.api.element.ElementLink) args[1]); return null;
        }
        if (target instanceof Construct construct && construct.name.equals("FluidParticle")) return construct;
        if (target instanceof Construct construct && construct.name.equals("WalkAnimation")) {
            net.minecraft.entity.EntityLivingBase entity = (net.minecraft.entity.EntityLivingBase) construct.args[0];
            if (method.equals("position")) return entity.limbSwing;
            if (method.equals("setSpeed")) { entity.limbSwingAmount = (float) num(args[0]); return null; }
            if (method.equals("update")) { entity.prevLimbSwingAmount = entity.limbSwingAmount; entity.limbSwingAmount += ((float) num(args[0]) - entity.limbSwingAmount) * (float) num(args[1]); entity.limbSwing += entity.limbSwingAmount; return null; }
        }
        if (target == program.scene && method.equals("world")) return program.scene.getScene().getWorld();
        if (method.equals("getHolderLookupProvider")) return new Symbol("registries");
        return program.invoke(target, method, args);
    }
    static NBTBase toNbt(Object value) {
        if (value instanceof NBTBase nbt) return nbt.copy();
        if (value instanceof ItemStack stack) return stack.writeToNBT(new NBTTagCompound());
        if (value instanceof FluidStack stack) return stack.writeToNBT(new NBTTagCompound());
        if (value instanceof Boolean bool) return new NBTTagByte((byte) (bool ? 1 : 0));
        if (value instanceof Number number) return new NBTTagDouble(number.doubleValue());
        if (value instanceof List<?> list) { NBTTagList result = new NBTTagList(); list.forEach(v -> result.appendTag(toNbt(v))); return result; }
        return new NBTTagString(String.valueOf(value));
    }
    static Object binary(String op, Object a, Object b) {
        return switch (op) {
            case "==" -> equivalent(a, b);
            case "!=" -> !equivalent(a, b);
            case "+" -> { if (a instanceof String || b instanceof String) yield String.valueOf(a) + b;
                if (integral(a) && integral(b)) yield ((Number) a).longValue() + ((Number) b).longValue(); yield num(a) + num(b); }
            case "-" -> { if (integral(a) && integral(b)) yield ((Number) a).longValue() - ((Number) b).longValue(); yield num(a) - num(b); }
            case "*" -> { if (integral(a) && integral(b)) yield ((Number) a).longValue() * ((Number) b).longValue(); yield num(a) * num(b); }
            case "/" -> { if (integral(a) && integral(b)) yield ((Number) a).longValue() / ((Number) b).longValue(); yield num(a) / num(b); }
            case "%" -> { if (integral(a) && integral(b)) yield ((Number) a).longValue() % ((Number) b).longValue(); yield num(a) % num(b); }
            case ">" -> num(a) > num(b); case "<" -> num(a) < num(b); case ">=" -> num(a) >= num(b); case "<=" -> num(a) <= num(b);
            case "&&" -> truth(a) && truth(b); case "||" -> truth(a) || truth(b); case "^" -> truth(a) ^ truth(b);
            default -> throw new IllegalArgumentException("Callback operator " + op);
        };
    }
    private static boolean integral(Object value) { return value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long; }
    static Object unary(String op, Object value) {
        if (op.equals("-") && integral(value)) return -((Number) value).longValue();
        return op.equals("-") ? -num(value) : op.equals("!") ? !truth(value) : value;
    }
    private static boolean equivalent(Object a, Object b) {
        if (a instanceof Number x && b instanceof Number y) return Double.compare(x.doubleValue(), y.doubleValue()) == 0;
        if ((a instanceof Symbol || a instanceof Enum<?>) && (b instanceof Symbol || b instanceof Enum<?> || b instanceof String)
                || (b instanceof Symbol || b instanceof Enum<?>) && a instanceof String)
            return tail(String.valueOf(a)).equalsIgnoreCase(tail(String.valueOf(b)));
        return Objects.equals(a, b);
    }
    static Object literal(String value) {
        if (value.equals("null")) return null;
        if (value.equals("true") || value.equals("false")) return Boolean.parseBoolean(value);
        if (value.startsWith("\"")) return new JsonParser().parse(value).getAsString();
        String number = value.replace("_", "");
        if (number.matches("0[xX][0-9a-fA-F]+[lL]?")) return Long.parseUnsignedLong(number.replaceFirst("[lL]$", "").substring(2), 16);
        boolean floating = number.matches(".*[.eEfFdD].*");
        number = number.replaceAll("[fFdDlL]$", "");
        if (!floating) return Long.parseLong(number);
        return Double.parseDouble(number);
    }
    static double num(Object value) { return ((Number) value).doubleValue(); }
    static double number(JsonElement value) { return value.getAsDouble(); }
    static boolean truth(Object value) { return value instanceof Boolean b ? b : value != null && (!(value instanceof Number n) || n.doubleValue() != 0); }
    static String string(JsonObject json, String key, String fallback) { return json.has(key) ? json.get(key).getAsString() : fallback; }
    static String tail(String value) { return value.substring(value.lastIndexOf('.') + 1); }
    static Vec3d vec(Object value) { if (value instanceof Vec3d vec) return vec; Vec3i pos = (Vec3i) value; return new Vec3d(pos.getX(), pos.getY(), pos.getZ()); }
    static BlockPos pos(Object value) { return value instanceof BlockPos p ? p : new BlockPos(vec(value)); }
    static EnumFacing direction(Object value) { return value instanceof EnumFacing f ? f : EnumFacing.valueOf(tail(String.valueOf(value)).toUpperCase(Locale.ROOT)); }
    static ItemStack stack(Object value) { if (value instanceof ItemStack stack) return stack; if (value instanceof IBlockState state) return PonderData.item(state.getBlock().getRegistryName().toString()); throw new IllegalArgumentException("Expected item: " + value); }
    static ItemStack copy(ItemStack stack, int count) { ItemStack copy = stack.copy(); if (!copy.isEmpty()) copy.setCount(count); return copy; }
    static Fluid fluid(Object value) {
        if (value instanceof Fluid fluid) return fluid;
        if (value instanceof FluidStack stack) return stack.getFluid();
        String id = tail(String.valueOf(value)).toLowerCase(Locale.ROOT).replace("minecraft:", "").replace("create:", "");
        Fluid fluid = FluidRegistry.getFluid(id);
        if (fluid == null) throw new IllegalArgumentException("Unknown fluid " + id);
        return fluid;
    }
}
