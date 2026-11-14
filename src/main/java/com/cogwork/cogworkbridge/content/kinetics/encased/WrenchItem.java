package com.cogwork.cogworkbridge.content.kinetics.encased;

import com.cogwork.cogworkbridge.content.contraptions.actors.AttachedActorBlock;
import com.cogwork.cogworkbridge.content.contraptions.chassis.ChassisBlock;
import com.cogwork.cogworkbridge.content.contraptions.chassis.ChassisTileEntity;
import com.cogwork.cogworkbridge.content.contraptions.chassis.StickerBlock;
import com.cogwork.cogworkbridge.content.contraptions.bearing.MechanicalBearingTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.chain.ChainDriveBlock;
import com.cogwork.cogworkbridge.content.kinetics.chain.ChainDriveTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.cogwheel.CogwheelBlock;
import com.cogwork.cogworkbridge.content.kinetics.crusher.CrushingWheelBlock;
import com.cogwork.cogworkbridge.content.kinetics.crusher.CrushingWheelControllerBlock;
import com.cogwork.cogworkbridge.content.kinetics.deployer.DeployerBlock;
import com.cogwork.cogworkbridge.content.kinetics.fan.NozzleBlock;
import com.cogwork.cogworkbridge.content.kinetics.placement.KineticPlacementRules;
import com.cogwork.cogworkbridge.content.kinetics.saw.MechanicalSawBlock;
import com.cogwork.cogworkbridge.content.kinetics.saw.MechanicalSawTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.sequencer.SequencedGearshiftBlock;
import com.cogwork.cogworkbridge.content.kinetics.speedController.SpeedControllerBlock;
import com.cogwork.cogworkbridge.content.fluids.MechanicalPumpBlock;
import com.cogwork.cogworkbridge.content.fluids.FluidTransferManager;
import com.cogwork.cogworkbridge.content.fluids.FluidTankBlock;
import com.cogwork.cogworkbridge.content.fluids.FluidTankTileEntity;
import com.cogwork.cogworkbridge.content.fluids.FluidValveBlock;
import com.cogwork.cogworkbridge.content.fluids.FluidPipeBlock;
import com.cogwork.cogworkbridge.content.fluids.FluidPipeTileEntity;
import com.cogwork.cogworkbridge.content.fluids.SmartFluidPipeBlock;
import com.cogwork.cogworkbridge.content.fluids.GlassFluidPipeBlock;
import com.cogwork.cogworkbridge.content.fluids.EncasedFluidPipeBlock;
import com.cogwork.cogworkbridge.content.processing.basin.BasinBlock;
import com.cogwork.cogworkbridge.content.processing.basin.BasinTileEntity;
import com.cogwork.cogworkbridge.content.processing.burner.BlazeBurnerBlock;
import com.cogwork.cogworkbridge.content.processing.crafter.MechanicalCrafterBlock;
import com.cogwork.cogworkbridge.content.processing.press.MechanicalPressBlock;
import com.cogwork.cogworkbridge.content.logistics.crate.CreativeCrateBlock;
import com.cogwork.cogworkbridge.content.logistics.portable.PortableStorageInterfaceBlock;
import com.cogwork.cogworkbridge.content.logistics.chute.ChuteBlock;
import com.cogwork.cogworkbridge.content.logistics.chute.ChuteTileEntity;
import com.cogwork.cogworkbridge.content.logistics.funnel.FunnelBlock;
import com.cogwork.cogworkbridge.content.logistics.funnel.FunnelTileEntity;
import com.cogwork.cogworkbridge.content.logistics.vault.ItemVaultBlock;
import com.cogwork.cogworkbridge.content.logistics.vault.VaultConnectivity;
import com.cogwork.cogworkbridge.content.logistics.hatch.ItemHatchBlock;
import com.cogwork.cogworkbridge.content.logistics.ejector.EjectorBlock;
import com.cogwork.cogworkbridge.content.logistics.tunnel.BeltTunnelBlock;
import com.cogwork.cogworkbridge.content.logistics.tunnel.BeltTunnelTileEntity;
import com.cogwork.cogworkbridge.content.logistics.belt.BeltBlock;
import com.cogwork.cogworkbridge.content.logistics.belt.BeltTileEntity;
import com.cogwork.cogworkbridge.content.logistics.belt.BeltSlicer;
import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import com.cogwork.cogworkbridge.kinetic.KineticNetworkManager;
import com.cogwork.cogworkbridge.kinetic.KineticTileEntity;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.registry.ModCreativeTab;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.Rotation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.items.ItemHandlerHelper;

/** Rotates or picks up Create devices, with machine-specific wrench actions. */
public final class WrenchItem extends Item {
    public WrenchItem() {
        setMaxStackSize(1);
        setCreativeTab(ModCreativeTab.INSTANCE);
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos,
                                            EnumFacing face, float hitX, float hitY, float hitZ,
                                            EnumHand hand) {
        return useWrench(player, world, pos, hand, face, hitX, hitY, hitZ);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
                                      EnumFacing face, float hitX, float hitY, float hitZ) {
        return useWrench(player, world, pos, hand, face, hitX, hitY, hitZ);
    }

    private EnumActionResult useWrench(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
                                       EnumFacing face, float hitX, float hitY, float hitZ) {
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        if (block instanceof com.cogwork.cogworkbridge.content.fluids.whistle.SteamWhistleBlock || block instanceof com.cogwork.cogworkbridge.content.fluids.whistle.WhistleExtensionBlock) {
            if (player.isSpectator() || !player.canPlayerEdit(pos,face,player.getHeldItem(hand)) || !world.isBlockModifiable(player,pos)) return EnumActionResult.FAIL;
            if (!world.isRemote) {
                if (player.isSneaking() && world.getTileEntity(pos) instanceof com.cogwork.cogworkbridge.content.fluids.whistle.WhistleExtensionTileEntity e && e.isDouble() && hitY >= .5F) e.removeHalf();
                else if (player.isSneaking()) world.destroyBlock(pos, block instanceof com.cogwork.cogworkbridge.content.fluids.whistle.SteamWhistleBlock);
                else { BlockPos root=com.cogwork.cogworkbridge.content.fluids.whistle.WhistleExtensionBlock.root(world,pos); if(world.getTileEntity(root) instanceof com.cogwork.cogworkbridge.content.fluids.whistle.SteamWhistleTileEntity t)t.cycleSize(); }
            }
            return EnumActionResult.SUCCESS;
        }
        com.cogwork.cogworkbridge.content.decoration.palette.BracketAttachment bracket =
                com.cogwork.cogworkbridge.content.decoration.palette.BracketAttachment.of(world.getTileEntity(pos));
        if (bracket != null && bracket.present()) {
            if (player.isSpectator() || !player.canPlayerEdit(pos, face, player.getHeldItem(hand)) || !world.isBlockModifiable(player, pos)) return EnumActionResult.FAIL;
            if (!world.isRemote) com.cogwork.cogworkbridge.content.decoration.palette.BracketAttachment.remove(world.getTileEntity(pos), player);
            if (!player.isSneaking()) return EnumActionResult.SUCCESS;
        }
        boolean casing = block == AllBlocks.ANDESITE_CASING || block == AllBlocks.BRASS_CASING;
        if (!(block instanceof KineticBlock || block instanceof ChassisBlock || block instanceof StickerBlock || block instanceof AttachedActorBlock
                || block instanceof BasinBlock || block instanceof BlazeBurnerBlock
                || block instanceof NozzleBlock || block instanceof FluidTankBlock
                || block instanceof FluidPipeBlock || block instanceof CreativeCrateBlock
                || block instanceof PortableStorageInterfaceBlock || block instanceof ChuteBlock
                || block instanceof FunnelBlock || block instanceof ItemVaultBlock || block instanceof ItemHatchBlock
                || block instanceof BeltTunnelBlock || block instanceof EjectorBlock
                || block instanceof com.cogwork.cogworkbridge.content.contraptions.mounted.CartAssemblerBlock
                || block instanceof com.cogwork.cogworkbridge.content.contraptions.mounted.ControllerRailBlock
                || block instanceof com.cogwork.cogworkbridge.content.contraptions.elevator.RedstoneContactBlock
                || block instanceof com.cogwork.cogworkbridge.content.redstone.RedstoneLinkBlock
                || block instanceof com.cogwork.cogworkbridge.content.redstone.RoseQuartzLampBlock
                || block instanceof com.cogwork.cogworkbridge.content.redstone.DeskBellBlock
                || block instanceof com.cogwork.cogworkbridge.content.redstone.AnalogLeverBlock
                || block instanceof com.cogwork.cogworkbridge.content.redstone.DetectorBlock
                || block instanceof com.cogwork.cogworkbridge.content.redstone.PlacardBlock
                || block instanceof com.cogwork.cogworkbridge.content.logistics.packager.PackagerBlock
                || block instanceof com.cogwork.cogworkbridge.content.logistics.packager.StockLinkBlock
                || block instanceof com.cogwork.cogworkbridge.content.logistics.packager.StockTickerBlock
                || block instanceof com.cogwork.cogworkbridge.content.redstone.PulseDiodeBlock
                || block instanceof com.cogwork.cogworkbridge.content.redstone.LatchBlock
                || block instanceof com.cogwork.cogworkbridge.content.decoration.MetalScaffoldingBlock
                || block instanceof com.cogwork.cogworkbridge.content.equipment.BellBlock
                || casing)) return EnumActionResult.PASS;
        if (player.isSpectator() || !player.canPlayerEdit(pos, face, player.getHeldItem(hand))
                || !world.isBlockModifiable(player, pos)) return EnumActionResult.FAIL;

        if (block instanceof com.cogwork.cogworkbridge.content.decoration.palette.GirderShaftBlock) {
            if (!world.isRemote) {
                EnumFacing.Axis shaftAxis = state.getValue(KineticBlock.AXIS);
                if (player.isSneaking()) {
                    if (world.setBlockToAir(pos) && !player.capabilities.isCreativeMode) {
                        ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(com.cogwork.cogworkbridge.registry.BlockFamily.get("metal_girder")));
                        ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(AllBlocks.SHAFT));
                    }
                } else {
                    Block girder = com.cogwork.cogworkbridge.registry.BlockFamily.get("metal_girder");
                    world.setBlockState(pos, girder.getDefaultState().withProperty(com.cogwork.cogworkbridge.content.decoration.palette.GirderBlock.AXIS,
                            shaftAxis == EnumFacing.Axis.X ? EnumFacing.Axis.Z : EnumFacing.Axis.X), 3);
                    if (!player.capabilities.isCreativeMode) ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(AllBlocks.SHAFT));
                }
            }
            return EnumActionResult.SUCCESS;
        }

        if (block instanceof com.cogwork.cogworkbridge.content.kinetics.waterwheel.LargeWaterWheelBlock && !player.isSneaking()) return EnumActionResult.PASS;

        if (player.isSneaking()) {
            if (block instanceof com.cogwork.cogworkbridge.content.contraptions.mounted.CartAssemblerBlock
                    && world.getTileEntity(pos) instanceof com.cogwork.cogworkbridge.content.contraptions.mounted.CartAssemblerTileEntity assembler) {
                if (world.isRemote) return EnumActionResult.SUCCESS;
                BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(world, pos, state, player);
                if (MinecraftForge.EVENT_BUS.post(event)) return EnumActionResult.FAIL;
                IBlockState rail = assembler.getRailState(state);
                if (!world.setBlockState(pos, rail, 3)) return EnumActionResult.FAIL;
                if (!player.capabilities.isCreativeMode) ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(AllBlocks.CART_ASSEMBLER));
                return EnumActionResult.SUCCESS;
            }
            if (block instanceof EncasedCogwheelBlock || block instanceof EncasedShaftBlock) {
                return Encasing.tryRemove(world, pos, state) ? EnumActionResult.SUCCESS : EnumActionResult.FAIL;
            }
            return pickUp(player, world, pos, state);
        }

        if (block instanceof com.cogwork.cogworkbridge.content.kinetics.chainconveyor.ChainConveyorBlock)
            return EnumActionResult.PASS;

        if (block instanceof com.cogwork.cogworkbridge.content.contraptions.mounted.ControllerRailBlock rail) {
            if (!world.isRemote) world.setBlockState(pos, rail.wrenchState(state, world, pos), 3);
            return EnumActionResult.SUCCESS;
        }
        if (block instanceof com.cogwork.cogworkbridge.content.contraptions.mounted.CartAssemblerBlock) {
            if (!world.isRemote) {
                var oldShape = state.getValue(com.cogwork.cogworkbridge.content.contraptions.mounted.CartAssemblerBlock.SHAPE);
                var nextShape = oldShape == net.minecraft.block.BlockRailBase.EnumRailDirection.EAST_WEST
                        ? net.minecraft.block.BlockRailBase.EnumRailDirection.NORTH_SOUTH : net.minecraft.block.BlockRailBase.EnumRailDirection.EAST_WEST;
                setRotatedState(world, pos, state, state.withProperty(
                        com.cogwork.cogworkbridge.content.contraptions.mounted.CartAssemblerBlock.SHAPE, nextShape));
            }
            return EnumActionResult.SUCCESS;
        }

        if (block instanceof com.cogwork.cogworkbridge.content.redstone.RedstoneLinkBlock) {
            if (!world.isRemote && world.getTileEntity(pos) instanceof com.cogwork.cogworkbridge.content.redstone.RedstoneLinkTileEntity link)
                link.toggleReceiver();
            return EnumActionResult.SUCCESS;
        }
        if (block instanceof com.cogwork.cogworkbridge.content.redstone.RoseQuartzLampBlock) {
            if (!world.isRemote && world.getTileEntity(pos) instanceof com.cogwork.cogworkbridge.content.redstone.RoseQuartzLampTileEntity lamp) lamp.wrenchToggle();
            return EnumActionResult.SUCCESS;
        }

        if (casing) return EnumActionResult.PASS;
        if (block instanceof BeltBlock) {
            if (!(world.getTileEntity(pos) instanceof BeltTileEntity belt)) return EnumActionResult.FAIL;
            if (belt.casing() != BeltTileEntity.CasingType.NONE && face != EnumFacing.UP) {
                if (!world.isRemote) belt.removeCasing();
                return EnumActionResult.SUCCESS;
            }
            if (belt.hasPulley() && belt.index() > 0 && belt.index() + 1 < belt.length()
                    && face.getAxis() == ((BeltBlock) block).getAxis(state)) {
                if (!world.isRemote) {
                    belt.setPulley(false);
                    if (!player.capabilities.isCreativeMode)
                        ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(AllBlocks.SHAFT));
                    KineticNetworkManager.discard(world);
                }
                return EnumActionResult.SUCCESS;
            }
            return BeltSlicer.shortenOrSplit(world, pos, player, hand, face, hitX, hitY, hitZ);
        }
        if (block instanceof BeltTunnelBlock) {
            if (!world.isRemote && world.getTileEntity(pos) instanceof BeltTunnelTileEntity tunnel) tunnel.toggleWindow();
            return EnumActionResult.SUCCESS;
        }
        if (block instanceof ItemVaultBlock) {
            if (face.getAxis() != EnumFacing.Axis.Y) return EnumActionResult.SUCCESS;
            if (!world.isRemote) {
                VaultConnectivity.split(world, pos);
                IBlockState current = world.getBlockState(pos);
                setRotatedState(world, pos, current, current.withRotation(Rotation.CLOCKWISE_90));
            }
            return EnumActionResult.SUCCESS;
        }
        if (block instanceof FunnelBlock) {
            if (!world.isRemote && world.getTileEntity(pos) instanceof FunnelTileEntity funnel) {
                BeltTileEntity belt = funnel.alignedBelt();
                if (belt != null) {
                    if (belt.step().getY() == 0) {
                        funnel.toggleExtended();
                        EnumFacing orientation = state.getValue(FunnelBlock.FACING);
                        if (funnel.isExtended() && world.getTileEntity(pos.offset(orientation)) instanceof FunnelTileEntity other
                                && other.isExtended() && other.alignedBelt() != null
                                && world.getBlockState(other.getPos()).getValue(FunnelBlock.FACING) == orientation.getOpposite())
                            com.cogwork.cogworkbridge.content.advancement.AllAdvancements.award(player, "belt_funnel_kiss");
                    }
                } else if (funnel.beltBelow() == null) funnel.toggleExtraction();
            }
            return EnumActionResult.SUCCESS;
        }
        if (block instanceof ChuteBlock) {
            if (!world.isRemote && world.getTileEntity(pos) instanceof ChuteTileEntity chute) chute.toggleWindow();
            return EnumActionResult.SUCCESS;
        }
        if (block instanceof FluidPipeBlock && !(block instanceof com.cogwork.cogworkbridge.content.fluids.SmartFluidPipeBlock)) {
            boolean hasFlowForAdvancement = world.getTileEntity(pos) instanceof FluidPipeTileEntity flowing
                    && java.util.Arrays.stream(EnumFacing.VALUES).anyMatch(direction -> flowing.getFlow(direction, 1F) != null);
            if (block instanceof GlassFluidPipeBlock || block instanceof EncasedFluidPipeBlock) {
                if (!world.isRemote && !FluidPipeBlock.replace(world, pos, AllBlocks.FLUID_PIPE.getDefaultState())) {
                    return EnumActionResult.FAIL;
                }
                if (!world.isRemote && hasFlowForAdvancement) com.cogwork.cogworkbridge.content.advancement.AllAdvancements.award(player, "glass_pipe");
                return EnumActionResult.SUCCESS;
            }
            TileEntity tile = world.getTileEntity(pos);
            EnumFacing.Axis axis = tile instanceof FluidPipeTileEntity pipe ? pipe.getStraightAxis() : null;
            if (axis == null) {
                double closest = Double.MAX_VALUE;
                for (EnumFacing direction : EnumFacing.values()) {
                    if (direction.getAxis() == face.getAxis()) continue;
                    double dx = hitX - (0.5D + direction.getXOffset() * 0.5D);
                    double dy = hitY - (0.5D + direction.getYOffset() * 0.5D);
                    double dz = hitZ - (0.5D + direction.getZOffset() * 0.5D);
                    double distance = dx * dx + dy * dy + dz * dz;
                    if (distance < closest) { closest = distance; axis = direction.getAxis(); }
                }
            }
            if (axis == null || axis == face.getAxis()) return EnumActionResult.PASS;
            if (!world.isRemote && !FluidPipeBlock.replace(world, pos,
                    AllBlocks.GLASS_FLUID_PIPE.getDefaultState().withProperty(GlassFluidPipeBlock.AXIS, axis))) {
                return EnumActionResult.FAIL;
            }
            if (!world.isRemote && hasFlowForAdvancement) com.cogwork.cogworkbridge.content.advancement.AllAdvancements.award(player, "glass_pipe");
                return EnumActionResult.SUCCESS;
        }
        if (block instanceof AttachedActorBlock || block instanceof NozzleBlock) return EnumActionResult.FAIL;
        if (block instanceof BasinBlock) {
            if (!world.isRemote && world.getTileEntity(pos) instanceof BasinTileEntity basin) basin.wrench(face);
            return EnumActionResult.SUCCESS;
        }
        if (block instanceof FluidTankBlock) {
            if (!world.isRemote && world.getTileEntity(pos) instanceof FluidTankTileEntity tank) {
                tank.toggleWindows();
                playRotateSound(world, pos);
            }
            return EnumActionResult.SUCCESS;
        }
        if (block instanceof MechanicalPumpBlock) {
            if (!world.isRemote) {
                IBlockState reversed = state.withProperty(MechanicalPumpBlock.FACING,
                        state.getValue(MechanicalPumpBlock.FACING).getOpposite());
                if (setRotatedState(world, pos, state, reversed)) {
                    FluidTransferManager.markDirty(world);
                    playRotateSound(world, pos);
                }
            }
            return EnumActionResult.SUCCESS;
        }
        if (block instanceof MechanicalCrafterBlock crafter) {
            return crafter.wrench(world, pos, state, face) ? EnumActionResult.SUCCESS : EnumActionResult.PASS;
        }
        if (block instanceof DeployerBlock deployer && deployer.wrench(world, pos, state, hitX, hitY, hitZ)) {
            return EnumActionResult.SUCCESS;
        }
        if (block instanceof EncasedCogwheelBlock cog && face.getAxis() == cog.getAxis(state)) {
            if (!world.isRemote && world.getTileEntity(pos) instanceof EncasedCogwheelTileEntity tile) {
                tile.toggle(face);
                playRotateSound(world, pos);
            }
            return EnumActionResult.SUCCESS;
        }

        EnumFacing.Axis around = face.getAxis();
        if (block instanceof ChainDriveBlock && world.getTileEntity(pos) instanceof ChainDriveTileEntity chain) {
            for (EnumFacing side : EnumFacing.VALUES) {
                if (chain.connects(side)) {
                    around = side.getAxis();
                    break;
                }
            }
        }
        IBlockState rotated = rotatedState(state, around);
        if (rotated == state) {
            if (block instanceof ChassisBlock && !world.isRemote
                    && world.getTileEntity(pos) instanceof ChassisTileEntity chassis) chassis.rotateSticky(around);
            return EnumActionResult.SUCCESS;
        }
        return applyRotatedState(world, pos, state, rotated, java.util.Collections.singletonList(around))
                ? EnumActionResult.SUCCESS : EnumActionResult.FAIL;
    }

    /** Apply a menu choice through the same safety checks and network updates as the wrench. */
    public static boolean applyMenuRotation(World world, BlockPos pos, IBlockState state, IBlockState selected) {
        if (world.isRemote || state.getBlock() != selected.getBlock() || state == selected) return false;
        java.util.List<EnumFacing.Axis> turns = findRotationPath(state, selected);
        // For properties which are independent of physical rotation, the state can be
        // changed directly. Chassis and encased cog ports require a real rotation path.
        if (turns == null) {
            TileEntity tile = world.getTileEntity(pos);
            if (tile instanceof ChassisTileEntity || tile instanceof EncasedCogwheelTileEntity) return false;
            turns = java.util.Collections.emptyList();
        }
        return applyRotatedState(world, pos, state, selected, turns);
    }

    private static java.util.List<EnumFacing.Axis> findRotationPath(IBlockState original, IBlockState target) {
        java.util.ArrayDeque<IBlockState> queue = new java.util.ArrayDeque<>();
        java.util.Map<IBlockState, java.util.List<EnumFacing.Axis>> paths = new java.util.HashMap<>();
        queue.add(original);
        paths.put(original, java.util.Collections.emptyList());
        while (!queue.isEmpty()) {
            IBlockState current = queue.remove();
            java.util.List<EnumFacing.Axis> path = paths.get(current);
            if (path.size() >= 6) continue;
            for (EnumFacing.Axis axis : EnumFacing.Axis.values()) {
                IBlockState next = rotatedState(current, axis);
                if (paths.containsKey(next)) continue;
                java.util.List<EnumFacing.Axis> nextPath = new java.util.ArrayList<>(path);
                nextPath.add(axis);
                if (next.equals(target)) return nextPath;
                paths.put(next, nextPath);
                queue.add(next);
            }
        }
        return null;
    }

    private static boolean applyRotatedState(World world, BlockPos pos, IBlockState state,
                                             IBlockState rotated, java.util.List<EnumFacing.Axis> turns) {
        Block block = state.getBlock();
        if (block instanceof CogwheelBlock cog
                && !KineticPlacementRules.validCogwheelPosition(world, pos,
                cog.getAxis(rotated), cog.isLarge())) return false;
        if (block instanceof CrushingWheelBlock && !validCrushingWheelRotation(world, pos, rotated)) return false;
        if (block instanceof com.cogwork.cogworkbridge.content.redstone.PlacardBlock placard
                && !placard.canPlaceBlockOnSide(world, pos, com.cogwork.cogworkbridge.content.redstone.PlacardBlock.outward(rotated)))
            return false;
        if (world.isRemote) return true;
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof MechanicalBearingTileEntity bearing && !bearing.emergencyDisassemble()) return false;
        if (!setRotatedState(world, pos, state, rotated)) return false;
        tile = world.getTileEntity(pos);
        IBlockState step = state;
        for (EnumFacing.Axis around : turns) {
            if (tile instanceof ChassisTileEntity chassis) chassis.rotateSticky(around);
            if (tile instanceof EncasedCogwheelTileEntity encased) encased.rotatePorts(around);
            if (block instanceof MechanicalSawBlock && around == EnumFacing.Axis.Y
                    && step.getValue(MechanicalSawBlock.FACING).getAxis() == EnumFacing.Axis.Y
                    && step.getValue(MechanicalSawBlock.SHAFT_AXIS) == EnumFacing.Axis.Z
                    && world.getTileEntity(pos) instanceof MechanicalSawTileEntity saw) {
                saw.setFlipped(!saw.isFlipped());
            }
            step = rotatedState(step, around);
        }
        if (world.getTileEntity(pos) instanceof KineticTileEntity kinetic) kinetic.refreshKinetics();
        if (block instanceof FluidValveBlock || block instanceof MechanicalPumpBlock) FluidTransferManager.markDirty(world);
        if (block instanceof SmartFluidPipeBlock) {
            if (world.getTileEntity(pos) instanceof FluidPipeTileEntity pipe) pipe.markConnectionsDirty();
            FluidTransferManager.markDirty(world);
        }
        KineticNetworkManager.discard(world);
        if (block instanceof CrushingWheelBlock) {
            CrushingWheelBlock.updateControllers(world, pos, rotated);
            for (EnumFacing side : EnumFacing.VALUES) {
                BlockPos otherPos = pos.offset(side, 2);
                if (!world.isBlockLoaded(otherPos)) continue;
                IBlockState other = world.getBlockState(otherPos);
                if (other.getBlock() instanceof CrushingWheelBlock)
                    CrushingWheelBlock.updateControllers(world, otherPos, other);
            }
        }
        playRotateSound(world, pos);
        return true;
    }

    private static boolean setRotatedState(World world, BlockPos pos, IBlockState state, IBlockState rotated) {
        TileEntity original = world.getTileEntity(pos);
        NBTTagCompound saved = null;
        if (original != null && original.shouldRefresh(world, pos, state, rotated)) {
            if (state.getBlock() != rotated.getBlock() || !rotated.getBlock().hasTileEntity(rotated)) return false;
            TileEntity replacement = rotated.getBlock().createTileEntity(world, rotated);
            if (replacement == null || replacement.getClass() != original.getClass()) return false;
            saved = original.writeToNBT(new NBTTagCompound());
        }
        if (!world.setBlockState(pos, rotated, 3)) return false;
        if (saved != null) {
            TileEntity replacement = world.getTileEntity(pos);
            if (replacement != null && replacement.getClass() == original.getClass()) {
                replacement.readFromNBT(saved);
                replacement.markDirty();
                world.notifyBlockUpdate(pos, rotated, rotated, 2);
            }
        }
        return true;
    }

    private static IBlockState rotatedState(IBlockState state, EnumFacing.Axis around) {
        if (state.getBlock() instanceof com.cogwork.cogworkbridge.content.redstone.DetectorBlock) {
            EnumFacing target = com.cogwork.cogworkbridge.content.redstone.DetectorBlock.target(state);
            if (target.getAxis() == around) return state;
            return com.cogwork.cogworkbridge.content.redstone.DetectorBlock.withTarget(state, target.rotateAround(around));
        }
        if (state.getBlock() instanceof com.cogwork.cogworkbridge.content.redstone.PlacardBlock) {
            EnumFacing outward = com.cogwork.cogworkbridge.content.redstone.PlacardBlock.outward(state);
            if (outward.getAxis() == around) {
                return around == EnumFacing.Axis.Y ? state.withRotation(Rotation.CLOCKWISE_90) : state;
            }
            EnumFacing next = outward.rotateAround(around);
            var mount = next == EnumFacing.UP ? com.cogwork.cogworkbridge.content.redstone.PlacardBlock.Mount.FLOOR
                    : next == EnumFacing.DOWN ? com.cogwork.cogworkbridge.content.redstone.PlacardBlock.Mount.CEILING
                    : com.cogwork.cogworkbridge.content.redstone.PlacardBlock.Mount.WALL;
            IBlockState rotated = state.withProperty(com.cogwork.cogworkbridge.content.redstone.PlacardBlock.MOUNT, mount);
            return next.getAxis().isHorizontal() ? rotated.withProperty(com.cogwork.cogworkbridge.content.redstone.PlacardBlock.FACING, next) : rotated;
        }
        if (state.getBlock() instanceof com.cogwork.cogworkbridge.content.redstone.AnalogLeverBlock) {
            EnumFacing outward = com.cogwork.cogworkbridge.content.redstone.AnalogLeverBlock.connectedDirection(state);
            if (outward.getAxis() == around) {
                return around == EnumFacing.Axis.Y ? state.withRotation(Rotation.CLOCKWISE_90) : state;
            }
            EnumFacing next = outward.rotateAround(around);
            var mount = next == EnumFacing.UP ? com.cogwork.cogworkbridge.content.redstone.AnalogLeverBlock.Mount.FLOOR
                    : next == EnumFacing.DOWN ? com.cogwork.cogworkbridge.content.redstone.AnalogLeverBlock.Mount.CEILING
                    : com.cogwork.cogworkbridge.content.redstone.AnalogLeverBlock.Mount.WALL;
            IBlockState rotated = state.withProperty(com.cogwork.cogworkbridge.content.redstone.AnalogLeverBlock.MOUNT, mount);
            return next.getAxis().isHorizontal() ? rotated.withProperty(com.cogwork.cogworkbridge.content.redstone.AnalogLeverBlock.FACING, next) : rotated;
        }
        if (state.getBlock() instanceof SmartFluidPipeBlock) {
            return SmartFluidPipeBlock.rotateAround(state, around);
        }
        if (state.getBlock() instanceof FluidValveBlock) {
            return FluidValveBlock.rotateAround(state, around);
        }
        if (state.getBlock() instanceof SequencedGearshiftBlock) {
            return SequencedGearshiftBlock.wrenchedState(state, around);
        }
        if (state.getBlock() instanceof DeployerBlock) {
            EnumFacing facing = state.getValue(DeployerBlock.FACING);
            EnumFacing.Axis axis = DeployerBlock.rotationAxis(facing,
                    state.getValue(DeployerBlock.AXIS_ALONG_FIRST_COORDINATE));
            if (facing.getAxis() == around) return state.cycleProperty(DeployerBlock.AXIS_ALONG_FIRST_COORDINATE);
            EnumFacing nextFacing = facing.rotateAround(around);
            EnumFacing.Axis nextAxis = rotateAxis(axis, around);
            EnumFacing.Axis alignment = DeployerBlock.rotationAxis(nextFacing, EnumFacing.Axis.X) == nextAxis
                    ? EnumFacing.Axis.X : EnumFacing.Axis.Z;
            return state.withProperty(DeployerBlock.FACING, nextFacing)
                    .withProperty(DeployerBlock.AXIS_ALONG_FIRST_COORDINATE, alignment);
        }
        if (state.getBlock() instanceof MechanicalSawBlock) {
            EnumFacing facing = state.getValue(MechanicalSawBlock.FACING);
            if (facing.getAxis() == around) {
                return state.cycleProperty(MechanicalSawBlock.SHAFT_AXIS);
            }
            IBlockState rotated = state.withProperty(MechanicalSawBlock.FACING, facing.rotateAround(around));
            return around == EnumFacing.Axis.Y ? rotated.cycleProperty(MechanicalSawBlock.SHAFT_AXIS) : rotated;
        }
        if (state.getBlock() instanceof MechanicalPressBlock || state.getBlock() instanceof SpeedControllerBlock) {
            return around == EnumFacing.Axis.Y ? state.withRotation(Rotation.CLOCKWISE_90) : state;
        }
        if (state.getPropertyKeys().contains(KineticBlock.AXIS)) {
            EnumFacing.Axis axis = state.getValue(KineticBlock.AXIS);
            return state.withProperty(KineticBlock.AXIS, rotateAxis(axis, around));
        }
        for (IProperty<?> property : state.getPropertyKeys()) {
            if (!(property instanceof PropertyDirection direction) || !"facing".equals(property.getName())) continue;
            EnumFacing facing = state.getValue(direction);
            if (facing.getAxis() == around) return state;
            EnumFacing rotated = facing.rotateAround(around);
            return direction.getAllowedValues().contains(rotated) ? state.withProperty(direction, rotated) : state;
        }
        return state;
    }

    private static EnumFacing.Axis rotateAxis(EnumFacing.Axis axis, EnumFacing.Axis around) {
        return axis == around ? axis : EnumFacing.getFacingFromAxis(EnumFacing.AxisDirection.POSITIVE, axis)
                .rotateAround(around).getAxis();
    }

    private static boolean validCrushingWheelRotation(World world, BlockPos pos, IBlockState state) {
        EnumFacing.Axis axis = state.getValue(KineticBlock.AXIS);
        for (EnumFacing side : EnumFacing.VALUES) {
            BlockPos adjacentPos = pos.offset(side);
            if (!world.isBlockLoaded(adjacentPos)) continue;
            IBlockState adjacent = world.getBlockState(adjacentPos);
            if (adjacent.getBlock() instanceof CrushingWheelControllerBlock && side.getAxis() != axis) return false;
            if (adjacent.getBlock() instanceof CrushingWheelBlock
                    && (side.getAxis() != axis || adjacent.getValue(KineticBlock.AXIS) != axis)) return false;
        }
        return true;
    }

    private static EnumActionResult pickUp(EntityPlayer player, World world, BlockPos pos, IBlockState state) {
        if (world.isRemote) return EnumActionResult.SUCCESS;
        BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(world, pos, state, player);
        if (MinecraftForge.EVENT_BUS.post(event)) return EnumActionResult.FAIL;
        TileEntity tile = world.getTileEntity(pos);
        if (player.capabilities.isCreativeMode && tile instanceof com.cogwork.cogworkbridge.content.kinetics.chainconveyor.ChainConveyorTileEntity wheel)
            wheel.suppressChainDrops();
        if (tile instanceof MechanicalBearingTileEntity bearing && !bearing.emergencyDisassemble()) {
            return EnumActionResult.FAIL;
        }
        NonNullList<ItemStack> drops = NonNullList.create();
        if (!player.capabilities.isCreativeMode) state.getBlock().getDrops(drops, world, pos, state, 0);
        if (!world.setBlockToAir(pos)) return EnumActionResult.FAIL;
        for (ItemStack drop : drops) ItemHandlerHelper.giveItemToPlayer(player, drop);
        KineticNetworkManager.discard(world);
        world.playSound(null, pos, SoundEvents.BLOCK_ANVIL_PLACE, SoundCategory.BLOCKS, 0.35F, 1.4F);
        return EnumActionResult.SUCCESS;
    }

    private static void playRotateSound(World world, BlockPos pos) {
        world.playSound(null, pos, SoundEvents.BLOCK_ANVIL_PLACE, SoundCategory.BLOCKS, 0.25F, 1.7F);
    }
}
