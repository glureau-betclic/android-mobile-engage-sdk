package com.emarsys.mobileengage.experimental;

import java.util.HashSet;
import java.util.Set;

public class MobileEngageExperimental {

    static Set<String> enabledFeatures = new HashSet<>();

    public static boolean isFeatureEnabled(FlipperFeature feature) {
        return enabledFeatures.contains(feature.getName());
    }

    public static void enableFeature(FlipperFeature feature) {
        enabledFeatures.add(feature.getName());
    }

    static void reset() {
        enabledFeatures.clear();
    }

}
