package barry.skyblockqol.client.feature;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class FeatureRegistry {

    private static final List<Feature> FEATURES = new ArrayList<>();

    private FeatureRegistry() {}

    public static void register(Feature feature) {
        FEATURES.add(feature);
    }

    public static List<Feature> getAll() {
        return Collections.unmodifiableList(FEATURES);
    }

    public static List<Feature> search(String query) {
        if (query == null || query.isBlank()) return getAll();
        return FEATURES.stream()
                .filter(f -> f.matches(query))
                .collect(Collectors.toList());
    }
}