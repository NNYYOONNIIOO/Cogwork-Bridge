package com.cogwork.cogworkbridge.compat.ponder;

import com.cogwork.cogworkbridge.Reference;
import com.google.gson.*;
import net.createmod.ponder.api.structure.*;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.*;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import java.io.*;
import java.util.*;

/** Resolves only our registered schematic aliases, never another plugin's structures. */
final class UpstreamPonderStructures implements PonderStructureProvider {
    private final Set<String> paths = new HashSet<>();
    UpstreamPonderStructures(JsonObject catalog) {
        for (JsonElement element : catalog.getAsJsonArray("catalog")) paths.add(element.getAsJsonObject().get("structure").getAsString());
    }
    @Override public ResourceLocation getId() { return new ResourceLocation(Reference.MOD_ID, "create_structures"); }
    @Override public void invalidate() { PonderText.invalidate(); }
    @Override public PonderStructureProviderResult find(ResourceLocation location) throws IOException {
        if (!location.getNamespace().equals(Reference.MOD_ID) || !paths.contains(location.getPath())) return PonderStructureProviderResult.notFound();
        ResourceLocation structureResource = new ResourceLocation("create", "ponder/" + location.getPath() + ".nbt");
        NBTTagCompound root;
        try (InputStream input = Minecraft.getMinecraft().getResourceManager().getResource(structureResource).getInputStream()) {
            root = CompressedStreamTools.readCompressed(input);
        }
        NBTTagList palette = root.getTagList("palette", 10);
        List<IBlockState> states = new ArrayList<>();
        NBTTagList mapped = new NBTTagList();
        List<String> diagnostics = new ArrayList<>();
        for (int i = 0; i < palette.tagCount(); i++) {
            try { states.add(PonderData.state(palette.getCompoundTagAt(i))); }
            catch (IllegalArgumentException missing) { throw new IOException(location + ": " + missing.getMessage(), missing); }
            mapped.appendTag(paletteState(states.get(i)));
        }
        NBTTagList blocks = root.getTagList("blocks", 10);
        Map<BlockPos, BeltShape> beltShapes = beltShapes(blocks, palette);
        Map<BlockPos, TankShape> tankShapes = new HashMap<>();
        for (int i = 0; i < blocks.tagCount(); i++) {
            NBTTagCompound entry = blocks.getCompoundTagAt(i);
            int stateIndex = entry.getInteger("state");
            if (stateIndex < 0 || stateIndex >= palette.tagCount()) continue;
            String path = palette.getCompoundTagAt(stateIndex).getString("Name");
            if (!path.endsWith(":fluid_tank") && !path.endsWith(":creative_fluid_tank")) continue;
            NBTTagCompound source = entry.getCompoundTag("nbt");
            BlockPos local = PonderData.modernPos(entry.getTag("pos"));
            BlockPos controller = controllerPos(source, local);
            if (controller.equals(local)) {
                tankShapes.put(controller, new TankShape(
                        Math.max(1, source.getInteger("Size")),
                        Math.max(1, source.getInteger("Height")),
                        !source.hasKey("Window", 1) || source.getBoolean("Window")));
            }
        }
        // Constructors and serializers see a populated, isolated world, never a null/live world.
        net.createmod.ponder.foundation.PonderWorld conversionWorld = new net.createmod.ponder.foundation.PonderWorld(BlockPos.ORIGIN, null);
        for (int i = 0; i < blocks.tagCount(); i++) {
            NBTTagCompound entry = blocks.getCompoundTagAt(i);
            int index = entry.getInteger("state");
            if (index < 0 || index >= states.size()) throw new IOException("Invalid Ponder palette index");
            conversionWorld.setBlockState(PonderData.modernPos(entry.getTag("pos")), states.get(index), 2);
        }
        for (int i = 0; i < blocks.tagCount(); i++) {
            NBTTagCompound entry = blocks.getCompoundTagAt(i);
            int index = entry.getInteger("state");
            if (index < 0 || index >= states.size()) throw new IOException("Invalid Ponder palette index");
            NBTTagList p = entry.getTagList("pos", 3);
            BlockPos pos = new BlockPos(p.getIntAt(0), p.getIntAt(1), p.getIntAt(2));
            NBTTagCompound tile = PonderData.tile(conversionWorld, states.get(index), pos, entry.getCompoundTag("nbt"), palette.getCompoundTagAt(index).getCompoundTag("Properties"));
            if (tile != null) {
                String path = states.get(index).getBlock().getRegistryName().getPath();
                BeltShape belt = beltShapes.get(pos);
                if (belt != null) {
                    tile.setInteger("StepX", belt.step().getX());
                    tile.setInteger("StepY", belt.step().getY());
                    tile.setInteger("StepZ", belt.step().getZ());
                    tile.setInteger("Index", belt.index());
                    tile.setInteger("Length", belt.length());
                }
                if (path.equals("fluid_tank") || path.equals("creative_fluid_tank")) {
                    NBTTagCompound source = entry.getCompoundTag("nbt");
                    BlockPos controller = controllerPos(source, pos);
                    com.cogwork.cogworkbridge.content.fluids.FluidTankTileEntity.setControllerOffset(tile, controller.subtract(pos));
                    TankShape shape = tankShapes.get(controller);
                    if (shape != null) {
                        tile.setInteger("TankWidth", shape.width());
                        tile.setInteger("TankHeight", shape.height());
                        tile.setBoolean("TankWindows", shape.windows());
                    }
                }
                entry.setTag("nbt", tile);
            } else entry.removeTag("nbt");
        }
        PonderData.convertNested(root.getTag("entities"));
        root.setTag("palette", mapped); root.removeTag("palettes"); root.setInteger("DataVersion", 1343);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CompressedStreamTools.writeCompressed(root, output);
        byte[] encoded = output.toByteArray();
        String fingerprint;
        try { fingerprint = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(encoded)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        return PonderStructureProviderResult.found(encoded, "cogworkbridge-ponder-v2:" + fingerprint, Map.of(), diagnostics);
    }
    private record TankShape(int width, int height, boolean windows) { }
    private record BeltMember(BlockPos pos, int index, int length) { }
    private record BeltShape(BlockPos step, int index, int length) { }
    private static Map<BlockPos, BeltShape> beltShapes(NBTTagList blocks, NBTTagList palette) throws IOException {
        Map<BlockPos, List<BeltMember>> chains = new HashMap<>();
        for (int i = 0; i < blocks.tagCount(); i++) {
            NBTTagCompound entry = blocks.getCompoundTagAt(i);
            int state = entry.getInteger("state");
            if (state < 0 || state >= palette.tagCount() || !palette.getCompoundTagAt(state).getString("Name").equals("create:belt")) continue;
            NBTTagCompound nbt = entry.getCompoundTag("nbt");
            if (!nbt.hasKey("Controller")) continue;
            chains.computeIfAbsent(PonderData.modernPos(nbt.getTag("Controller")), key -> new ArrayList<>()).add(
                    new BeltMember(PonderData.modernPos(entry.getTag("pos")), nbt.getInteger("Index"), Math.max(1, nbt.getInteger("Length"))));
        }
        Map<BlockPos, BeltShape> result = new HashMap<>();
        for (List<BeltMember> members : chains.values()) {
            members.sort(Comparator.comparingInt(BeltMember::index));
            BeltMember first = members.get(0);
            if (members.size() < 2) continue;
            BeltMember second = members.get(1);
            int distance = second.index() - first.index();
            BlockPos delta = second.pos().subtract(first.pos());
            if (distance <= 0 || delta.getX() % distance != 0 || delta.getY() % distance != 0 || delta.getZ() % distance != 0)
                throw new IOException("Invalid Ponder belt indices at " + first.pos());
            BlockPos step = new BlockPos(delta.getX() / distance, delta.getY() / distance, delta.getZ() / distance);
            if (step.equals(BlockPos.ORIGIN) || Math.max(Math.abs(step.getX()), Math.max(Math.abs(step.getY()), Math.abs(step.getZ()))) > 1)
                throw new IOException("Invalid Ponder belt step at " + first.pos());
            for (BeltMember member : members) {
                int index = member.index() - first.index();
                if (!member.pos().equals(first.pos().add(step.getX() * index, step.getY() * index, step.getZ() * index)))
                    throw new IOException("Inconsistent Ponder belt chain at " + member.pos());
                result.put(member.pos(), new BeltShape(step, member.index(), member.length()));
            }
        }
        return result;
    }
    private static BlockPos controllerPos(NBTTagCompound source, BlockPos local) {
        if (!source.hasKey("Controller")) return local;
        BlockPos lastKnown = source.hasKey("LastKnownPos") ? PonderData.modernPos(source.getTag("LastKnownPos")) : local;
        return local.add(PonderData.modernPos(source.getTag("Controller")).subtract(lastKnown));
    }
    private static NBTTagCompound paletteState(IBlockState state) {
        NBTTagCompound result = NBTUtil.writeBlockState(new NBTTagCompound(), state);
        NBTTagCompound properties = result.getCompoundTag("Properties");
        // FFD adds waterlogged to native states. Ponder rejects even its redundant dry default.
        // Omit only false when the native decoder will restore exactly the same default value.
        if (properties.getString("waterlogged").equals("false")) {
            IBlockState defaults = state.getBlock().getDefaultState();
            for (IProperty<?> property : state.getPropertyKeys())
                if (property.getName().equals("waterlogged") && Boolean.FALSE.equals(defaults.getValue(property))) {
                    properties.removeTag("waterlogged");
                    break;
                }
        }
        return result;
    }
}
