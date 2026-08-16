package barry.skyblockqol.client.feature;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Lightweight descriptor exposing a feature's name, description and on/off
 * state to the menu UI, without requiring the underlying feature class to
 * give up its static fields/methods.
 */
public class Feature {

    private final String name;
    private final String description;
    private final BooleanSupplier getter;
    private final Consumer<Boolean> setter;

    public Feature(String name, String description, BooleanSupplier getter, Consumer<Boolean> setter) {
        this.name = name;
        this.description = description;
        this.getter = getter;
        this.setter = setter;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean isEnabled() { return getter.getAsBoolean(); }
    public void setEnabled(boolean enabled) { setter.accept(enabled); }
    public void toggle() { setEnabled(!isEnabled()); }

    /** Case-insensitive match against name AND description, used by the search box. */
    public boolean matches(String query) {
        if (query == null || query.isBlank()) return true;
        String q = query.toLowerCase();
        return name.toLowerCase().contains(q) || description.toLowerCase().contains(q);
    }
}