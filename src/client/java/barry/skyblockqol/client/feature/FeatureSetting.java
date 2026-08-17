package barry.skyblockqol.client.feature;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class FeatureSetting {

    private final String name;
    private final String description;
    private final BooleanSupplier getter;
    private final Consumer<Boolean> setter;
    private final Runnable middleClickAction; // nullable

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

    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean isEnabled() { return getter.getAsBoolean(); }
    public void toggle() { setter.accept(!isEnabled()); }

    public boolean hasMiddleClickAction() { return middleClickAction != null; }
    public void runMiddleClickAction() { if (middleClickAction != null) middleClickAction.run(); }
}