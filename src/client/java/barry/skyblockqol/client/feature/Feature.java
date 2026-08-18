package barry.skyblockqol.client.feature;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class Feature {

    private final String category;
    private final String name;
    private final String description;
    private final BooleanSupplier getter;
    private final Consumer<Boolean> setter;
    private final List<FeatureSetting> settings = new ArrayList<>();
    private final List<KeybindSetting> keybindSettings = new ArrayList<>();
    private final List<RangeSetting> rangeSettings = new ArrayList<>();
    private Runnable configureAction; // nullable - runs when the gear icon is clicked

    public Feature(String category, String name, String description, BooleanSupplier getter, Consumer<Boolean> setter) {
        this.category = category;
        this.name = name;
        this.description = description;
        this.getter = getter;
        this.setter = setter;
    }

    public String getCategory() { return category; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean isEnabled() { return getter.getAsBoolean(); }
    public void setEnabled(boolean enabled) { setter.accept(enabled); }
    public void toggle() { setEnabled(!isEnabled()); }

    public Feature withSetting(FeatureSetting setting) {
        settings.add(setting);
        return this;
    }

    public Feature withKeybindSetting(KeybindSetting setting) {
        keybindSettings.add(setting);
        return this;
    }

    public Feature withRangeSetting(RangeSetting setting) {
        rangeSettings.add(setting);
        return this;
    }

    /** Chainable: attach an action for the gear icon (e.g. open a config screen). */
    public Feature withConfigureAction(Runnable configureAction) {
        this.configureAction = configureAction;
        return this;
    }

    public boolean hasConfigureAction() { return configureAction != null; }
    public void runConfigureAction() { if (configureAction != null) configureAction.run(); }

    public List<FeatureSetting> getSettings() { return settings; }
    public List<KeybindSetting> getKeybindSettings() { return keybindSettings; }
    public List<RangeSetting> getRangeSettings() { return rangeSettings; }

    public boolean hasExpandableContent() {
        return !settings.isEmpty() || !keybindSettings.isEmpty() || !rangeSettings.isEmpty();
    }

    public boolean matches(String query) {
        if (query == null || query.isBlank()) return true;
        String q = query.toLowerCase();
        return name.toLowerCase().contains(q) || description.toLowerCase().contains(q);
    }
}