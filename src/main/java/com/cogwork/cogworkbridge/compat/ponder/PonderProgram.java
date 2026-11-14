package com.cogwork.cogworkbridge.compat.ponder;

import com.cogwork.cogworkbridge.CogworkBridge;
import com.google.gson.*;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.*;
import net.createmod.ponder.api.element.*;
import net.createmod.ponder.foundation.instruction.TickingInstruction;
import net.minecraft.util.math.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.nbt.NBTTagCompound;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.*;

/** Executes the compiled upstream scene DSL against Ponder's native, resettable timeline. */
final class PonderProgram {
    final SceneBuilder scene;
    final SceneBuildingUtil util;
    final PonderValues values;
    final String name;
    final String registeredId;
    final Map<Integer, Object> links = new HashMap<>();
    final List<PonderInstructions.BeltRef> beltItems = new ArrayList<>();
    final Set<String> unsupported = new LinkedHashSet<>();
    String sceneId;
    BlockPos editingPos;
    int duration;
    int nextBeltItemId;
    private int textIndex = 1;
    PonderProgram(SceneBuilder scene, SceneBuildingUtil util, String name, String registeredId) {
        this.scene = scene; this.util = util; this.name = name; this.registeredId = registeredId; values = new PonderValues(this);
    }
    void build(JsonObject program) {
        sceneId = program.get("id").getAsString();
        duration = program.get("duration").getAsInt();
        scene.addInstruction(s -> s.addElement(new PonderDiagnostics(this)));
        int cursor = 0;
        // Replay state belongs to the scene, never to a real world or a previously viewed scene.
        scene.addInstruction(s -> beltItems.forEach(PonderInstructions.BeltRef::reset));
        for (JsonElement element : program.getAsJsonArray("actions")) {
            JsonObject action = element.getAsJsonObject();
            int tick = action.get("at").getAsInt();
            if (tick > cursor) scene.idle(tick - cursor);
            cursor = tick;
            try {
                String group = action.get("group").getAsString(), op = action.get("op").getAsString();
                // Upstream may mark completion before its final idle/animation. Legacy's
                // instruction stops ticking immediately, so finish only at the resolved end.
                if (group.equals("scene") && op.equals("markAsFinished")) continue;
                Object[] args = values.arguments(action.getAsJsonArray("args"));
                Object result;
                if (group.equals("world") && PonderInstructions.handles(op)) result = PonderInstructions.world(this, op, args);
                else if (group.equals("overlay") && PonderInputs.handles(op)) result = PonderInputs.show(this, op, args);
                else if (group.equals("effects") && PonderInstructions.handlesEffect(op)) result = PonderInstructions.effect(this, op, args);
                else if (group.equals("special") && PonderInstructions.handlesSpecial(op)) result = PonderInstructions.special(this, op, args);
                else {
                    Object target = switch (group) { case "world" -> scene.world(); case "effects" -> scene.effects(); case "overlay" -> scene.overlay(); case "special" -> scene.special(); case "debug" -> scene.debug(); default -> scene; };
                    if (op.equals("title")) {
                        args[0] = registeredId;
                        args[1] = PonderText.get("create.ponder." + sceneId + ".header", String.valueOf(args[1]));
                    }
                    result = invoke(target, op, args);
                }
                if (action.has("result")) links.put(action.get("result").getAsInt(), result);
                if (group.equals("world") && Set.of("setBlock", "setBlocks", "replaceBlocks").contains(op)) {
                    JsonElement raw = action.getAsJsonArray("args").get(1);
                    if (raw.isJsonObject() && raw.getAsJsonObject().has("properties")) {
                        JsonObject properties = raw.getAsJsonObject().getAsJsonObject("properties");
                        run("visual state", () -> tiles(args[0], tile -> properties.entrySet().forEach(e ->
                                PonderData.visualProperty(this, tile, e.getKey(), values.value(e.getValue())))));
                    }
                }
                if (action.has("chain")) for (JsonElement entry : action.getAsJsonArray("chain")) {
                    JsonArray chain = entry.getAsJsonArray();
                    String method = chain.get(0).getAsString();
                    Object[] arguments = values.arguments(chain.get(1).getAsJsonArray());
                    if (method.equals("text") && arguments.length > 0) arguments[0] = PonderText.get(
                            "create.ponder." + sceneId + ".text_" + textIndex++, String.valueOf(arguments[0]));
                    // Upstream shared strings remain in the original resource namespace.
                    if (method.equals("sharedText") && arguments.length > 0 && arguments[0] instanceof String key)
                        arguments[0] = key.contains(":") ? key.replace("create:", "cogworkbridge:") : key;
                    invoke(result, method, arguments);
                }
            } catch (RuntimeException failure) { report(action.get("group").getAsString() + "." + action.get("op").getAsString(), failure); }
        }
        if (duration > cursor) scene.idle(duration - cursor);
        if (!unsupported.isEmpty()) {
            scene.overlay().showText(120).colored(PonderPalette.RED).independent(0)
                    .text(PonderText.get("cogworkbridge.ponder.incomplete", "Some demonstration operations are not supported in this version.")
                            + " [" + String.join(", ", unsupported) + "]");
            scene.idle(120);
        }
        int end = Math.max(scene.getScene().getBuildCursor(), scene.getScene().getTotalTicks());
        if (end > scene.getScene().getBuildCursor()) scene.idle(end - scene.getScene().getBuildCursor());
        scene.markAsFinished();
    }
    void report(String operation, RuntimeException failure) {
        if (unsupported.add(operation)) CogworkBridge.LOGGER.warn("Ponder {}: {}: {}", name, operation, failure.toString());
    }
    void run(String operation, Runnable action) {
        scene.addInstruction(s -> {
            try { action.run(); s.forEach(WorldSectionElement.class, WorldSectionElement::queueRedraw); }
            catch (RuntimeException failure) { report(operation, failure); }
        });
    }
    void animate(int duration, Consumer<Float> delta) {
        scene.addInstruction(new TickingInstruction(false, Math.max(1, duration)) {
            private float previous;
            @Override public void reset(net.createmod.ponder.foundation.PonderScene s) { super.reset(s); previous = 0; }
            @Override protected void tickRunning(net.createmod.ponder.foundation.PonderScene s, int elapsed, float progress) {
                float step = progress - previous; previous = progress;
                try { delta.accept(step); } catch (RuntimeException failure) { report("animation", failure); }
            }
            @Override public Object captureState() { return new Object[]{super.captureState(), previous}; }
            @Override public void restoreState(Object state) { Object[] data = (Object[]) state; super.restoreState(data[0]); previous = (Float) data[1]; }
        });
    }
    Selection selection(Object value) {
        if (value instanceof Selection selected) return selected;
        if (value instanceof BlockPos pos) return util.select().position(pos);
        throw new IllegalArgumentException("Expected selection: " + value);
    }
    void tiles(Object selection, Consumer<TileEntity> action) {
        for (BlockPos pos : selection(selection)) { TileEntity tile = scene.getScene().getWorld().getTileEntity(pos); if (tile != null) action.accept(tile); }
    }
    void mutate(TileEntity tile, Consumer<NBTTagCompound> action) {
        NBTTagCompound nbt = tile.writeToNBT(new NBTTagCompound());
        action.accept(nbt); PonderData.convertNested(nbt);
        PonderData.aliases(nbt, tile.getBlockType().getRegistryName().getPath());
        if (tile instanceof com.cogwork.cogworkbridge.content.logistics.arm.MechanicalArmTileEntity)
            PonderData.armPointSides(tile.getWorld(), tile.getPos(), nbt);
        tile.readFromNBT(nbt);
        tile.getWorld().markBlockRangeForRenderUpdate(tile.getPos(), tile.getPos());
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    Object convert(Object value, Class<?> type) {
        if (value == null) { if (type.isPrimitive()) throw new IllegalArgumentException("null primitive"); return null; }
        if (type.isInstance(value)) return value;
        if (value instanceof Number n) {
            if (type == int.class || type == Integer.class) return n.intValue();
            if (type == float.class || type == Float.class) return n.floatValue();
            if (type == double.class || type == Double.class) return n.doubleValue();
            if (type == long.class || type == Long.class) return n.longValue();
            if (type == byte.class || type == Byte.class) return n.byteValue();
        }
        if ((type == boolean.class || type == Boolean.class) && value instanceof Boolean) return value;
        if (value instanceof PonderValues.Symbol symbol) {
            String last = symbol.last();
            if (type == net.minecraft.item.EnumDyeColor.class && last.equalsIgnoreCase("light_gray")) return net.minecraft.item.EnumDyeColor.SILVER;
            if (type.isEnum()) return Enum.valueOf((Class) type, last.toUpperCase(Locale.ROOT));
            if (type == String.class) return last.toLowerCase(Locale.ROOT);
            if (type == net.createmod.catnip.gui.element.ScreenElement.class) return PonderIcons.icon(last);
        }
        if (value instanceof PonderValues.Callback callback) {
            if (type == Consumer.class) return (Consumer) v -> callback.apply(v);
            if (type == Function.class) return (Function) v -> callback.apply(v);
            if (type == UnaryOperator.class) return (UnaryOperator) v -> callback.apply(v);
            if (type == Supplier.class) return (Supplier) () -> callback.apply();
        }
        if (value instanceof PonderValues.Construct reference && reference.name().equals("methodref")) {
            if (type == Consumer.class) return (Consumer) v -> values.methodReference(reference, v);
            if (type == Supplier.class) return (Supplier) () -> values.methodReference(reference);
        }
        if (value instanceof String string) {
            if (type == net.minecraft.util.ResourceLocation.class) return new net.minecraft.util.ResourceLocation(string);
            if (type.isEnum()) return Enum.valueOf((Class) type, string.toUpperCase(Locale.ROOT));
        }
        if (type == String.class) return String.valueOf(value);
        if (type == BlockPos.class && value instanceof Vec3d vec) return new BlockPos(vec);
        if (type == Vec3d.class && value instanceof Vec3i pos) return new Vec3d(pos.getX(), pos.getY(), pos.getZ());
        if (type == Vec3i.class && value instanceof Vec3d vec) return new BlockPos(vec);
        throw new IllegalArgumentException("Cannot convert " + value + " to " + type.getSimpleName());
    }
    Object invoke(Object target, String method, Object... args) {
        if (target == null) throw new IllegalArgumentException("No target for " + method);
        List<Method> methods = new ArrayList<>(Arrays.asList(target.getClass().getMethods()));
        methods.sort(Comparator.comparing(Method::isVarArgs));
        for (Method candidate : methods) {
            if (!candidate.getName().equals(method)) continue;
            Class<?>[] types = candidate.getParameterTypes();
            int fixed = candidate.isVarArgs() ? types.length - 1 : types.length;
            if (args.length < fixed || !candidate.isVarArgs() && args.length != fixed) continue;
            Object[] converted = new Object[types.length];
            try {
                for (int i = 0; i < fixed; i++) converted[i] = convert(args[i], types[i]);
                if (candidate.isVarArgs()) {
                    Class<?> component = types[fixed].getComponentType();
                    Object array = Array.newInstance(component, args.length - fixed);
                    for (int i = fixed; i < args.length; i++) Array.set(array, i - fixed, convert(args[i], component));
                    converted[fixed] = array;
                }
            } catch (IllegalArgumentException incompatible) { continue; }
            try { candidate.setAccessible(true); return candidate.invoke(target, converted); }
            catch (InvocationTargetException e) { throw new IllegalStateException(method + ": " + e.getCause(), e.getCause()); }
            catch (ReflectiveOperationException e) { throw new IllegalStateException(method, e); }
        }
        throw new IllegalArgumentException("Unsupported call " + target.getClass().getSimpleName() + "." + method + "/" + args.length);
    }
}
