package com.cogwork.cogworkbridge.content.decoration.palette;

import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import xy177.farmersfuturedelight.common.block.CopperWeathering;
import xy177.farmersfuturedelight.common.block.CopperWeathering.WeatherState;
import xy177.farmersfuturedelight.common.block.IWeatheringCopper;
import com.cogwork.cogworkbridge.content.decoration.ConnectedTextureData;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;

/** Cooperates with FFD's weathering, honeycomb, axe and lightning handling. */
public final class WeatheringPaletteBlocks {
    private WeatheringPaletteBlocks() { }

    public static class Cube extends Block implements IWeatheringCopper {
        private final WeatherState weather;
        private final boolean waxed;
        public Cube(WeatherState weather, boolean waxed) {
            super(Material.IRON); this.weather = weather; this.waxed = waxed;
            setHardness(3F); setResistance(6F); setHarvestLevel("pickaxe", 0); setTickRandomly(!waxed && weather != WeatherState.OXIDIZED);
        }
        @Override public WeatherState getWeatherState() { return weather; }
        @Override public boolean isWaxed() { return waxed; }
        @Override protected BlockStateContainer createBlockState() {
            return new ExtendedBlockState(this, new IProperty<?>[0], new IUnlistedProperty<?>[]{ConnectedTextureData.CONTEXT});
        }
        @Override public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
            return ((IExtendedBlockState) state).withProperty(ConnectedTextureData.CONTEXT, ConnectedTextureData.capture(world, pos, state));
        }
        @Override public void updateTick(World world, BlockPos pos, IBlockState state, Random random) { CopperWeathering.tryWeather(world, pos, state, random); }
    }

    public static class Stairs extends PaletteBlocks.Stairs implements IWeatheringCopper {
        private final WeatherState weather;
        private final boolean waxed;
        public Stairs(Block base, WeatherState weather, boolean waxed) {
            super(base); this.weather = weather; this.waxed = waxed;
            setTickRandomly(!waxed && weather != WeatherState.OXIDIZED);
        }
        @Override public WeatherState getWeatherState() { return weather; }
        @Override public boolean isWaxed() { return waxed; }
        @Override protected BlockStateContainer createBlockState() {
            return new ExtendedBlockState(this, new IProperty<?>[]{FACING, HALF, SHAPE}, new IUnlistedProperty<?>[]{ConnectedTextureData.CONTEXT});
        }
        @Override public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
            return ((IExtendedBlockState) state).withProperty(ConnectedTextureData.CONTEXT, ConnectedTextureData.capture(world, pos, state));
        }
        @Override public void updateTick(World world, BlockPos pos, IBlockState state, Random random) { CopperWeathering.tryWeather(world, pos, state, random); }
    }

    public static class Slab extends PaletteBlocks.Slab implements IWeatheringCopper {
        private final WeatherState weather;
        private final boolean waxed;
        public Slab(WeatherState weather, boolean waxed) {
            super(Material.IRON); this.weather = weather; this.waxed = waxed;
            setTickRandomly(!waxed && weather != WeatherState.OXIDIZED);
        }
        @Override public WeatherState getWeatherState() { return weather; }
        @Override public boolean isWaxed() { return waxed; }
        @Override protected BlockStateContainer createBlockState() {
            return new ExtendedBlockState(this, new IProperty<?>[]{BlockStairs.HALF}, new IUnlistedProperty<?>[]{ConnectedTextureData.CONTEXT});
        }
        @Override public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
            return ((IExtendedBlockState) state).withProperty(ConnectedTextureData.CONTEXT, ConnectedTextureData.capture(world, pos, state));
        }
        @Override public void updateTick(World world, BlockPos pos, IBlockState state, Random random) { CopperWeathering.tryWeather(world, pos, state, random); }
    }

    public static class DoubleSlab extends PaletteBlocks.DoubleSlab implements IWeatheringCopper {
        private final WeatherState weather;
        private final boolean waxed;
        public DoubleSlab(PaletteBlocks.Slab single, WeatherState weather, boolean waxed) {
            super(single); this.weather = weather; this.waxed = waxed;
            setTickRandomly(!waxed && weather != WeatherState.OXIDIZED);
        }
        @Override public WeatherState getWeatherState() { return weather; }
        @Override public boolean isWaxed() { return waxed; }
        @Override protected BlockStateContainer createBlockState() {
            return new ExtendedBlockState(this, new IProperty<?>[0], new IUnlistedProperty<?>[]{ConnectedTextureData.CONTEXT});
        }
        @Override public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
            return ((IExtendedBlockState) state).withProperty(ConnectedTextureData.CONTEXT, ConnectedTextureData.capture(world, pos, state));
        }
        @Override public void updateTick(World world, BlockPos pos, IBlockState state, Random random) { CopperWeathering.tryWeather(world, pos, state, random); }
    }
}
