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

    /** Chainable: attach a sub-setting shown when this feature's row is expanded. */
    public Feature withSetting(FeatureSetting setting) {
        settings.add(setting);
        return this;
    }

    public List<FeatureSetting> getSettings() { return settings; }

    public boolean matches(String query) {
        if (query == null || query.isBlank()) return true;
        String q = query.toLowerCase();
        return name.toLowerCase().contains(q) || description.toLowerCase().contains(q);
    }
}