package barry.skyblockqol.client.feature;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** A single boolean sub-setting shown when a Feature's row is expanded. */
public class FeatureSetting {

    private final String name;
    private final String description;
    private final BooleanSupplier getter;
    private final Consumer<Boolean> setter;

    public FeatureSetting(String name, String description, BooleanSupplier getter, Consumer<Boolean> setter) {
        this.name = name;
        this.description = description;
        this.getter = getter;
        this.setter = setter;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean isEnabled() { return getter.getAsBoolean(); }
    public void toggle() { setter.accept(!isEnabled()); }
}