package com.citymodeler.matsim.models.bundle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.citymodeler.matsim.models.facilities.ActivityFacilities;
import com.citymodeler.matsim.models.facilities.osm.OsmFacilityConfig;
import com.citymodeler.matsim.models.facilities.osm.OsmFacilityConverter;
import com.citymodeler.matsim.models.facilities.osm.OsmFacilityParser;
import com.citymodeler.matsim.models.gtfs.GtfsFeedSet;
import com.citymodeler.matsim.models.gtfs.GtfsImportConfig;
import com.citymodeler.matsim.models.gtfs.GtfsImporter;
import com.citymodeler.matsim.models.gtfs.GtfsTransitBuildResult;
import com.citymodeler.matsim.models.gtfs.GtfsTransitScheduleBuilder;
import com.citymodeler.matsim.models.io.FacilitiesXmlWriter;
import com.citymodeler.matsim.models.io.NetworkXmlWriter;
import com.citymodeler.matsim.models.io.TransitScheduleXmlWriter;
import com.citymodeler.matsim.models.io.VehiclesXmlWriter;
import com.citymodeler.matsim.models.mapping.TransitMappingConfig;
import com.citymodeler.matsim.models.mapping.TransitMappingResult;
import com.citymodeler.matsim.models.mapping.TransitNetworkMapper;
import com.citymodeler.matsim.models.mapping.TransitScheduleClipper;
import com.citymodeler.matsim.models.network.index.LinkSpatialIndex;
import com.citymodeler.matsim.models.osm.OsmImportConfig;
import com.citymodeler.matsim.models.osm.OsmNetworkImporter;
import com.citymodeler.matsim.models.osm.network.OsmMatsimNetworkBuilder;
import com.citymodeler.matsim.models.osm.network.OsmNetworkBuildConfig;
import com.citymodeler.matsim.models.osm.network.OsmNetworkBuildResult;
import com.citymodeler.matsim.models.osm.network.OsmSignalAwareSimplifier;
import com.citymodeler.matsim.models.osm.network.OsmSimplifiedNetwork;
import com.citymodeler.matsim.models.osm.network.OsmSimplifyOptions;
import com.citymodeler.matsim.models.transit.TransitSchedule;
import com.citymodeler.matsim.models.vehicles.Vehicle;
import com.citymodeler.matsim.models.vehicles.VehicleDefinitions;
import com.citymodeler.matsim.models.vehicles.VehicleType;

/**
 * End-to-end bundle production, mirroring the three-stage OSM+GTFS pipeline:
 *
 * <ol>
 *   <li><b>Base network</b> — OSM → cleaned, signal-ready network.</li>
 *   <li><b>Unmapped transit</b> — GTFS → {@code TransitSchedule} + {@code VehicleDefinitions}
 *       (one vehicle type per mode, one vehicle per departure).</li>
 *   <li><b>Mapping</b> — the unmapped schedule is mapped onto the base network, producing a mapped
 *       schedule and a mapped network (base network plus PT routing / artificial links).</li>
 * </ol>
 *
 * <p>All three stages are emitted so the base and mapped artifacts can be compared like-for-like
 * with an external converter. Outputs (fixed file names in the target directory):
 * <ul>
 *   <li>{@code network.xml} — stage 1 base network</li>
 *   <li>{@code facilities.xml} — OSM activity facilities</li>
 *   <li>{@code transitSchedule.xml} — stage 2 unmapped schedule (only with GTFS)</li>
 *   <li>{@code vehicleDefinitions.xml} — stage 2 vehicles (only with GTFS)</li>
 *   <li>{@code network-mapped.xml} — stage 3 mapped network (only with GTFS)</li>
 *   <li>{@code transitSchedule-mapped.xml} — stage 3 mapped schedule (only with GTFS)</li>
 * </ul>
 *
 * <p>This class only orchestrates this project's own components and writers; it contains no
 * third-party transit/network conversion logic. Because mapping mutates its inputs in place, the
 * stage-1/2 artifacts are written to disk <em>before</em> mapping runs.
 */
public final class OsmGtfsBundleRunner {

    public static final String NETWORK_FILE = "network.xml";
    public static final String FACILITIES_FILE = "facilities.xml";
    public static final String TRANSIT_SCHEDULE_FILE = "transitSchedule.xml";
    public static final String VEHICLES_FILE = "vehicleDefinitions.xml";
    public static final String MAPPED_NETWORK_FILE = "network-mapped.xml";
    public static final String MAPPED_SCHEDULE_FILE = "transitSchedule-mapped.xml";

    private static final String TARGET_CRS = "EPSG:3857";

    private final OsmNetworkBuildConfig networkConfig;
    private final OsmSimplifyOptions simplifyOptions;
    private final double clipMarginMeters;

    public OsmGtfsBundleRunner() {
        this(OsmNetworkBuildConfig.materializeGeometryConfigWithCleanup(), OsmSimplifyOptions.defaults(), 0.0);
    }

    public OsmGtfsBundleRunner(OsmNetworkBuildConfig networkConfig, OsmSimplifyOptions simplifyOptions) {
        this(networkConfig, simplifyOptions, 0.0);
    }

    public OsmGtfsBundleRunner(OsmNetworkBuildConfig networkConfig, OsmSimplifyOptions simplifyOptions,
                               double clipMarginMeters) {
        this.networkConfig = Objects.requireNonNull(networkConfig, "networkConfig");
        this.simplifyOptions = Objects.requireNonNull(simplifyOptions, "simplifyOptions");
        this.clipMarginMeters = clipMarginMeters;
    }

    /** Summary of a produced bundle: artifact paths and the structural counts of each artifact. */
    public record BundleResult(
            Path directory,
            Path networkFile,
            Path facilitiesFile,
            Path transitScheduleFile,
            Path vehiclesFile,
            Path mappedNetworkFile,
            Path mappedScheduleFile,
            int baseNetworkNodes,
            int baseNetworkLinks,
            int facilityCount,
            Integer transitLines,
            Integer transitRoutes,
            Integer departures,
            Integer vehicleTypes,
            Integer vehicles,
            Integer mappedNetworkNodes,
            Integer mappedNetworkLinks,
            List<String> warnings) {
    }

    /**
     * Build a network-only bundle from an OSM file. Transit artifacts are omitted.
     */
    public BundleResult run(Path osmFile, Path outputDirectory) throws IOException {
        return run(osmFile, null, null, outputDirectory);
    }

    /**
     * Build a full bundle from an OSM file and a GTFS feed (folder or zip). When {@code gtfsFeed}
     * is {@code null} the transit artifacts are omitted.
     */
    public BundleResult run(Path osmFile, Path gtfsFeed,
                            GtfsImportConfig.ServiceDateSelection dateSelection,
                            Path outputDirectory) throws IOException {
        Objects.requireNonNull(osmFile, "osmFile");
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        Files.createDirectories(outputDirectory);
        List<String> warnings = new ArrayList<>();

        // ---- Stage 1: OSM -> base network ------------------------------------------------
        OsmImportConfig importConfig = OsmImportConfig.of(osmFile, TARGET_CRS);
        var importResult = new OsmNetworkImporter().read(importConfig);
        OsmNetworkBuildResult built = new OsmMatsimNetworkBuilder().build(importResult, networkConfig);
        OsmSimplifiedNetwork simplified =
                OsmSignalAwareSimplifier.simplify(built, importResult, networkConfig, simplifyOptions);
        var baseNetwork = simplified.network();
        int baseNodes = baseNetwork.getNodes().size();
        int baseLinks = baseNetwork.getLinks().size();

        // ---- OSM -> activity facilities --------------------------------------------------
        OsmFacilityConfig facilityConfig = OsmFacilityConfig.defaults(TARGET_CRS);
        OsmFacilityConverter facilityConverter = new OsmFacilityConverter(facilityConfig);
        ActivityFacilities facilities =
                facilityConverter.convert(new OsmFacilityParser(facilityConfig).parse(osmFile));

        Path networkFile = outputDirectory.resolve(NETWORK_FILE);
        Path facilitiesFile = outputDirectory.resolve(FACILITIES_FILE);
        // Stage-1 artifacts written before any mutating mapping step.
        new NetworkXmlWriter().write(baseNetwork, networkFile);
        new FacilitiesXmlWriter().write(facilities, facilitiesFile);

        Path transitScheduleFile = null;
        Path vehiclesFile = null;
        Path mappedNetworkFile = null;
        Path mappedScheduleFile = null;
        Integer lines = null;
        Integer routes = null;
        Integer departures = null;
        Integer vehicleTypes = null;
        Integer vehicles = null;
        Integer mappedNodes = null;
        Integer mappedLinks = null;

        // ---- Stage 2: GTFS -> unmapped schedule + vehicles -------------------------------
        if (gtfsFeed != null) {
            GtfsImportConfig gtfsConfig = GtfsImportConfig.forFolder(gtfsFeed,
                    dateSelection != null ? dateSelection
                            : GtfsImportConfig.ServiceDateSelection.DAY_WITH_MOST_TRIPS);
            GtfsFeedSet feeds = new GtfsImporter().read(gtfsConfig);
            GtfsTransitBuildResult build = new GtfsTransitScheduleBuilder().build(feeds, gtfsConfig);
            warnings.addAll(build.warnings());

            TransitSchedule unmapped = build.schedule();
            VehicleDefinitions vehicleDefinitions = build.vehicles();

            // Clip GTFS to the network extent so out-of-area stops do not drive artificial-link
            // fabrication. The clip is build-new; vehicles for dropped departures are pruned so the
            // vehicle file contains exactly the vehicles the clipped schedule references.
            TransitScheduleClipper.ClipResult clip =
                    TransitScheduleClipper.clipToNetwork(unmapped, baseNetwork, clipMarginMeters);
            if (clip.stopsDropped() > 0) {
                warnings.add("clipped " + clip.stopsDropped() + " stop facilities and "
                        + clip.routesDropped() + " routes outside the network extent");
            }
            unmapped = clip.schedule();
            vehicleDefinitions = pruneVehicles(vehicleDefinitions, clip.retainedVehicleIds());

            transitScheduleFile = outputDirectory.resolve(TRANSIT_SCHEDULE_FILE);
            vehiclesFile = outputDirectory.resolve(VEHICLES_FILE);
            // Write stage-2 artifacts BEFORE mapping, since mapping mutates the schedule in place.
            new TransitScheduleXmlWriter().write(unmapped, transitScheduleFile);
            new VehiclesXmlWriter().write(vehicleDefinitions, vehiclesFile);

            lines = unmapped.getTransitLines().size();
            routes = countRoutes(unmapped);
            departures = countDepartures(unmapped);
            vehicleTypes = vehicleDefinitions.getVehicleTypes().size();
            vehicles = vehicleDefinitions.getVehicles().size();

            // ---- Stage 3: map the schedule onto the base network -------------------------
            LinkSpatialIndex spatialIndex = new LinkSpatialIndex(baseNetwork, 100.0);
            TransitNetworkMapper mapper =
                    new TransitNetworkMapper(TransitMappingConfig.defaults(), spatialIndex);
            TransitMappingResult mapping = mapper.map(unmapped, baseNetwork);
            warnings.addAll(mapping.warnings());

            mappedNetworkFile = outputDirectory.resolve(MAPPED_NETWORK_FILE);
            mappedScheduleFile = outputDirectory.resolve(MAPPED_SCHEDULE_FILE);
            new NetworkXmlWriter().write(mapping.mappedNetwork(), mappedNetworkFile);
            new TransitScheduleXmlWriter().write(mapping.mappedSchedule(), mappedScheduleFile);

            mappedNodes = mapping.mappedNetwork().getNodes().size();
            mappedLinks = mapping.mappedNetwork().getLinks().size();
        }

        return new BundleResult(outputDirectory, networkFile, facilitiesFile, transitScheduleFile,
                vehiclesFile, mappedNetworkFile, mappedScheduleFile, baseNodes, baseLinks,
                facilities.getFacilities().size(), lines, routes, departures, vehicleTypes, vehicles,
                mappedNodes, mappedLinks, List.copyOf(warnings));
    }

    /**
     * Keep only vehicles whose ids are still referenced by the (clipped) schedule, so the vehicle
     * file is consistent with the departures actually emitted. Vehicle types are retained.
     */
    private static VehicleDefinitions pruneVehicles(VehicleDefinitions all,
                                                     java.util.Set<String> retainedVehicleIds) {
        VehicleDefinitions pruned = new VehicleDefinitions();
        for (VehicleType type : all.getVehicleTypes().values()) {
            pruned.addVehicleType(type);
        }
        for (Vehicle v : all.getVehicles().values()) {
            if (retainedVehicleIds.contains(v.getId().toString())) {
                pruned.addVehicle(v);
            }
        }
        return pruned;
    }

    private static int countRoutes(TransitSchedule schedule) {
        return schedule.getTransitLines().values().stream()
                .mapToInt(l -> l.getRoutes().size()).sum();
    }

    private static int countDepartures(TransitSchedule schedule) {
        return schedule.getTransitLines().values().stream()
                .flatMap(l -> l.getRoutes().values().stream())
                .mapToInt(r -> r.getDepartures().size()).sum();
    }

    /** Convenience CLI: {@code <osm-file> <gtfs-feed-or-'none'> <output-dir>}. */
    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            throw new IllegalArgumentException(
                    "Usage: OsmGtfsBundleRunner <osm-file> <gtfs-feed|none> <output-dir>");
        }
        Path osm = Path.of(args[0]);
        Path gtfs = "none".equalsIgnoreCase(args[1]) ? null : Path.of(args[1]);
        Path out = Path.of(args[2]);
        BundleResult r = new OsmGtfsBundleRunner().run(osm, gtfs, null, out);
        System.err.printf("bundle: base nodes=%d links=%d | facilities=%d | lines=%s routes=%s "
                        + "departures=%s vehicleTypes=%s vehicles=%s | mapped nodes=%s links=%s%n",
                r.baseNetworkNodes(), r.baseNetworkLinks(), r.facilityCount(), r.transitLines(),
                r.transitRoutes(), r.departures(), r.vehicleTypes(), r.vehicles(),
                r.mappedNetworkNodes(), r.mappedNetworkLinks());
    }
}
