package com.cogwork.cogworkbridge.compat.ponder;

import com.cogwork.cogworkbridge.kinetic.KineticTileEntity;
import com.cogwork.cogworkbridge.content.logistics.belt.BeltTileEntity;
import com.cogwork.cogworkbridge.content.logistics.packagebox.PackageItem;
import com.cogwork.cogworkbridge.content.display.DisplayBoardTileEntity;
import net.createmod.ponder.api.element.*;
import net.createmod.ponder.api.ParticleEmitter;
import net.createmod.ponder.foundation.instruction.TickingInstruction;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.properties.IProperty;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraftforge.items.ItemStackHandler;
import java.util.*;
import static com.cogwork.cogworkbridge.compat.ponder.PonderValues.*;

/** Create-specific instructions implemented on the port's real tiles in an isolated Ponder world. */
final class PonderInstructions {
    private static final Set<String> WORLD = Set.of(
            "setKineticSpeed", "multiplyKineticSpeed", "modifyKineticSpeed", "modifyBlockEntity", "modifyBlockEntityNBT",
            "createItemOnBelt", "createItemOnBeltLike", "removeItemsFromBelt", "stallBeltItem", "changeBeltItemTo",
            "rotateBearing", "movePulley", "moveDeployer", "animateBogey", "instructArm", "setFilterData", "setCraftingResult",
            "connectCrafterInvs", "flapFunnel", "propagatePipeChange", "toggleControls", "animateTrainStation",
            "conductorBlaze", "changeSignalState", "setDisplayBoardText", "dyeDisplayBoard", "flashDisplayLink",
            "modifyBlock", "modifyBlocks", "cycleBlockProperty", "toggleRedstonePower", "createEntity", "incrementBlockBreakingProgress");
    static boolean handles(String op) { return WORLD.contains(op); }
    static boolean handlesEffect(String op) { return Set.of("rotationDirectionIndicator", "rotationSpeedIndicator", "superGlue", "simpleParticleEmitter", "particleEmitterWithinBlockSpace").contains(op); }
    static boolean handlesSpecial(String op) { return Set.of("createBirb", "changeBirbPose", "conductorBirb", "createCart").contains(op); }
    private static boolean accepts(Class<?> type, TileEntity tile) {
        return type.isInstance(tile) || type == com.cogwork.cogworkbridge.content.kinetics.waterwheel.WaterWheelTileEntity.class
                && tile instanceof com.cogwork.cogworkbridge.content.kinetics.waterwheel.LargeWaterWheelTileEntity;
    }
    static Object world(PonderProgram p, String op, Object[] a) {
        switch (op) {
            case "incrementBlockBreakingProgress" -> p.run(op, () -> {
                var world = p.scene.getScene().getWorld();
                BlockPos at = pos(a[0]);
                IBlockState state = world.getBlockState(at);
                int id = at.hashCode();
                if (state.getBlock().isAir(state, world, at)) { world.setBlockBreakingProgress(id, at, -1); return; }
                var existing = world.getBlockBreakingProgress().get(id);
                int progress = existing == null ? 1 : existing.progress + 1;
                if (progress >= 10) {
                    world.setBlockBreakingProgress(id, at, -1);
                    world.playEvent(2001, at, net.minecraft.block.Block.getStateId(state));
                    world.setBlockToAir(at);
                } else world.setBlockBreakingProgress(id, at, progress);
            });
            case "createEntity" -> {
                return p.scene.world().createEntity(world -> {
                    Entity entity = (Entity) ((PonderValues.Callback) a[0]).apply(world);
                    if (entity != null) {
                        entity.prevPosX = entity.lastTickPosX = entity.posX;
                        entity.prevPosY = entity.lastTickPosY = entity.posY;
                        entity.prevPosZ = entity.lastTickPosZ = entity.posZ;
                        entity.prevRotationYaw = entity.rotationYaw;
                        entity.prevRotationPitch = entity.rotationPitch;
                    }
                    return entity;
                });
            }
            case "setKineticSpeed", "multiplyKineticSpeed", "modifyKineticSpeed" -> p.run(op, () -> p.tiles(a[0], tile -> {
                if (!(tile instanceof KineticTileEntity kinetic)) return;
                float speed = (float) (op.equals("setKineticSpeed") ? num(a[1]) : op.equals("multiplyKineticSpeed") ? kinetic.getSpeed() * num(a[1]) : num(((PonderValues.Callback) a[1]).apply(kinetic.getSpeed())));
                p.mutate(tile, nbt -> { nbt.setFloat("Speed", speed); nbt.setBoolean("Overstressed", false); });
            }));
            case "modifyBlockEntityNBT" -> p.run(op, () -> p.tiles(a[0], tile -> {
                if (accepts((Class<?>) a[1], tile)) p.mutate(tile, nbt -> ((PonderValues.Callback) a[2]).apply(nbt));
            }));
            case "modifyBlockEntity" -> p.run(op, () -> {
                TileEntity tile = p.scene.getScene().getWorld().getTileEntity(pos(a[0]));
                if (tile != null && accepts((Class<?>) a[1], tile)) {
                    if (a[2] instanceof PonderValues.Callback callback) callback.apply(tile);
                    else if (a[2] instanceof Construct reference) PonderTile.methodReference(p, tile, reference);
                    else throw new IllegalArgumentException("Unsupported block callback");
                }
            });
            case "modifyBlock", "modifyBlocks" -> p.run(op, () -> {
                for (BlockPos pos : p.selection(a[0])) {
                    IBlockState before = p.scene.getScene().getWorld().getBlockState(pos);
                    p.editingPos = pos;
                    try {
                        Object result = ((PonderValues.Callback) a[1]).apply(before);
                        if (!(result instanceof IBlockState state)) throw new IllegalArgumentException("Block callback did not return state");
                        if (state != before) p.scene.getScene().getWorld().setBlockState(pos, state, 3);
                    } finally { p.editingPos = null; }
                }
            });
            case "cycleBlockProperty" -> p.run(op, () -> {
                BlockPos at = pos(a[0]); IBlockState state = p.scene.getScene().getWorld().getBlockState(at);
                String property = tail(String.valueOf(a[1])).toLowerCase(Locale.ROOT);
                boolean found = false;
                for (IProperty<?> candidate : state.getPropertyKeys()) if (candidate.getName().equals(property)) { p.scene.getScene().getWorld().setBlockState(at, cycle(state, candidate), 3); found = true; break; }
                if (!found) { TileEntity tile = p.scene.getScene().getWorld().getTileEntity(at); if (tile != null) new PonderTile(p, tile).cycle(property); }
            });
            case "toggleRedstonePower" -> {
                p.scene.world().toggleRedstonePower(p.selection(a[0]));
                p.run(op, () -> p.tiles(a[0], tile -> { PonderTile access = new PonderTile(p, tile); if (access.hasField("powered")) access.assign("powered", !truth(access.field("powered"))); }));
            }
            case "createItemOnBelt" -> {
                BeltRef reference = new BeltRef(p, pos(a[0]), stack(a[2]), direction(a[1]));
                p.run(op, reference::start);
                p.scene.addInstruction(new TickingInstruction(false, p.duration + 200) {
                    @Override protected void tickRunning(net.createmod.ponder.foundation.PonderScene s, int elapsed, float progress) { reference.tick(); }
                    @Override public Object captureState() { return new Object[]{super.captureState(), reference.capture()}; }
                    @Override public void restoreState(Object state) { Object[] data = (Object[]) state; super.restoreState(data[0]); reference.restore((Object[]) data[1]); }
                    @Override public void reset(net.createmod.ponder.foundation.PonderScene scene) { super.reset(scene); reference.reset(); }
                });
                return reference;
            }
            case "createItemOnBeltLike" -> p.run(op, () -> {
                TileEntity tile = p.scene.getScene().getWorld().getTileEntity(pos(a[0]));
                if (tile != null) new PonderTile(p, tile).insert(stack(a[2]), direction(a[1]));
            });
            case "removeItemsFromBelt" -> p.run(op, () -> {
                BlockPos at = pos(a[0]);
                p.beltItems.stream().filter(v -> v.current.equals(at)).forEach(BeltRef::remove);
                TileEntity tile = p.scene.getScene().getWorld().getTileEntity(at);
                if (tile != null) new PonderTile(p, tile).clearItems();
            });
            case "stallBeltItem" -> p.run(op, () -> ((BeltRef) a[0]).stalled = truth(a[1]));
            case "changeBeltItemTo" -> p.run(op, () -> { BeltRef ref = (BeltRef) a[0]; ref.item = stack(a[1]).copy(); ref.render(); });
            case "rotateBearing", "movePulley", "moveDeployer", "animateBogey" -> {
                String field = switch (op) { case "rotateBearing" -> "angleDegrees"; case "movePulley" -> "cableOffset"; case "moveDeployer" -> "renderExtension"; default -> "wheelAngle"; };
                p.animate((int) num(a[2]), delta -> {
                    TileEntity tile = p.scene.getScene().getWorld().getTileEntity(pos(a[0]));
                    if (tile == null) return;
                    if (op.equals("animateBogey") && tile instanceof com.cogwork.cogworkbridge.content.trains.track.BogeyTile bogey) { bogey.animateTutorialWheels(-(float) num(a[1]) * delta); return; }
                    PonderTile access = new PonderTile(p, tile);
                    if (op.equals("moveDeployer")) {
                        NBTTagCompound data = tile.getTileData();
                        float before = data.hasKey("TutorialDeployerExtension") ? data.getFloat("TutorialDeployerExtension") : (float) num(access.field(field));
                        data.setFloat("TutorialDeployerPrevious", before);
                        data.setFloat("TutorialDeployerExtension", before + (float) num(a[1]) * delta);
                        return;
                    }
                    float before = (float) num(access.field(field)), value = before + (float) num(a[1]) * delta;
                    access.assign(field, value);
                    if (op.equals("movePulley") && tile instanceof com.cogwork.cogworkbridge.content.contraptions.pulley.RopePulleyTileEntity)
                        access.assign("running", true);
                    if (op.equals("movePulley") && access.hasField("previousCableOffset")) access.assign("previousCableOffset", before);
                    if (op.equals("rotateBearing") && access.hasField("previousAngleDegrees")) access.assign("previousAngleDegrees", before);
                });
            }
            case "instructArm" -> p.run(op, () -> {
                TileEntity tile = p.scene.getScene().getWorld().getTileEntity(pos(a[0]));
                if (tile == null) return;
                PonderTile access = new PonderTile(p, tile);
                int phase = PonderTile.ordinal(String.valueOf(a[1])), target = (int) num(a[3]);
                int before = (int) num(access.field("target"));
                if (before >= 0) { access.assign("previousTarget", before); access.assign("startingAtRest", false); }
                else access.assign("startingAtRest", true);
                if (target >= 0 && phase >= 2) {
                    net.minecraft.nbt.NBTTagList points = tile.writeToNBT(new NBTTagCompound()).getTagList("Points", 10);
                    for (int i = 0; i < points.tagCount(); i++) if (!points.getCompoundTagAt(i).getBoolean("Output")) target++;
                }
                access.assign("held", stack(a[2]).copy()); access.assign("phase", phase);
                access.assign("target", target); access.assign("previousProgress", 0); access.assign("progress", 0);
            });
            case "setFilterData" -> p.run(op, () -> p.tiles(a[0], tile -> new PonderTile(p, tile).call("setFilter", a[2])));
            case "flapFunnel" -> p.run(op, () -> { TileEntity tile = p.scene.getScene().getWorld().getTileEntity(pos(a[0])); if (tile != null) new PonderTile(p, tile).flap(truth(a[1])); });
            case "setCraftingResult" -> p.run(op, () -> { TileEntity tile = p.scene.getScene().getWorld().getTileEntity(pos(a[0])); if (tile != null) new PonderTile(p, tile).assign("scriptedResult", a[1]); });
            case "connectCrafterInvs" -> p.run(op, () -> PonderTile.connectCrafters(p, pos(a[0]), pos(a[1])));
            case "propagatePipeChange" -> p.run(op, () -> { TileEntity tile = p.scene.getScene().getWorld().getTileEntity(pos(a[0])); if (tile != null) new PonderTile(p, tile).call("onSpeedChanged", 0); });
            case "toggleControls" -> p.scene.addInstruction(new net.createmod.ponder.foundation.instruction.PonderInstruction() {
                boolean complete;
                final net.createmod.ponder.foundation.PonderWorld world = p.scene.getScene().getWorld();
                final BlockPos at = pos(a[0]);
                @Override public void reset(net.createmod.ponder.foundation.PonderScene scene) { complete = false; com.cogwork.cogworkbridge.client.TutorialRenderState.setOpen(world, at, false); }
                @Override public boolean isComplete() { return complete; }
                @Override public void tick(net.createmod.ponder.foundation.PonderScene scene) {
                    if (complete) return; complete = true;
                    com.cogwork.cogworkbridge.client.TutorialRenderState.setOpen(world, at, !com.cogwork.cogworkbridge.client.TutorialRenderState.isOpen(world, at));
                    scene.forEach(WorldSectionElement.class, WorldSectionElement::queueRedraw);
                }
                @Override public Object captureState() { return new boolean[]{complete, com.cogwork.cogworkbridge.client.TutorialRenderState.isOpen(world, at)}; }
                @Override public void restoreState(Object state) { boolean[] saved = (boolean[]) state; complete = saved[0]; com.cogwork.cogworkbridge.client.TutorialRenderState.setOpen(world, at, saved[1]); }
            });
            case "animateTrainStation" -> p.run(op, () -> { TileEntity tile = p.scene.getScene().getWorld().getTileEntity(pos(a[0])); if (tile != null) new PonderTile(p, tile).assign("trainPresent", a[1]); });
            case "conductorBlaze" -> p.run(op, () -> { TileEntity tile = p.scene.getScene().getWorld().getTileEntity(pos(a[0])); if (tile != null) new PonderTile(p, tile).assign("tutorialConductor", a[1]); });
            case "changeSignalState" -> p.run(op, () -> { TileEntity tile = p.scene.getScene().getWorld().getTileEntity(pos(a[0])); if (tile != null) new PonderTile(p, tile).signal(String.valueOf(a[1])); });
            case "setDisplayBoardText", "dyeDisplayBoard" -> p.run(op, () -> {
                TileEntity tile = p.scene.getScene().getWorld().getTileEntity(pos(a[0]));
                if (!(tile instanceof DisplayBoardTileEntity display)) return;
                if (op.equals("setDisplayBoardText")) display.setManualText(String.valueOf(a[2]), (int) num(a[1]));
                else display.setRowColor(net.minecraft.item.EnumDyeColor.valueOf(tail(String.valueOf(a[2])).toUpperCase(Locale.ROOT)), (int) num(a[1]));
            });
            case "flashDisplayLink" -> p.run(op, () -> { TileEntity tile = p.scene.getScene().getWorld().getTileEntity(pos(a[0])); if (tile != null) new PonderTile(p, tile).call("pulse"); });
            default -> throw new IllegalArgumentException("Missing instruction " + op);
        }
        return null;
    }
    @SuppressWarnings({"rawtypes", "unchecked"}) private static IBlockState cycle(IBlockState state, IProperty property) { return state.cycleProperty(property); }
    static Object effect(PonderProgram p, String op, Object[] args) {
        if (op.equals("simpleParticleEmitter") || op.equals("particleEmitterWithinBlockSpace")) {
            Vec3d motion = vec(args[1]);
            Object type = args[0];
            return (ParticleEmitter) (world, x, y, z) -> {
                if (type instanceof Construct descriptor && descriptor.name().equals("FluidParticle")) {
                    net.minecraftforge.fluids.FluidStack fluid = descriptor.args()[0] instanceof net.minecraftforge.fluids.FluidStack f ? f : new net.minecraftforge.fluids.FluidStack(PonderValues.fluid(descriptor.args()[0]), 1000);
                    net.minecraft.block.Block block = fluid.getFluid().getBlock();
                    if (block != null) world.spawnParticle(EnumParticleTypes.BLOCK_CRACK, x, y, z, motion.x, motion.y, motion.z, net.minecraft.block.Block.getStateId(block.getDefaultState()));
                    else world.spawnParticle(EnumParticleTypes.WATER_SPLASH, x, y, z, motion.x, motion.y, motion.z);
                } else world.spawnParticle(particle(String.valueOf(type)), x, y, z, motion.x, motion.y, motion.z);
            };
        }
        BlockPos at = pos(args[0]);
        if (op.equals("superGlue")) {
            p.scene.effects().emitParticles(new Vec3d(at).add(.5, .5, .5), p.scene.effects().simpleParticleEmitter(EnumParticleTypes.SLIME, Vec3d.ZERO), 4, 10);
            return null;
        }
        // Keep the indicator centred on the actual rotation axis and let its handedness follow RPM.
        p.animate(20, delta -> {
            TileEntity tile = p.scene.getScene().getWorld().getTileEntity(at);
            if (!(tile instanceof KineticTileEntity kinetic)) return;
            float sign = Math.signum(kinetic.getSpeed());
            double angle = p.scene.getScene().getCurrentTime() * .22 * sign;
            EnumFacing.Axis axis = kinetic.axis();
            for (int i = 0; i < 4; i++) {
                double a = angle + Math.PI * i / 2, c = Math.cos(a) * .65, s = Math.sin(a) * .65;
                p.scene.getScene().getWorld().spawnParticle(EnumParticleTypes.REDSTONE, at.getX() + .5 + (axis == EnumFacing.Axis.X ? 0 : c), at.getY() + .5 + (axis == EnumFacing.Axis.Y ? 0 : axis == EnumFacing.Axis.X ? c : s), at.getZ() + .5 + (axis == EnumFacing.Axis.Z ? 0 : s), sign > 0 ? .92 : .08, .37, sign > 0 ? .04 : .65);
            }
        });
        return null;
    }
    static EnumParticleTypes particle(String id) {
        return switch (tail(id).toLowerCase(Locale.ROOT)) {
            case "poof" -> EnumParticleTypes.EXPLOSION_NORMAL; case "cloud" -> EnumParticleTypes.CLOUD;
            case "splash" -> EnumParticleTypes.WATER_SPLASH; case "bubble" -> EnumParticleTypes.WATER_BUBBLE;
            case "item_slime" -> EnumParticleTypes.SLIME; case "dust", "redstone" -> EnumParticleTypes.REDSTONE;
            case "happy_villager" -> EnumParticleTypes.VILLAGER_HAPPY; case "smoke" -> EnumParticleTypes.SMOKE_NORMAL;
            default -> { try { yield EnumParticleTypes.valueOf(tail(id).toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { yield EnumParticleTypes.CRIT; } }
        };
    }
    static Object special(PonderProgram p, String op, Object[] a) {
        if (op.equals("createBirb")) {
            PonderParrot parrot = new PonderParrot(vec(a[0]), () -> pose(p, a[1]), false);
            ElementLink<ParrotElement> link = new net.createmod.ponder.foundation.element.ElementLinkImpl<>(ParrotElement.class);
            p.scene.addInstruction(s -> { s.addElement(parrot); s.linkElement(parrot, link); parrot.setVisible(true); });
            return link;
        }
        if (op.equals("changeBirbPose")) { p.scene.special().changeBirbPose((ElementLink<ParrotElement>) a[0], () -> pose(p, a[1])); return null; }
        if (op.equals("conductorBirb")) {
            p.run(op, () -> { ParrotElement parrot = p.scene.getScene().resolve((ElementLink<ParrotElement>) a[0]); if (parrot != null) PonderTile.conductor(parrot, truth(a[1])); }); return null;
        }
        if (op.equals("createCart")) {
            return p.scene.special().createCart(vec(a[0]), (float) num(a[1]), (world, x, y, z) -> {
                String description = String.valueOf(a[2]);
                return description.contains("Furnace") ? new net.minecraft.entity.item.EntityMinecartFurnace(world, x, y, z)
                        : description.contains("Chest") ? new net.minecraft.entity.item.EntityMinecartChest(world, x, y, z) : new net.minecraft.entity.item.EntityMinecartEmpty(world, x, y, z);
            });
        }
        throw new IllegalArgumentException(op);
    }
    private static ParrotPose pose(PonderProgram p, Object value) {
        if (value instanceof PonderValues.Callback callback) value = callback.apply();
        if (value instanceof Construct ref && ref.name().equals("methodref")) value = p.values.methodReference(ref);
        if (value instanceof ParrotPose pose) return pose;
        String name = String.valueOf(value);
        return name.contains("Flappy") ? new ParrotPose.FlappyPose() : name.contains("Dance") ? new ParrotPose.DancePose() : new ParrotPose.FacePointOfInterestPose();
    }
    static ItemStack parcel(Object[] contents) {
        ItemStackHandler items = new ItemStackHandler(9); int slot = 0;
        List<Object> stacks = contents.length == 1 && contents[0] instanceof List<?> list ? new ArrayList<>(list) : Arrays.asList(contents);
        for (Object content : stacks) if (content instanceof ItemStack stack && slot < 9) items.setStackInSlot(slot++, stack.copy());
        return PackageItem.create(items, "", new Random(1));
    }
    static final class BeltRef {
        final PonderProgram p; final BlockPos initial; final EnumFacing insertion; final ItemStack original;
        final int id;
        BlockPos current; ItemStack item; float progress; boolean stalled, removed;
        BeltRef(PonderProgram p, BlockPos pos, ItemStack item, EnumFacing insertion) { this.p = p; initial = pos; original = item.copy(); this.insertion = insertion; id = p.nextBeltItemId++; p.beltItems.add(this); reset(); }
        void reset() { current = initial; item = original.copy(); progress = .5F; stalled = false; removed = true; }
        void start() { reset(); removed = false; render(); }
        Object[] capture() { return new Object[]{current, item.copy(), progress, stalled, removed}; }
        void restore(Object[] data) { current = (BlockPos) data[0]; item = ((ItemStack) data[1]).copy(); progress = (Float) data[2]; stalled = (Boolean) data[3]; removed = (Boolean) data[4]; }
        void remove() { removed = true; TileEntity tile = p.scene.getScene().getWorld().getTileEntity(current); if (tile instanceof BeltTileEntity belt) belt.setTutorialItem(id, ItemStack.EMPTY, 0, 0); }
        void render() {
            if (removed) return;
            TileEntity tile = p.scene.getScene().getWorld().getTileEntity(current);
            if (tile instanceof BeltTileEntity belt) {
                BeltTileEntity.TutorialItem visible = belt.tutorialItem(id);
                float previous = visible == null ? progress : visible.position();
                belt.setTutorialItem(id, item, previous, progress);
            }
        }
        void tick() {
            if (removed) return;
            TileEntity tile = p.scene.getScene().getWorld().getTileEntity(current);
            if (!(tile instanceof BeltTileEntity belt)) { remove(); return; }
            if (!stalled) {
                BlockPos step = belt.step();
                float direction = step.getX() != 0 ? -Math.signum(step.getX()) : Math.signum(step.getZ());
                progress += belt.getSpeed() * direction / 480F;
                if (progress > 1 || progress < 0) {
                    int sign = progress > 1 ? 1 : -1;
                    BlockPos next = current.add(step.getX() * sign, step.getY() * sign, step.getZ() * sign);
                    var world = p.scene.getScene().getWorld();
                    if (world.getTileEntity(next) instanceof BeltTileEntity) {
                        belt.setTutorialItem(id, ItemStack.EMPTY, 0, 0);
                        current = next; progress -= sign;
                    } else {
                        TileEntity receiver = world.getTileEntity(current.up());
                        if (!(receiver instanceof com.cogwork.cogworkbridge.content.logistics.funnel.FunnelTileEntity))
                            receiver = world.getTileEntity(next);
                        if (receiver != null) {
                            item = new PonderTile(p, receiver).insertFromBelt(item, EnumFacing.getFacingFromVector(
                                    step.getX() * sign, step.getY() * sign, step.getZ() * sign));
                            if (item.isEmpty()) { remove(); return; }
                            progress = sign > 0 ? 1 : 0;
                        } else { remove(); return; }
                    }
                }
            }
            render();
        }
    }
}
