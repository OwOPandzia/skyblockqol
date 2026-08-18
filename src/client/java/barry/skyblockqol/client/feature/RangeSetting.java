package barry.skyblockqol.client.feature;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/**
 * A settings-row for a bounded numeric value, edited inline in the feature
 * menu itself - no separate screen. FeatureMenuScreen renders this as its
 * own row type: middle-clicking it starts "editing" with a live text
 * buffer, Enter confirms and clamps the value, Escape cancels, and clicking
 * anywhere else while editing also confirms.
 */
public class RangeSetting {

    private final String name;
    private final String description;
    private final double min;
    private final double max;
    private final DoubleSupplier getter;
    private final DoubleConsumer setter;

    public RangeSetting(String name, String description, double min, double max,
                        DoubleSupplier getter, DoubleConsumer setter) {
        this.name = name;
        this.description = description;
        this.min = min;
        this.max = max;
        this.getter = getter;
        this.setter = setter;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public double getValue() { return getter.getAsDouble(); }

    public void setValue(double value) {
        setter.accept(Math.max(min, Math.min(max, value)));
    }
}