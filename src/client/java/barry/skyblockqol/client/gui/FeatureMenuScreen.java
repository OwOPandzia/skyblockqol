package barry.skyblockqol.client.gui;

import barry.skyblockqol.client.feature.Feature;
import barry.skyblockqol.client.feature.FeatureRegistry;
import barry.skyblockqol.client.feature.FeatureSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.*;

public class FeatureMenuScreen extends Screen {

    private static final int COLUMN_WIDTH = 200;
    private static final int COLUMN_GAP = 24;
    private static final int HEADER_HEIGHT = 32;
    private static final int ROW_HEIGHT = 28;
    private static final int SUBROW_HEIGHT = 24;
    private static final int TOP_MARGIN = 40;
    private static final long HOVER_DELAY_MS = 1000;

    private static final int COLOR_HEADER_BG = 0xE6101010;
    private static final int COLOR_ROW_OFF = 0xE6141414;
    private static final int COLOR_ROW_ON = 0xE62E7BD6;
    private static final int COLOR_SUBROW_BG = 0xE61F1F1F;
    private static final int COLOR_ROW_HOVER = 0x33FFFFFF;
    private static final int COLOR_TEXT = 0xFFFFFFFF;

    // Persists across menu re-opens for the session, like most clickgui menus.
    private static final Set<Feature> EXPANDED = Collections.newSetFromMap(new IdentityHashMap<>());

    private EditBox searchBox;

    private int lastMouseX;
    private int lastMouseY;

    private final List<FeatureRow> featureRows = new ArrayList<>();
    private final List<SettingRow> settingRows = new ArrayList<>();
    private final List<GearRow> gearRows = new ArrayList<>();

    private Object hoverTarget;
    private long hoverStartMillis;
    private String hoverTitle;
    private String hoverDescription;
    private boolean hoverFoundThisFrame;

    public FeatureMenuScreen() {
        super(Component.literal("SkyblockQOL"));
    }

    @Override
    protected void init() {
        int boxWidth = 300;
        searchBox = new EditBox(this.font,
                this.width / 2 - boxWidth / 2,
                this.height - 40,
                boxWidth, 20,
                Component.literal("Search"));
        searchBox.setHint(Component.literal("Search here..."));
        this.addRenderableWidget(searchBox);
        this.setInitialFocus(searchBox);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
        featureRows.clear();
        settingRows.clear();
        gearRows.clear();
        hoverFoundThisFrame = false;

        String query = searchBox != null ? searchBox.getValue() : "";

        List<String> visibleCategories = new ArrayList<>();
        for (String category : FeatureRegistry.categoriesInOrder()) {
            if (!FeatureRegistry.search(category, query).isEmpty()) {
                visibleCategories.add(category);
            }
        }

        int totalWidth = visibleCategories.size() * COLUMN_WIDTH
                + Math.max(0, visibleCategories.size() - 1) * COLUMN_GAP;
        int startX = this.width / 2 - totalWidth / 2;

        int colIndex = 0;
        for (String category : visibleCategories) {
            int x = startX + colIndex * (COLUMN_WIDTH + COLUMN_GAP);
            drawColumn(graphics, category, FeatureRegistry.search(category, query), x, TOP_MARGIN, mouseX, mouseY);
            colIndex++;
        }

        if (visibleCategories.isEmpty()) {
            graphics.centeredText(this.font, "No features match your search.",
                    this.width / 2, TOP_MARGIN, 0xFFAA5555);
        }

        if (!hoverFoundThisFrame) {
            hoverTarget = null;
        }

        if (hoverTarget != null && System.currentTimeMillis() - hoverStartMillis >= HOVER_DELAY_MS) {
            drawTooltip(graphics, hoverTitle, hoverDescription, mouseX, mouseY);
        }
    }

    private void drawColumn(GuiGraphicsExtractor graphics, String category, List<Feature> features,
                            int x, int y, int mouseX, int mouseY) {

        // fill(x1, y1, x2, y2, color) - verify name if this doesn't compile.
        graphics.fill(x, y, x + COLUMN_WIDTH, y + HEADER_HEIGHT, COLOR_HEADER_BG);
        graphics.centeredText(this.font, category, x + COLUMN_WIDTH / 2, y + HEADER_HEIGHT / 2 - 4, COLOR_TEXT);

        int rowY = y + HEADER_HEIGHT;
        for (Feature feature : features) {
            boolean hovered = mouseX >= x && mouseX < x + COLUMN_WIDTH
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;

            int bg = feature.isEnabled() ? COLOR_ROW_ON : COLOR_ROW_OFF;
            graphics.fill(x, rowY, x + COLUMN_WIDTH, rowY + ROW_HEIGHT, bg);
            if (hovered) {
                graphics.fill(x, rowY, x + COLUMN_WIDTH, rowY + ROW_HEIGHT, COLOR_ROW_HOVER);
                registerHover(feature, feature.getName(), feature.getDescription());
            }

            boolean expanded = EXPANDED.contains(feature);
            String arrow = feature.getSettings().isEmpty() ? "" : (expanded ? "  \u25BE" : "  \u25B8");
            graphics.centeredText(this.font, feature.getName() + arrow,
                    x + COLUMN_WIDTH / 2, rowY + ROW_HEIGHT / 2 - 4, COLOR_TEXT);

            if (feature.hasConfigureAction()) {
                int gearSize = 16;
                int gearX = x + COLUMN_WIDTH - gearSize - 4;
                int gearY = rowY + (ROW_HEIGHT - gearSize) / 2;
                graphics.text(this.font, "\u2699", gearX, gearY, COLOR_TEXT);
                gearRows.add(new GearRow(feature, gearX, gearY, gearSize, gearSize));
            }

            featureRows.add(new FeatureRow(feature, x, rowY, COLUMN_WIDTH, ROW_HEIGHT));
            rowY += ROW_HEIGHT;

            if (expanded) {
                List<FeatureSetting> settings = feature.getSettings();
                if (settings.isEmpty()) {
                    graphics.fill(x, rowY, x + COLUMN_WIDTH, rowY + SUBROW_HEIGHT, COLOR_SUBROW_BG);
                    graphics.centeredText(this.font, "No additional settings",
                            x + COLUMN_WIDTH / 2, rowY + SUBROW_HEIGHT / 2 - 4, 0xFF888888);
                    rowY += SUBROW_HEIGHT;
                } else {
                    for (FeatureSetting setting : settings) {
                        boolean subHovered = mouseX >= x && mouseX < x + COLUMN_WIDTH
                                && mouseY >= rowY && mouseY < rowY + SUBROW_HEIGHT;

                        int subBg = setting.isEnabled() ? COLOR_ROW_ON : COLOR_SUBROW_BG;
                        graphics.fill(x, rowY, x + COLUMN_WIDTH, rowY + SUBROW_HEIGHT, subBg);
                        if (subHovered) {
                            graphics.fill(x, rowY, x + COLUMN_WIDTH, rowY + SUBROW_HEIGHT, COLOR_ROW_HOVER);
                            registerHover(setting, setting.getName(), setting.getDescription());
                        }

                        graphics.text(this.font, "  " + setting.getName(), x + 10, rowY + SUBROW_HEIGHT / 2 - 4, COLOR_TEXT);

                        settingRows.add(new SettingRow(setting, x, rowY, COLUMN_WIDTH, SUBROW_HEIGHT));
                        rowY += SUBROW_HEIGHT;
                    }
                }
            }
        }
    }

    private void registerHover(Object target, String title, String description) {
        hoverFoundThisFrame = true;
        if (hoverTarget != target) {
            hoverTarget = target;
            hoverStartMillis = System.currentTimeMillis();
        }
        hoverTitle = title;
        hoverDescription = description;
    }

    private void drawTooltip(GuiGraphicsExtractor graphics, String title, String description, int mouseX, int mouseY) {
        int maxTextWidth = 180;
        List<String> lines = wrapText(description, maxTextWidth);

        int lineHeight = this.font.lineHeight + 2; // verify field name if this doesn't compile
        int contentWidth = this.font.width(title);
        for (String line : lines) contentWidth = Math.max(contentWidth, this.font.width(line));
        int boxWidth = contentWidth + 12;
        int boxHeight = lineHeight + 4 + lines.size() * lineHeight + 6;

        int x = mouseX + 12;
        int y = mouseY + 12;
        if (x + boxWidth > this.width) x = this.width - boxWidth - 4;
        if (y + boxHeight > this.height) y = this.height - boxHeight - 4;

        graphics.fill(x, y, x + boxWidth, y + boxHeight, 0xF0000000);
        graphics.text(this.font, title, x + 6, y + 4, 0xFFFFFF55);

        int ty = y + 4 + lineHeight;
        for (String line : lines) {
            graphics.text(this.font, line, x + 6, ty, 0xFFCCCCCC);
            ty += lineHeight;
        }
    }

    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (this.font.width(candidate) > maxWidth && !current.isEmpty()) {
                lines.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(candidate);
            }
        }
        if (!current.isEmpty()) lines.add(current.toString());
        return lines;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {

        if (event.button() == 2) {
            for (SettingRow row : settingRows) {
                if (row.contains(lastMouseX, lastMouseY) && row.setting().hasMiddleClickAction()) {
                    row.setting().runMiddleClickAction();
                    return true;
                }
            }
            return super.mouseClicked(event, doubleClick);
        }

        if (event.button() == 1) {
            for (FeatureRow row : featureRows) {
                if (row.contains(lastMouseX, lastMouseY)) {
                    if (!EXPANDED.remove(row.feature())) {
                        EXPANDED.add(row.feature());
                    }
                    return true;
                }
            }
            return super.mouseClicked(event, doubleClick);
        }

        if (event.button() == 0) {
            for (SettingRow row : settingRows) {
                if (row.contains(lastMouseX, lastMouseY)) {
                    row.setting().toggle();
                    return true;
                }
            }
            for (FeatureRow row : featureRows) {
                if (row.contains(lastMouseX, lastMouseY)) {
                    row.feature().toggle();
                    return true;
                }
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record FeatureRow(Feature feature, int x, int y, int w, int h) {
        boolean contains(int mx, int my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private record SettingRow(FeatureSetting setting, int x, int y, int w, int h) {
        boolean contains(int mx, int my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private record GearRow(Feature feature, int x, int y, int w, int h) {
        boolean contains(int mx, int my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }
}