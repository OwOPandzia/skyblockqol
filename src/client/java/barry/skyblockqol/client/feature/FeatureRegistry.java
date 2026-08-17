package barry.skyblockqol.client.feature;

import java.util.Optional;
import java.util.*;
import java.util.stream.Collectors;

public class FeatureRegistry {

    private static final List<Feature> FEATURES = new ArrayList<>();
    private static final LinkedHashSet<String> CATEGORY_ORDER = new LinkedHashSet<>();

    private FeatureRegistry() {}

    public static void register(Feature feature) {
        FEATURES.add(feature);
        CATEGORY_ORDER.add(feature.getCategory());
    }

    public static List<Feature> getAll() {
        return Collections.unmodifiableList(FEATURES);
    }

    public static List<String> categoriesInOrder() {
        return List.copyOf(CATEGORY_ORDER);
    }

    /** Features in one category, filtered by search query (name/description). */
    public static List<Feature> search(String category, String query) {
        return FEATURES.stream()
                .filter(f -> f.getCategory().equals(category))
                .filter(f -> f.matches(query))
                .collect(Collectors.toList());
    }

    public static Optional<Feature> find(String category, String name) {
        return FEATURES.stream()
                .filter(f -> f.getCategory().equals(category) && f.getName().equals(name))
                .findFirst();
    }
}