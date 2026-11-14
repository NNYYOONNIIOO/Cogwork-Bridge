package com.cogwork.cogworkbridge.content.trains.track;

import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.registry.AllItems;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraft.util.ResourceLocation;
import java.util.*;

/** The track's upstream deploy, deploy, press assembly line. */
public final class TrackAssemblySequence {
    private static final String STEP="TrackAssemblyStep";
    private TrackAssemblySequence(){}
    public static List<ItemStack> sleepers(){
        List<ItemStack> result=new ArrayList<>();
        result.add(new ItemStack(Blocks.STONE_SLAB,1,0));
        for(String name:new String[]{"farmers_future_delight:smooth_stone_slab","farmers_future_delight:andesite_slab"}) {
            Item item=ForgeRegistries.ITEMS.getValue(new ResourceLocation(name));
            if(item!=null && item!=Items.AIR)result.add(new ItemStack(item));
        }
        for(ItemStack item:OreDictionary.getOres("slabAndesite")) if(!item.isEmpty())result.add(item.copy());
        return result;
    }
    public static boolean isSleeper(ItemStack stack){
        if(stack.isEmpty())return false;
        for(ItemStack sleeper:sleepers())if(OreDictionary.itemMatches(sleeper,stack,false))return true;
        return false;
    }
    public static boolean isNugget(ItemStack held){
        if(held.isEmpty())return false;
        if(held.getItem()==Items.IRON_NUGGET || held.getItem()==AllItems.ZINC_NUGGET)return true;
        for(int id:OreDictionary.getOreIDs(held)) {
            String ore=OreDictionary.getOreName(id);
            if("nuggetIron".equals(ore)||"nuggetZinc".equals(ore))return true;
        }
        return false;
    }
    public static boolean isDeployStep(ItemStack input,ItemStack held){
        return isNugget(held) && (isSleeper(input) || input.getItem()==AllItems.INCOMPLETE_TRACK && stage(input)==1);
    }
    public static ItemStack deploy(ItemStack input){
        ItemStack result=new ItemStack(AllItems.INCOMPLETE_TRACK);
        NBTTagCompound tag=new NBTTagCompound();tag.setInteger(STEP,isSleeper(input)?1:2);result.setTagCompound(tag);
        return result;
    }
    public static ItemStack press(ItemStack input){
        return input.getItem()==AllItems.INCOMPLETE_TRACK && stage(input)==2
                ?new ItemStack(AllBlocks.TRACK):ItemStack.EMPTY;
    }
    public static int stage(ItemStack input){
        return input.hasTagCompound()?Math.max(0,Math.min(2,input.getTagCompound().getInteger(STEP))):0;
    }
    public static List<ItemStack> nuggets(){
        List<ItemStack> result=new ArrayList<>();result.add(new ItemStack(Items.IRON_NUGGET));result.add(new ItemStack(AllItems.ZINC_NUGGET));
        for(String ore:new String[]{"nuggetIron","nuggetZinc"})
            for(ItemStack s:OreDictionary.getOres(ore))if(!s.isEmpty())result.add(s.copy());
        return result;
    }
}
