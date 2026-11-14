package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.content.equipment.clipboard.ClipboardData;
import com.cogwork.cogworkbridge.content.equipment.clipboard.ClipboardTileEntity;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import org.lwjgl.input.Keyboard;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Address entries from carried and nearby clipboards. */
final class LogisticsAddressSuggestions {
    private final GuiTextField field;
    private final List<String> suggestions = new ArrayList<>();
    private int selected;
    LogisticsAddressSuggestions(GuiTextField field) { this.field = field; }

    private void refresh() {
        suggestions.clear();
        Minecraft mc = Minecraft.getMinecraft();
        if (!field.isFocused() || mc.player == null || mc.world == null) return;
        Set<String> values = new LinkedHashSet<>();
        for (int i = 0; i < mc.player.inventory.getSizeInventory(); i++) {
            ItemStack stack = mc.player.inventory.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() == Item.getItemFromBlock(AllBlocks.CLIPBOARD))
                collect(values, ClipboardData.fromItem(stack));
        }
        for (TileEntity tile : mc.world.loadedTileEntityList)
            if (tile instanceof ClipboardTileEntity clipboard && mc.player.getDistanceSq(tile.getPos()) <= 1024)
                collect(values, clipboard.content());
        String query = field.getText().trim().toLowerCase(Locale.ROOT);
        for (String value : values)
            if (!value.equals(field.getText()) && value.toLowerCase(Locale.ROOT).contains(query)) suggestions.add(value);
        selected = Math.max(0, Math.min(selected, Math.max(0, suggestions.size() - 1)));
    }
    private static void collect(Set<String> values, NBTTagCompound data) {
        for (int page = 0; page < ClipboardData.pageCount(data); page++) {
            String[] lines = ClipboardData.lines(data, page); int checked = ClipboardData.checked(data, page);
            for (int row = 0; row < lines.length; row++) {
                String line = lines[row];
                if ((checked & 1 << row) != 0 || !line.startsWith("#")) continue;
                String value = line.substring(1).trim();
                if (!value.isEmpty() && value.length() <= 25) values.add(value);
            }
        }
    }
    boolean key(int key) {
        if (!field.isFocused()) return false;
        refresh();
        if (suggestions.isEmpty()) return false;
        if (key == Keyboard.KEY_UP || key == Keyboard.KEY_DOWN) {
            selected = Math.floorMod(selected + (key == Keyboard.KEY_DOWN ? 1 : -1), suggestions.size());
            return true;
        }
        if (key == Keyboard.KEY_TAB || key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
            field.setText(suggestions.get(selected)); suggestions.clear(); return true;
        }
        return false;
    }
    boolean click(int x, int y, int button) {
        if (!field.isFocused() || button != 0) return false;
        refresh();
        int row = (y - field.y - field.height) / 11;
        if (x < field.x || x >= field.x + field.width || y < field.y + field.height || row < 0 || row >= Math.min(5, suggestions.size())) return false;
        field.setText(suggestions.get(row)); suggestions.clear(); return true;
    }
    void draw() {
        refresh();
        Minecraft mc = Minecraft.getMinecraft();
        for (int i = 0; i < Math.min(5, suggestions.size()); i++) {
            int y = field.y + field.height + i * 11;
            Gui.drawRect(field.x, y, field.x + field.width, y + 11, i == selected ? 0xe0707080 : 0xe0202020);
            mc.fontRenderer.drawString(mc.fontRenderer.trimStringToWidth(suggestions.get(i), field.width - 4), field.x + 2, y + 2, 0xffffff);
        }
    }
}
