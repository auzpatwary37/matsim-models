package com.citymodeler.matsim.models.gtfs;

import java.nio.file.Path;
import java.util.List;

/**
 * Configuration for GTFS import.
 */
public record GtfsImportConfig(
        List<FeedSource> feeds,
        ServiceDateSelection serviceDateSelection,
        boolean assumeAlwaysActive,
        String explicitDate) {

    public record FeedSource(Path path, String explicitFeedId) {
        public String effectiveFeedId() {
            return explicitFeedId != null ? explicitFeedId : GtfsFeedIdCodec.derive(path);
        }
    }

    public enum ServiceDateSelection {
        EXPLICIT,
        DAY_WITH_MOST_TRIPS,
        DAY_WITH_MOST_SERVICES,
        ALL
    }

    public GtfsImportConfig(List<FeedSource> feeds, ServiceDateSelection serviceDateSelection,
                             boolean assumeAlwaysActive) {
        this(feeds, serviceDateSelection, assumeAlwaysActive, null);
    }

    public static GtfsImportConfig forFolder(Path folder, ServiceDateSelection selection) {
        return new GtfsImportConfig(
                List.of(new FeedSource(folder, null)),
                selection, false, null);
    }
}
