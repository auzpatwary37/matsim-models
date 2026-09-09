package com.citymodeler.matsim.models.vehicles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.junit.jupiter.api.Test;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.io.VehiclesXmlReader;
import com.citymodeler.matsim.models.io.VehiclesXmlWriter;

/**
 * Interoperability oracle: validates that the canonical writer output and
 * the current-format fixture conform to the PUBLISHED MATSim
 * vehicleDefinitions v2.0 schema (vendored under {@code matsim-spec/} from
 * matsim.org/files/dtd). This is the external contract check that in-repo
 * round-trips alone cannot provide.
 */
class MatsimVehicleSpecValidationTest {

    private static final String SPEC_BASE = "http://www.matsim.org/files/dtd/";

    @Test
    void canonicalWriterOutputValidatesAgainstCurrentMatsimV2Schema() throws Exception {
        VehicleDefinitions definitions = new VehicleDefinitions();
        VehicleType bus = new VehicleType(Id.create("bus", VehicleType.class));
        bus.setSeatingCapacity(40);
        bus.setStandingCapacity(60);
        bus.setLengthMeters(12.0);
        bus.setWidthMeters(2.5);
        bus.setAccessTimeSeconds(1.5);
        bus.setEgressTimeSeconds(0.75);
        definitions.addVehicleType(bus);
        Vehicle vehicle = new Vehicle(Id.create("v1", Vehicle.class), "bus");
        vehicle.getAttributes().putAttribute("color", "red");
        definitions.addVehicle(vehicle);

        String xml = new VehiclesXmlWriter().writeToString(definitions);
        validator().validate(new StreamSource(new StringReader(xml)));
    }

    @Test
    void currentMatsimStyleFixtureValidatesAndLoads() throws Exception {
        String xml = resourceAsString("matsim-spec/vehicleDefinitions-current-v2.xml");
        validator().validate(new StreamSource(new StringReader(xml)));

        VehicleDefinitions definitions = new VehiclesXmlReader().read(xml);

        VehicleType bus = definitions.getVehicleTypes().get(Id.create("Bus", VehicleType.class));
        assertNotNull(bus);
        assertEquals(40, bus.getSeatingCapacity());
        assertEquals(60, bus.getStandingCapacity());
        assertEquals(12.0, bus.getLengthMeters());
        assertEquals(2.5, bus.getWidthMeters());
        assertEquals(0.5, bus.getAccessTimeSeconds());
        assertEquals(1.5, bus.getEgressTimeSeconds());

        VehicleType car = definitions.getVehicleTypes().get(Id.create("car", VehicleType.class));
        assertNotNull(car);
        assertEquals(4, car.getSeatingCapacity());
        assertEquals(null, car.getStandingCapacity());

        assertEquals(3, definitions.getVehicles().size());
        assertEquals("Bus", definitions.getVehicles().get(Id.create("bus-1", Vehicle.class)).getType());

        assertEquals("serial", bus.getExtraAttributes().getAttribute("doorOperationMode"));
    }

    @Test
    void unknownAttributesSurviveReadWriteRoundTrip() throws Exception {
        String xml = resourceAsString("matsim-spec/vehicleDefinitions-current-v2.xml");
        VehicleDefinitions definitions = new VehiclesXmlReader().read(xml);

        String rewritten = new VehiclesXmlWriter().writeToString(definitions);
        validator().validate(new StreamSource(new StringReader(rewritten)));

        VehicleDefinitions reparsed = new VehiclesXmlReader().read(rewritten);
        VehicleType bus = reparsed.getVehicleTypes().get(Id.create("Bus", VehicleType.class));
        assertEquals("serial", bus.getExtraAttributes().getAttribute("doorOperationMode"));
        assertEquals(0.5, bus.getAccessTimeSeconds());
        assertEquals(1.5, bus.getEgressTimeSeconds());
    }

    @Test
    void unsetAccessEgressDefaultsToOneSecond() {
        VehicleType type = new VehicleType(Id.create("plain", VehicleType.class));
        assertNull(type.getAccessTimeSeconds());
        assertNull(type.getEgressTimeSeconds());
        assertEquals(1.0, type.getEffectiveAccessTimeSeconds());
        assertEquals(1.0, type.getEffectiveEgressTimeSeconds());
    }

    private static Validator validator() throws Exception {
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setResourceResolver((type, namespaceUri, publicId, systemId, baseUri) -> {
            String resource = switch (systemId) {
                case SPEC_BASE + "matsimCommon.xsd" -> "matsim-spec/matsimCommon.xsd";
                case SPEC_BASE + "vehicleDefinitionsEnumTypes.xsd" -> "matsim-spec/vehicleDefinitionsEnumTypes.xsd";
                default -> null;
            };
            if (resource == null) {
                return null;
            }
            InputStream stream = MatsimVehicleSpecValidationTest.class.getClassLoader().getResourceAsStream(resource);
            return new LsInput(stream, systemId);
        });
        try (InputStream schemaStream = MatsimVehicleSpecValidationTest.class.getClassLoader().getResourceAsStream("matsim-spec/vehicleDefinitions_v2.0.xsd")) {
            Schema schema = factory.newSchema(new StreamSource(schemaStream));
            Validator validator = schema.newValidator();
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            return validator;
        }
    }

    private static final class LsInput implements org.w3c.dom.ls.LSInput {
        private final InputStream byteStream;
        private final String systemId;
        private Reader characterStream;

        private LsInput(InputStream byteStream, String systemId) {
            this.byteStream = byteStream;
            this.systemId = systemId;
        }

        @Override
        public void setStringData(String data) {
        }

        @Override
        public String getStringData() {
            return null;
        }

        @Override
        public void setSystemId(String systemId) {
        }

        @Override
        public String getSystemId() {
            return systemId;
        }

        @Override
        public void setPublicId(String publicId) {
        }

        @Override
        public String getPublicId() {
            return null;
        }

        @Override
        public void setCertifiedText(boolean certifiedText) {
        }

        @Override
        public boolean getCertifiedText() {
            return false;
        }

        @Override
        public void setCharacterStream(Reader characterStream) {
        }

        @Override
        public Reader getCharacterStream() {
            if (characterStream == null) {
                characterStream = new InputStreamReader(byteStream, java.nio.charset.StandardCharsets.UTF_8);
            }
            return characterStream;
        }

        @Override
        public void setByteStream(InputStream byteStream) {
        }

        @Override
        public InputStream getByteStream() {
            return null;
        }

        @Override
        public void setEncoding(String encoding) {
        }

        @Override
        public String getEncoding() {
            return java.nio.charset.StandardCharsets.UTF_8.name();
        }

        @Override
        public void setBaseURI(String baseURI) {
        }

        @Override
        public String getBaseURI() {
            return null;
        }
    }

    private static String resourceAsString(String resource) throws Exception {
        try (InputStreamReader reader = new InputStreamReader(
                MatsimVehicleSpecValidationTest.class.getClassLoader().getResourceAsStream(resource))) {
            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                builder.append(buffer, 0, read);
            }
            return builder.toString();
        }
    }
}
