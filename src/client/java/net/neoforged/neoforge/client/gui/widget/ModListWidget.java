/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.client.gui.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.VersionChecker;
import net.neoforged.fml.i18n.MavenVersionTranslator;
import net.neoforged.neoforge.client.gui.ModListScreen;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforgespi.language.IModInfo;

public class ModListWidget extends ObjectSelectionList<ModListWidget.ModEntry> {
    /** How far a jarred row, or the text next to a fold arrow, is pushed in from the content edge. */
    private static final int BUNDLE_INDENT = 10;

    private static String stripControlCodes(String value) {
        return net.minecraft.util.StringUtil.stripColor(value);
    }

    private static final Identifier VERSION_CHECK_ICONS = Identifier.fromNamespaceAndPath(NeoForgeMod.MOD_ID, "textures/gui/version_check_icons.png");
    private final int listWidth;

    private ModListScreen parent;

    public ModListWidget(ModListScreen parent, int listWidth, int top, int bottom) {
        super(parent.getMinecraftInstance(), listWidth, bottom - top, top, parent.getFontRenderer().lineHeight * 2 + 8);
        this.parent = parent;
        this.listWidth = listWidth;
        //this.setRenderBackground(false); // TODO: Porting 1.20.5 still needed?
    }

    @Override
    protected int scrollBarX() {
        return this.listWidth;
    }

    @Override
    public int getRowWidth() {
        return this.listWidth;
    }

    public void refreshList() {
        this.clearEntries();
        parent.buildModList(this::addEntry, mod -> new ModEntry(mod, this.parent));
    }

    public class ModEntry extends ObjectSelectionList.Entry<ModEntry> {
        private final ModListScreen.ModRow row;
        private final ModListScreen parent;

        ModEntry(ModListScreen.ModRow row, ModListScreen parent) {
            this.row = row;
            this.parent = parent;
        }

        @Override
        public Component getNarration() {
            return Component.translatable("narrator.select", row.displayName());
        }

        @Override
        public void extractContent(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            Component name = Component.literal(stripControlCodes(row.displayName()));
            Component version = Component.literal(stripControlCodes(row.version()));
            int left = getContentX();
            int top = getContentY();
            Font font = this.parent.getFontRenderer();
            // NeoFobric: a mod comes indented under the mod whose jar carries it (26.2 does the same), and a
            // mod that carries others gets the vanilla locator-bar arrow on its right edge as the fold
            // control.
            if (this.parent.isRowNested(row)) {
                left += BUNDLE_INDENT;
            }
            guiGraphics.text(font, Language.getInstance().getVisualOrder(FormattedText.composite(font.substrByWidth(name, listWidth))), left + 3, top + 2, 0xFFFFFFFF, false);
            var versionLine = Language.getInstance().getVisualOrder(FormattedText.composite(font.substrByWidth(version, listWidth)));
            guiGraphics.text(font, versionLine, left + 3, top + 2 + font.lineHeight, 0xFFCCCCCC, false);
            // Which loader a row's mod came from. Fabric mods are contributed by the bridge and would
            // otherwise look exactly like FML's own.
            drawLoaderChip(guiGraphics, font, left + 3 + font.width(versionLine) + 5, top + 2 + font.lineHeight, row.fabric());
            if (!row.fabric()) {
                VersionChecker.CheckResult vercheck = VersionChecker.getResult(row.container().getModInfo());
                if (vercheck.status().shouldDraw()) {
                    //TODO: Consider adding more icons for visualization
                    int entryHeight = getContentHeight();
                    guiGraphics.blit(RenderPipelines.GUI_TEXTURED, VERSION_CHECK_ICONS, getX() + width - 12, top + entryHeight / 4, vercheck.status().getSheetOffset() * 8, (vercheck.status().isAnimated() && ((System.currentTimeMillis() / 800 & 1)) == 1) ? 8 : 0, 8, 8, 64, 16);
                }
            }
            if (this.parent.hasBundledChildren(row.id())) {
                // Same vanilla HUD sprites 26.2's mod list uses for the fold control. blitSprite, not blit:
                // these are atlas sprites, and sampling the atlas at (0, 0) paints the missing-texture
                // checkerboard instead of the arrow. Unlike 26.2 the arrow is always drawn, not only while
                // hovered: a player should see which rows carry bundled mods without sweeping the mouse.
                var icon = this.parent.isBundleOpen(row.id()) ? COLLAPSE_ICON : EXPAND_ICON;
                guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, icon, expandIconX(), expandIconY(), EXPAND_ICON_WIDTH, EXPAND_ICON_HEIGHT);
            }
        }

        private static final Identifier EXPAND_ICON = Identifier.withDefaultNamespace("hud/locator_bar_arrow_down");
        private static final Identifier COLLAPSE_ICON = Identifier.withDefaultNamespace("hud/locator_bar_arrow_up");
        private static final int EXPAND_ICON_WIDTH = 7;
        private static final int EXPAND_ICON_HEIGHT = 10;

        /** {@return the x of the fold arrow, or -1 when this row carries nothing} */
        private int expandIconX() {
            return this.parent.hasBundledChildren(row.id()) ? getContentRight() - EXPAND_ICON_WIDTH - 3 : -1;
        }

        private int expandIconY() {
            return getContentY() + (getContentHeight() - EXPAND_ICON_HEIGHT) / 2;
        }

        /** A dark chip with a 1px brand-coloured border and brand-coloured text, drawn from primitives. */
        private void drawLoaderChip(GuiGraphicsExtractor graphics, Font font, int x, int y, boolean fabric) {
            String label = fabric ? "Fabric" : "NeoForge";
            int color = fabric ? 0xFFDBC1A4 : 0xFFFFA033;
            int width = font.width(label);
            int left = x - 2;
            int top = y - 1;
            int right = x + width + 2;
            int bottom = y + font.lineHeight;
            if (right > getContentRight()) {
                return; // Not enough room next to the version; the chip is decoration, not data.
            }
            graphics.fill(left, top, right, bottom, 0xC0181818);
            graphics.fill(left, top, right, top + 1, color);
            graphics.fill(left, bottom - 1, right, bottom, color);
            graphics.fill(left, top, left + 1, bottom, color);
            graphics.fill(right - 1, top, right, bottom, color);
            graphics.text(font, label, x, y, color);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            // NeoFobric: the fold arrow is its own hit target - folding must not also select the row.
            int iconX = expandIconX();
            if (iconX >= 0
                    && event.x() >= iconX - 2 && event.x() < iconX + EXPAND_ICON_WIDTH + 2
                    && event.y() >= expandIconY() - 1 && event.y() < expandIconY() + EXPAND_ICON_HEIGHT + 1) {
                this.parent.toggleBundle(row.id());
                return true;
            }
            // clicking on a selected item a second time unselects it
            parent.setSelected(isFocused() ? null : this);
            ModListWidget.this.setSelected(isFocused() ? null : this);
            return false;
        }

        @Override
        public void setFocused(boolean focused) {
            // ignore focus loss so the item stays selected when tabbing to the config button
            if (focused) {
                parent.setSelected(this);
                ModListWidget.this.setSelected(this);
            }
        }

        @Override
        public boolean isFocused() {
            return ModListWidget.this.getSelected() == this;
        }

        public IModInfo getInfo() {
            return row.container().getModInfo();
        }

        public ModContainer getContainer() {
            return row.container();
        }

        public ModListScreen.ModRow getRow() {
            return row;
        }

        public String getId() {
            return row.id();
        }
    }
}
