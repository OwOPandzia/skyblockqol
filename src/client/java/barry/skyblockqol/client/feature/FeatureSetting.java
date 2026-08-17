package barry.skyblockqol.client.feature;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class FeatureSetting {

    private final String name;
    private final String description;
    private final BooleanSupplier getter;
    private final Consumer<Boolean> setter;
    private final Runnable middleClickAction;
    private String configKey;

    public FeatureSetting(String name, String description, BooleanSupplier getter, Consumer<Boolean> setter) {
        this(name, description, getter, setter, null);
    }

    public FeatureSetting(String name, String description, BooleanSupplier getter, Consumer<Boolean> setter,
                          Runnable middleClickAction) {
        this.name = name;
        this.description = description;
        this.getter = getter;
        this.setter = setter;
        this.middleClickAction = middleClickAction;
    }

    /** Called by Feature#withSetting once the parent's config key is known. */
    void bindConfigKey(String parentKey) {
        this.configKey = parentKey + "::" + name;
        if (FeatureConfig.has(configKey)) {
            setter.accept(FeatureConfig.get(configKey, getter.getAsBoolean()));
        }
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean isEnabled() { return getter.getAsBoolean(); }

    public void toggle() {
        boolean newValue = !isEnabled();
        setter.accept(newValue);
        if (configKey != null) FeatureConfig.set(configKey, newValue);
    }

    public boolean hasMiddleClickAction() { return middleClickAction != null; }
    public void runMiddleClickAction() { if (middleClickAction != null) middleClickAction.run(); }
}