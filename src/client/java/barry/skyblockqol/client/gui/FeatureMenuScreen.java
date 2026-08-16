package barry.skyblockqol.client.gui;

import barry.skyblockqol.client.feature.Feature;
import barry.skyblockqol.client.feature.FeatureRegistry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class FeatureMenuScreen extends Screen {

    private static final int LIST_TOP = 46;
    private static final int ROW_HEIGHT = 34;
    private static final int ROW_WIDTH = 220;

    private EditBox searchBox;
    private final List<AbstractWidget> rowWidgets = new ArrayList<>();
    private List<Feature> currentMatches = List.of();

    public FeatureMenuScreen() {
        super(Component.literal("SkyblockQOL"));
    }

    @Override
    protected void init() {
        searchBox = new EditBox(this.font, this.width / 2 - ROW_WIDTH / 2, 20, ROW_WIDTH, 20,
                Component.literal("Search"));
        searchBox.setHint(Component.literal("Search features..."));
        searchBox.setResponder(this::rebuildRows);
        this.addRenderableWidget(searchBox);

        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
                .bounds(this.width / 2 - 50, this.height - 26, 100, 20)
                .build());

        rebuildRows(searchBox.getValue());
        this.setInitialFocus(searchBox);
    }

    private void rebuildRows(String query) {
        rowWidgets.forEach(this::removeWidget);
        rowWidgets.clear();

        currentMatches = FeatureRegistry.search(query);

        int y = LIST_TOP;
        for (Feature feature : currentMatches) {
            Button toggle = Button.builder(toggleLabel(feature), b -> {
                        feature.toggle();
                        b.setMessage(toggleLabel(feature));
                    })
                    .bounds(this.width / 2 - ROW_WIDTH / 2, y, ROW_WIDTH, 20)
                    .build();

            this.addRenderableWidget(toggle);
            rowWidgets.add(toggle);
            y += ROW_HEIGHT;
        }
    }

    private Component toggleLabel(Feature feature) {
        return Component.literal(feature.getName() + ": " + (feature.isEnabled() ? "ON" : "OFF"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        graphics.centeredText(this.font, this.title, this.width / 2, 6, 0xFFFFFF); // verify name

        int y = LIST_TOP;
        for (Feature feature : currentMatches) {
            graphics.centeredText(this.font, feature.getDescription(), this.width / 2, y + 22, 0x999999);
            y += ROW_HEIGHT;
        }

        if (currentMatches.isEmpty()) {
            graphics.centeredText(this.font, "No features match your search.",
                    this.width / 2, LIST_TOP + 6, 0xAA5555);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}