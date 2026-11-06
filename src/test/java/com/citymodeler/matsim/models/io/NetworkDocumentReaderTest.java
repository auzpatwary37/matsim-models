package com.citymodeler.matsim.models.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.citymodeler.matsim.models.lanes.Lane;
import com.citymodeler.matsim.models.lanes.Lanes;
import com.citymodeler.matsim.models.validation.ValidationIssue;
import com.citymodeler.matsim.models.validation.ValidationSeverity;

class NetworkDocumentReaderTest {

    @TempDir
    Path dir;

    private static final String NETWORK_HEAD = """
            <?xml version="1.0" encoding="UTF-8"?>
            <network>
              <nodes>
                <node id="n1" x="0.0" y="0.0"/>
                <node id="n2" x="100.0" y="0.0"/>
                <node id="n3" x="200.0" y="0.0"/>
              </nodes>
              <links>
                <link id="l1" from="n1" to="n2" length="100" freespeed="10" capacity="100" permlanes="1" modes="car"/>
                <link id="l2" from="n2" to="n3" length="100" freespeed="10" capacity="100" permlanes="1" modes="car"/>
              </links>
            """;

    private Path write(String name, String xml) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, xml, StandardCharsets.UTF_8);
        return file;
    }

    private static String withTail(String laneBlock) {
        return NETWORK_HEAD + laneBlock + "\n</network>\n";
    }

    private static boolean hasCode(LaneReadResult result, String code) {
        return result.issues().stream().anyMatch(issue -> code.equals(issue.getCode()));
    }

    @Test
    void networkWithoutLanesIsAbsent() throws IOException {
        Path file = write("plain.xml", withTail(""));
        NetworkBundle bundle = new NetworkDocumentReader().readBundle(file);
        assertEquals(LaneReadState.ABSENT, bundle.embeddedLanes().state());
        assertEquals(2, bundle.network().getLinks().size());
        assertTrue(bundle.embeddedLanes().issues().isEmpty());
    }

    @Test
    void readsEmbeddedCanonicalV2Block() throws IOException {
        String lanes = """
                  <laneDefinitions xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                      xsi:noNamespaceSchemaLocation="http://www.matsim.org/files/dtd/laneDefinitions_v2.0.xsd">
                    <lanesToLinkAssignment linkIdRef="l1">
                      <lane id="a">
                        <leadsTo><toLink refId="l2"/></leadsTo>
                        <representedLanes number="2"/>
                        <capacity vehiclesPerHour="600"/>
                      </lane>
                    </lanesToLinkAssignment>
                  </laneDefinitions>
                """;
        Path file = write("v2.xml", withTail(lanes));
        NetworkBundle bundle = new NetworkDocumentReader().readBundle(file);
        LaneReadResult lanesResult = bundle.embeddedLanes();
        assertEquals(LaneReadState.PRESENT, lanesResult.state());
        assertEquals(LaneDialect.MATSIM_V2_0, lanesResult.dialect().orElseThrow());
        Lanes model = lanesResult.lanes().orElseThrow();
        Lane lane = model.getLanesToLinkAssignments().get(
                com.citymodeler.matsim.models.api.Id.create("l1",
                        com.citymodeler.matsim.models.network.Link.class))
                .getLanes().get(com.citymodeler.matsim.models.api.Id.create("a", Lane.class));
        assertNotNull(lane);
        assertEquals(1, lane.getToLinkIds().size());
        assertEquals(Double.valueOf(2.0), lane.getRepresentedLanesNumber());
        assertEquals(600.0, lane.getCapacityVehiclesPerHour());
    }

    @Test
    void readsEmbeddedCanonicalV11Block() throws IOException {
        String lanes = """
                  <laneDefinitions>
                    <lanesToLinkAssignment linkIdRef="l1">
                      <lane id="a">
                        <toLink refId="l2"/>
                        <length meter="30.0"/>
                      </lane>
                    </lanesToLinkAssignment>
                  </laneDefinitions>
                """;
        Path file = write("v11.xml", withTail(lanes));
        NetworkBundle bundle = new NetworkDocumentReader().readBundle(file);
        LaneReadResult lanesResult = bundle.embeddedLanes();
        assertEquals(LaneReadState.PRESENT, lanesResult.state());
        assertEquals(LaneDialect.MATSIM_V1_1, lanesResult.dialect().orElseThrow());
    }

    @Test
    void readsEmbeddedModelsAssignmentBlock() throws IOException {
        String lanes = """
                  <lanes>
                    <assignment linkId="l1">
                      <lane id="a" toLinkIds="l2" capacityVehiclesPerHour="600"/>
                    </assignment>
                  </lanes>
                """;
        Path file = write("models.xml", withTail(lanes));
        NetworkBundle bundle = new NetworkDocumentReader().readBundle(file);
        assertEquals(LaneReadState.PRESENT, bundle.embeddedLanes().state());
        assertEquals(LaneDialect.MODELS_1_0, bundle.embeddedLanes().dialect().orElseThrow());
    }

    @Test
    void explicitlyEmptyV2EnvelopeIsPresentEmpty() throws IOException {
        String lanes = """
                  <laneDefinitions xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                      xsi:noNamespaceSchemaLocation="http://www.matsim.org/files/dtd/laneDefinitions_v2.0.xsd"/>
                """;
        Path file = write("empty.xml", withTail(lanes));
        LaneReadResult result = new NetworkDocumentReader().readBundle(file).embeddedLanes();
        assertEquals(LaneReadState.PRESENT, result.state());
        assertTrue(result.lanes().orElseThrow().getLanesToLinkAssignments().isEmpty());
    }

    @Test
    void hintlessEmptyEnvelopeIsUnsupportedNotGuessed() throws IOException {
        Path file = write("hintless.xml", withTail("  <laneDefinitions/>"));
        LaneReadResult result = new NetworkDocumentReader().readBundle(file).embeddedLanes();
        assertEquals(LaneReadState.UNSUPPORTED, result.state());
        assertFalse(result.issues().isEmpty());
    }

    @Test
    void competingLaneBlocksAreInvalid() throws IOException {
        String lanes = """
                  <laneDefinitions xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                      xsi:noNamespaceSchemaLocation="http://www.matsim.org/files/dtd/laneDefinitions_v2.0.xsd"/>
                  <lanes>
                    <assignment linkId="l1"><lane id="a" toLinkIds="l2"/></assignment>
                  </lanes>
                """;
        Path file = write("competing.xml", withTail(lanes));
        LaneReadResult result = new NetworkDocumentReader().readBundle(file).embeddedLanes();
        assertEquals(LaneReadState.INVALID, result.state());
        assertTrue(hasCode(result, "lanes.competing_blocks"));
    }

    @Test
    void duplicateLaneIdsAreInvalid() throws IOException {
        String lanes = """
                  <laneDefinitions xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                      xsi:noNamespaceSchemaLocation="http://www.matsim.org/files/dtd/laneDefinitions_v2.0.xsd">
                    <lanesToLinkAssignment linkIdRef="l1">
                      <lane id="a"><leadsTo><toLink refId="l2"/></leadsTo></lane>
                      <lane id="a"><leadsTo><toLink refId="l2"/></leadsTo></lane>
                    </lanesToLinkAssignment>
                  </laneDefinitions>
                """;
        Path file = write("duplane.xml", withTail(lanes));
        LaneReadResult result = new NetworkDocumentReader().readBundle(file).embeddedLanes();
        assertEquals(LaneReadState.INVALID, result.state());
        assertTrue(hasCode(result, "lanes.duplicate_lane"));
    }

    @Test
    void duplicateAssignmentsAreInvalid() throws IOException {
        String lanes = """
                  <laneDefinitions xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                      xsi:noNamespaceSchemaLocation="http://www.matsim.org/files/dtd/laneDefinitions_v2.0.xsd">
                    <lanesToLinkAssignment linkIdRef="l1">
                      <lane id="a"><leadsTo><toLink refId="l2"/></leadsTo></lane>
                    </lanesToLinkAssignment>
                    <lanesToLinkAssignment linkIdRef="l1">
                      <lane id="b"><leadsTo><toLink refId="l2"/></leadsTo></lane>
                    </lanesToLinkAssignment>
                  </laneDefinitions>
                """;
        Path file = write("dupassign.xml", withTail(lanes));
        LaneReadResult result = new NetworkDocumentReader().readBundle(file).embeddedLanes();
        assertEquals(LaneReadState.INVALID, result.state());
        assertTrue(hasCode(result, "lanes.duplicate_assignment"));
    }

    @Test
    void unknownTargetLinkIsInvalidWithStructuredOwner() throws IOException {
        String lanes = """
                  <laneDefinitions xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                      xsi:noNamespaceSchemaLocation="http://www.matsim.org/files/dtd/laneDefinitions_v2.0.xsd">
                    <lanesToLinkAssignment linkIdRef="l1">
                      <lane id="a"><leadsTo><toLink refId="l-nope"/></leadsTo></lane>
                    </lanesToLinkAssignment>
                  </laneDefinitions>
                """;
        Path file = write("badref.xml", withTail(lanes));
        LaneReadResult result = new NetworkDocumentReader().readBundle(file).embeddedLanes();
        assertEquals(LaneReadState.INVALID, result.state());
        assertTrue(hasCode(result, "lanes.unresolved_link_ref"));
        ValidationIssue issue = result.issues().stream()
                .filter(candidate -> "lanes.unresolved_link_ref".equals(candidate.getCode()))
                .findFirst().orElseThrow();
        assertEquals("a", issue.getOwner().orElseThrow().entityId());
        assertEquals("l1", issue.getOwner().orElseThrow().parentLinkId().orElseThrow());
        assertEquals("l-nope", issue.getReferencedEntity().orElseThrow().entityId());
        assertEquals(file, issue.getSourceLocation().orElseThrow().source());
    }

    @Test
    void nonNumericAttributeIsInvalidNotNumberFormatException() throws IOException {
        String lanes = """
                  <laneDefinitions xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                      xsi:noNamespaceSchemaLocation="http://www.matsim.org/files/dtd/laneDefinitions_v2.0.xsd">
                    <lanesToLinkAssignment linkIdRef="l1">
                      <lane id="a"><leadsTo><toLink refId="l2"/></leadsTo>
                        <capacity vehiclesPerHour="abc"/></lane>
                    </lanesToLinkAssignment>
                  </laneDefinitions>
                """;
        Path file = write("badnum.xml", withTail(lanes));
        LaneReadResult result = new NetworkDocumentReader().readBundle(file).embeddedLanes();
        assertEquals(LaneReadState.INVALID, result.state());
        assertTrue(hasCode(result, "lanes.malformed_value"));
    }

    @Test
    void prefixedNamespaceDocumentParses() throws IOException {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <m:network xmlns:m="http://www.matsim.org/files/dtd">
                  <m:nodes><m:node id="n1" x="0" y="0"/><m:node id="n2" x="100" y="0"/><m:node id="n3" x="200" y="0"/></m:nodes>
                  <m:links>
                    <m:link id="l1" from="n1" to="n2" length="100" freespeed="10" capacity="100" permlanes="1" modes="car"/>
                    <m:link id="l2" from="n2" to="n3" length="100" freespeed="10" capacity="100" permlanes="1" modes="car"/>
                  </m:links>
                  <m:laneDefinitions xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                      xsi:noNamespaceSchemaLocation="http://www.matsim.org/files/dtd/laneDefinitions_v2.0.xsd">
                    <m:lanesToLinkAssignment linkIdRef="l1">
                      <m:lane id="a"><m:leadsTo><m:toLink refId="l2"/></m:leadsTo></m:lane>
                    </m:lanesToLinkAssignment>
                  </m:laneDefinitions>
                </m:network>
                """;
        Path file = write("ns.xml", xml);
        LaneReadResult result = new NetworkDocumentReader().readBundle(file).embeddedLanes();
        assertEquals(LaneReadState.PRESENT, result.state());
        assertEquals(LaneDialect.MATSIM_V2_0, result.dialect().orElseThrow());
    }

    @Test
    void networkIssuesStaySeparateFromLaneIssues() throws IOException {
        String lanes = """
                  <laneDefinitions xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                      xsi:noNamespaceSchemaLocation="http://www.matsim.org/files/dtd/laneDefinitions_v2.0.xsd">
                    <lanesToLinkAssignment linkIdRef="l1">
                      <lane id="a"><leadsTo><toLink refId="l2"/></leadsTo></lane>
                    </lanesToLinkAssignment>
                  </laneDefinitions>
                """;
        String xml = NETWORK_HEAD.replace("capacity=\"100\" permlanes", "capacity=\"0\" permlanes")
                + lanes + "\n</network>\n";
        Path file = write("split.xml", xml);
        NetworkBundle bundle = new NetworkDocumentReader().readBundle(file);
        assertEquals(LaneReadState.PRESENT, bundle.embeddedLanes().state());
        assertTrue(bundle.networkIssues().stream().anyMatch(ValidationIssue::isError),
                "nonpositive capacity should produce network errors");
    }

    @Test
    void malformedWholeXmlThrowsBundleReadException() throws IOException {
        Path file = write("broken.xml", "<network><nodes>");
        BundleReadException exception = assertThrows(BundleReadException.class,
                () -> new NetworkDocumentReader().readBundle(file));
        assertFalse(exception.getIssues().isEmpty());
        assertEquals(ValidationSeverity.ERROR, exception.getIssues().get(0).getSeverity());
    }

    @Test
    void missingFileThrowsBundleReadException() {
        assertThrows(BundleReadException.class,
                () -> new NetworkDocumentReader().readBundle(dir.resolve("nope.xml")));
    }

    @Test
    void laneCycleIsInvalid() throws IOException {
        String lanes = """
                  <laneDefinitions xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                      xsi:noNamespaceSchemaLocation="http://www.matsim.org/files/dtd/laneDefinitions_v2.0.xsd">
                    <lanesToLinkAssignment linkIdRef="l1">
                      <lane id="a"><leadsTo><toLane refId="b"/></leadsTo></lane>
                    </lanesToLinkAssignment>
                    <lanesToLinkAssignment linkIdRef="l2">
                      <lane id="b"><leadsTo><toLane refId="a"/></leadsTo></lane>
                    </lanesToLinkAssignment>
                  </laneDefinitions>
                """;
        Path file = write("cycle.xml", withTail(lanes));
        LaneReadResult result = new NetworkDocumentReader().readBundle(file).embeddedLanes();
        assertEquals(LaneReadState.INVALID, result.state());
        assertTrue(hasCode(result, "lanes.cycle"));
    }

    @Test
    void chainLongerThanFiveHopsIsFullyParsed() throws IOException {
        StringBuilder nodes = new StringBuilder("  <nodes>\n");
        StringBuilder links = new StringBuilder("  <links>\n");
        StringBuilder assignments = new StringBuilder();
        for (int i = 1; i <= 7; i++) {
            nodes.append("    <node id=\"n").append(i).append("\" x=\"").append((i - 1) * 100.0)
                    .append("\" y=\"0.0\"/>\n");
        }
        nodes.append("  </nodes>\n");
        for (int i = 1; i <= 6; i++) {
            links.append("    <link id=\"l").append(i).append("\" from=\"n").append(i)
                    .append("\" to=\"n").append(i + 1)
                    .append("\" length=\"100\" freespeed=\"10\" capacity=\"100\" permlanes=\"1\" modes=\"car\"/>\n");
        }
        links.append("  </links>\n");
        for (int i = 1; i <= 6; i++) {
            String to = i < 6 ? "<toLane refId=\"l" + (i + 1) + "a\"/>" : "<toLink refId=\"l6\"/>";
            assignments.append("    <lanesToLinkAssignment linkIdRef=\"l").append(i)
                    .append("\"><lane id=\"l").append(i).append("a\"><leadsTo>").append(to)
                    .append("</leadsTo></lane></lanesToLinkAssignment>\n");
        }
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <network>
                %s%s  <laneDefinitions xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                      xsi:noNamespaceSchemaLocation="http://www.matsim.org/files/dtd/laneDefinitions_v2.0.xsd">
                %s  </laneDefinitions>
                </network>
                """.formatted(nodes, links, assignments);
        Path file = write("chain.xml", xml);
        LaneReadResult result = new NetworkDocumentReader().readBundle(file).embeddedLanes();
        assertEquals(LaneReadState.PRESENT, result.state());
        assertEquals(6, result.lanes().orElseThrow().getLanesToLinkAssignments().size());
    }

    @Test
    void mixedDialectStructuresAreInvalid() throws IOException {
        String lanes = """
                  <laneDefinitions xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                      xsi:noNamespaceSchemaLocation="http://www.matsim.org/files/dtd/laneDefinitions_v2.0.xsd">
                    <lanesToLinkAssignment linkIdRef="l1">
                      <lane id="a"><leadsTo><toLink refId="l2"/></leadsTo></lane>
                      <lane id="b" toLinkIds="l2"/>
                    </lanesToLinkAssignment>
                  </laneDefinitions>
                """;
        Path file = write("mixed.xml", withTail(lanes));
        LaneReadResult result = new NetworkDocumentReader().readBundle(file).embeddedLanes();
        assertEquals(LaneReadState.INVALID, result.state());
        assertTrue(hasCode(result, "lanes.mixed_dialect"));
    }

    @Test
    void unknownRootIsUnsupportedNotSilentlyEmpty() throws IOException {
        Path file = write("other.xml",
                "<?xml version=\"1.0\"?>\n<garbage><stuff/></garbage>\n");
        LaneReadResult result = new LanesXmlReader().readResult(
                file, new NetworkXmlReader().read(write("net2.xml", withTail(""))));
        assertEquals(LaneReadState.UNSUPPORTED, result.state());
    }

    @Test
    void explicitResultChecksReferencesAgainstNetwork() throws IOException {
        String lanes = """
                <laneDefinitions xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                    xsi:noNamespaceSchemaLocation="http://www.matsim.org/files/dtd/laneDefinitions_v2.0.xsd">
                  <lanesToLinkAssignment linkIdRef="l-ghost">
                    <lane id="a"><leadsTo><toLink refId="l2"/></leadsTo></lane>
                  </lanesToLinkAssignment>
                </laneDefinitions>
                """;
        Path lanesFile = write("explicit.xml", lanes);
        Path networkFile = write("net.xml", withTail(""));
        var network = new NetworkXmlReader().read(networkFile);
        LaneReadResult result = new LanesXmlReader().readResult(lanesFile, network);
        assertEquals(LaneReadState.INVALID, result.state());
        assertTrue(hasCode(result, "lanes.missing_parent_link"));
    }

    @Test
    void representedLanesAndLengthSurviveV2RoundTrip() {
        Lanes lanes = new Lanes();
        var assignment = new com.citymodeler.matsim.models.lanes.LanesToLinkAssignment(
                com.citymodeler.matsim.models.api.Id.create("l1",
                        com.citymodeler.matsim.models.network.Link.class));
        Lane lane = new Lane(com.citymodeler.matsim.models.api.Id.create("a", Lane.class));
        lane.addToLinkId(com.citymodeler.matsim.models.api.Id.create("l2",
                com.citymodeler.matsim.models.network.Link.class));
        lane.setRepresentedLanesNumber(3.0);
        lane.setLengthMeter(42.0);
        assignment.addLane(lane);
        lanes.addAssignment(assignment);

        String xml = new LanesXmlWriter().writeToString(lanes);
        Lanes reread = new LanesXmlReader().read(xml);
        Lane back = reread.getLanesToLinkAssignments().values().iterator().next()
                .getLanes().values().iterator().next();
        assertEquals(Double.valueOf(3.0), back.getRepresentedLanesNumber());
        assertEquals(Double.valueOf(42.0), back.getLengthMeter());
    }
}
