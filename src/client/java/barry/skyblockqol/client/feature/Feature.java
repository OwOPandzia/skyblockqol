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
    private Runnable onConfigure;
    private final String configKey;

    public Feature(String category, String name, String description, BooleanSupplier getter, Consumer<Boolean> setter) {
        this.category = category;
        this.name = name;
        this.description = description;
        this.getter = getter;
        this.setter = setter;
        this.configKey = category + "::" + name;

        if (FeatureConfig.has(configKey)) {
            setter.accept(FeatureConfig.get(configKey, getter.getAsBoolean()));
        }
    }

    public String getCategory() { return category; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean isEnabled() { return getter.getAsBoolean(); }

    public void setEnabled(boolean enabled) {
        setter.accept(enabled);
        FeatureConfig.set(configKey, enabled);
    }

    public void toggle() { setEnabled(!isEnabled()); }

    public Feature withSetting(FeatureSetting setting) {
        setting.bindConfigKey(configKey);
        settings.add(setting);
        return this;
    }

    public List<FeatureSetting> getSettings() { return settings; }

    public void setOnConfigure(Runnable onConfigure) { this.onConfigure = onConfigure; }
    public Runnable getOnConfigure() { return onConfigure; }
    public boolean hasConfigureAction() { return onConfigure != null; }

    public boolean matches(String query) {
        if (query == null || query.isBlank()) return true;
        String q = query.toLowerCase();
        return name.toLowerCase().contains(q) || description.toLowerCase().contains(q);
    }
}