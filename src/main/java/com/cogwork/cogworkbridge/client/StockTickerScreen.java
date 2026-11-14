package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.content.logistics.packager.StockTickerContainer;
import com.cogwork.cogworkbridge.content.logistics.packager.StockTickerOrderPacket;
import com.cogwork.cogworkbridge.content.logistics.packager.StockOrder;
import com.cogwork.cogworkbridge.network.ModNetwork;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import java.io.IOException;
import java.util.List;

/** The stock list and request workflow are added with the network sync. */
public final class StockTickerScreen extends GuiContainer {
    private static final ResourceLocation BACKGROUND = new ResourceLocation("create", "textures/gui/stock_keeper.png");
    private final StockTickerContainer ticker;
    private final int[] counts = new int[45];
    private GuiTextField address, amount, search;
    private LogisticsAddressSuggestions suggestions;
    private int selected = -1;
    private int firstRow;
    private int selectedCategory = -1, firstCategory;
    private boolean categoryMode;
    private java.util.List<ItemStack> stock = java.util.Collections.emptyList();
    private java.util.List<Integer> stockCounts = java.util.Collections.emptyList();
    private final java.util.List<ItemStack> pendingStock = new java.util.ArrayList<>();
    private final java.util.List<Integer> pendingCounts = new java.util.ArrayList<>();
    private boolean receivingStock;
    private final java.util.List<Integer> visible = new java.util.ArrayList<>();
    private final java.util.List<StockOrder.Line> order;
    private final java.util.List<StockOrder.Craft> crafts;
    public StockTickerScreen(StockTickerContainer container) {
        super(container); ticker = container; order = container.order(); crafts = container.crafts(); xSize = 256; ySize = 176;
    }
    @Override public void initGui() {
        super.initGui();
        address = new GuiTextField(0, fontRenderer, guiLeft + 26, guiTop + 139, 120, 12);
        address.setMaxStringLength(25);
        if (mc.world.getTileEntity(ticker.position()) instanceof com.cogwork.cogworkbridge.content.logistics.packager.StockTickerTileEntity tile) address.setText(tile.previousAddress());
        suggestions = new LogisticsAddressSuggestions(address);
        amount = new GuiTextField(1, fontRenderer, guiLeft + 151, guiTop + 139, 38, 12);
        amount.setMaxStringLength(4); amount.setText("1");
        search = new GuiTextField(2, fontRenderer, guiLeft + 38, guiTop + 20, 180, 12); search.setMaxStringLength(64);
        buttonList.clear(); buttonList.add(new CreateIconButton(0, guiLeft + 220, guiTop + 137, 0, 1));
        buttonList.add(new net.minecraft.client.gui.GuiButton(1, guiLeft + 193, guiTop + 137, 20, 18, "+"));
        buttonList.add(new net.minecraft.client.gui.GuiButton(2, guiLeft + 4, guiTop + 8, 20, 18, "×"));
        buttonList.add(new net.minecraft.client.gui.GuiButton(3, guiLeft + 4, guiTop + 32, 29, 18, ""));
        buttonList.add(new net.minecraft.client.gui.GuiButton(4, guiLeft + 4, guiTop + 54, 29, 18, "C"));
        buttonList.add(new net.minecraft.client.gui.GuiButton(5, guiLeft + 4, guiTop + 76, 29, 18, "+"));
        buttonList.add(new net.minecraft.client.gui.GuiButton(6, guiLeft + 4, guiTop + 98, 29, 18, "All"));
        Keyboard.enableRepeatEvents(true);
    }
    public void updateStock(int window, boolean first, boolean last, List<ItemStack> items, List<Integer> values) {
        if (ticker.windowId != window) return;
        if (first) { pendingStock.clear(); pendingCounts.clear(); receivingStock = true; }
        if (!receivingStock || items.size() != values.size()) return;
        pendingStock.addAll(items); pendingCounts.addAll(values);
        if (last) {
            stock = new java.util.ArrayList<>(pendingStock); stockCounts = new java.util.ArrayList<>(pendingCounts);
            receivingStock = false; refreshStock();
        }
    }
    private void refreshStock() {
        visible.clear(); String query = search == null ? "" : search.getText().trim().toLowerCase(java.util.Locale.ROOT);
        java.util.List<ItemStack> categories = categories();
        if (selectedCategory >= categories.size()) selectedCategory = -1;
        for (int i = 0; i < stock.size(); i++) {
            ItemStack item = stock.get(i);
            String name = item.getDisplayName().toLowerCase(java.util.Locale.ROOT);
            String registry = item.getItem().getRegistryName() == null ? "" : item.getItem().getRegistryName().toString();
            int category = categoryOf(item, categories);
            if (category >= 0 && mc.world.getTileEntity(ticker.position()) instanceof com.cogwork.cogworkbridge.content.logistics.packager.StockTickerTileEntity tile
                    && tile.categoryHidden(mc.player.getUniqueID(), category)) continue;
            if ((query.isEmpty() || name.contains(query) || registry.contains(query))
                    && (selectedCategory < 0 || category == selectedCategory)) visible.add(i);
        }
        if (selectedCategory < 0 && !categories.isEmpty()) visible.sort(java.util.Comparator.comparingInt(i -> {
            int category = categoryOf(stock.get(i), categories); return category < 0 ? categories.size() : category;
        }));
        firstRow = Math.max(0, Math.min(firstRow, Math.max(0, (visible.size() + 8) / 9 - 5)));
        for (int i = 0; i < 45; i++) {
            int visibleIndex = firstRow * 9 + i;
            int source = visibleIndex < visible.size() ? visible.get(visibleIndex) : -1;
            ticker.display().setStackInSlot(i, source >= 0 ? stock.get(source) : ItemStack.EMPTY);
            counts[i] = source >= 0 && source < stockCounts.size() ? stockCounts.get(source) : 0;
        }
    }
    private java.util.List<ItemStack> categories() {
        return mc.world.getTileEntity(ticker.position()) instanceof com.cogwork.cogworkbridge.content.logistics.packager.StockTickerTileEntity tile
                ? tile.categories() : java.util.Collections.emptyList();
    }
    private static int categoryOf(ItemStack item, java.util.List<ItemStack> categories) {
        for (int i = 0; i < categories.size(); i++)
            if (com.cogwork.cogworkbridge.content.fluids.ListFilterItem.matchesItem(categories.get(i), item)) return i;
        return -1;
    }
    @Override public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        for (var button : buttonList) if (button instanceof CreateIconButton icon) icon.release();
        super.onGuiClosed();
    }
    @Override protected void actionPerformed(net.minecraft.client.gui.GuiButton button) {
        if (button.id == 3) { ModNetwork.CHANNEL.sendToServer(new com.cogwork.cogworkbridge.content.logistics.packager.StockTickerSecurityPacket(ticker.windowId)); return; }
        if (button.id == 4) { categoryMode = !categoryMode; return; }
        if (button.id == 5) { ModNetwork.CHANNEL.sendToServer(new com.cogwork.cogworkbridge.content.logistics.packager.StockTickerCategoryPacket(ticker.windowId, -1)); categoryMode = true; return; }
        if (button.id == 6) { selectedCategory = -1; categoryMode = false; firstRow = 0; refreshStock(); return; }
        if (button.id == 2) { order.clear(); crafts.clear(); return; }
        if (button.id == 0 && !order.isEmpty()) {
            ModNetwork.CHANNEL.sendToServer(new StockTickerOrderPacket(ticker.windowId, order, address.getText(), crafts));
            order.clear(); crafts.clear(); return;
        }
        if ((button.id != 0 && button.id != 1) || selected < 0 || ticker.display().getStackInSlot(selected).isEmpty()) return;
        try {
            int requested = Math.max(1, Math.min(4096, Integer.parseInt(amount.getText())));
            ItemStack item = ticker.display().getStackInSlot(selected).copy(); item.setCount(1);
            if (button.id == 1) {
                order.add(new StockOrder.Line(item, requested));
                java.util.List<StockOrder.Line> normalized = StockOrder.normalize(order); order.clear(); order.addAll(normalized);
            } else ModNetwork.CHANNEL.sendToServer(new StockTickerOrderPacket(ticker.windowId, java.util.List.of(new StockOrder.Line(item, requested)), address.getText()));
        } catch (NumberFormatException ignored) { }
    }
    @Override protected void keyTyped(char typed, int key) throws IOException {
        if (suggestions.key(key)) return;
        if (search.textboxKeyTyped(typed, key)) { firstRow = 0; selected = -1; refreshStock(); return; }
        if (address.textboxKeyTyped(typed, key) || amount.textboxKeyTyped(typed, key)) return;
        super.keyTyped(typed, key);
    }
    @Override protected void mouseClicked(int x, int y, int button) throws IOException {
        if (suggestions.click(x, y, button)) return;
        if (categoryMode && x >= guiLeft + 37 && x < guiLeft + 217 && y >= guiTop + 36 && y < guiTop + 136) {
            int index = firstCategory + (y - guiTop - 36) / 20;
            if (index < categories().size()) {
                if (button == 1) ModNetwork.CHANNEL.sendToServer(new com.cogwork.cogworkbridge.content.logistics.packager.StockTickerCategoryPacket(ticker.windowId, index));
                else if (button == 0 && isShiftKeyDown()) ModNetwork.CHANNEL.sendToServer(new com.cogwork.cogworkbridge.content.logistics.packager.StockTickerCategoryPacket(ticker.windowId, -2 - index));
                else if (button == 0) { selectedCategory = index; categoryMode = false; firstRow = 0; refreshStock(); }
            }
            return;
        }
        address.mouseClicked(x, y, button); amount.mouseClicked(x, y, button); search.mouseClicked(x, y, button);
        if (button == 0) {
            int column = (x - (guiLeft + 37)) / 20, row = (y - (guiTop + 36)) / 20;
            if (x >= guiLeft + 37 && y >= guiTop + 36 && column >= 0 && column < 9 && row >= 0 && row < 5) selected = row * 9 + column;
        }
        super.mouseClicked(x, y, button);
    }
    @Override public void drawScreen(int x, int y, float partial) {
        if (mc.world.getTileEntity(ticker.position()) instanceof com.cogwork.cogworkbridge.content.logistics.packager.StockTickerTileEntity tile)
            buttonList.get(3).displayString = I18n.format(tile.isLocked() ? "cogworkbridge.stock_ticker.unlock" : "cogworkbridge.stock_ticker.lock");
        super.drawScreen(x, y, partial);
        if (categoryMode) {
            drawRect(guiLeft + 36, guiTop + 35, guiLeft + 218, guiTop + 136, 0xff62584a);
            java.util.List<ItemStack> filters = categories();
            for (int row = 0; row < 5 && row + firstCategory < filters.size(); row++) {
                ItemStack filter = filters.get(row + firstCategory); int lineY = guiTop + 36 + row * 20;
                itemRender.renderItemAndEffectIntoGUI(filter, guiLeft + 39, lineY);
                boolean hidden = mc.world.getTileEntity(ticker.position()) instanceof com.cogwork.cogworkbridge.content.logistics.packager.StockTickerTileEntity tile
                        && tile.categoryHidden(mc.player.getUniqueID(), row + firstCategory);
                fontRenderer.drawString(fontRenderer.trimStringToWidth(filter.getDisplayName(), 150), guiLeft + 58, lineY + 4, hidden ? 0x888888 : 0xffffff);
            }
        }
        suggestions.draw();
    }
    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = org.lwjgl.input.Mouse.getEventDWheel();
        if (wheel == 0) return;
        int x = org.lwjgl.input.Mouse.getEventX() * width / mc.displayWidth;
        int y = height - org.lwjgl.input.Mouse.getEventY() * height / mc.displayHeight - 1;
        if (x < guiLeft + 37 || x >= guiLeft + 217 || y < guiTop + 36 || y >= guiTop + 136) return;
        if (categoryMode) { firstCategory = Math.max(0, Math.min(Math.max(0, categories().size() - 5), firstCategory + (wheel < 0 ? 1 : -1))); return; }
        firstRow += wheel < 0 ? 1 : -1; selected = -1; refreshStock();
    }
    @Override protected void drawGuiContainerBackgroundLayer(float partial, int mouseX, int mouseY) {
        GlStateManager.color(1, 1, 1, 1); mc.getTextureManager().bindTexture(BACKGROUND);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, 256, 36);
        for (int y = 36; y < 136; y += 20) drawTexturedModalRect(guiLeft, guiTop + y, 0, 48, 256, 20);
        drawTexturedModalRect(guiLeft, guiTop + 136, 0, 80, 256, 40);
        address.drawTextBox(); amount.drawTextBox(); search.drawTextBox();
    }
    @Override protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        String title = I18n.format("tile.cogworkbridge.stock_ticker.name");
        fontRenderer.drawString(title, (256 - fontRenderer.getStringWidth(title)) / 2, 8, 0x592424);
        if (!order.isEmpty()) fontRenderer.drawString("+" + order.size(), 218, 10, 0x592424);
        for (int i = 0; i < 45; i++) {
            ItemStack stack = ticker.display().getStackInSlot(i); if (stack.isEmpty()) continue;
            int x = 37 + (i % 9) * 20, y = 36 + (i / 9) * 20;
            if (i == selected) drawRect(x - 2, y - 2, x + 18, y + 18, 0x88ffffff);
            itemRender.renderItemAndEffectIntoGUI(stack, x, y);
            itemRender.renderItemOverlayIntoGUI(fontRenderer, stack, x, y, counts[i] > 999 ? "999+" : Integer.toString(counts[i]));
        }
    }
}
