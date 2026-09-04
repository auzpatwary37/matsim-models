package com.citymodeler.matsim.models.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.citymodeler.matsim.models.api.Attributes;

final class XmlSupport {
    private XmlSupport() {
    }

    static Document parse(Path path) {
        try (InputStream inputStream = openInputStream(path)) {
            return parse(inputStream);
        } catch (IOException exception) {
            throw new MatsimParseException("Could not read XML from " + path, exception);
        }
    }

    static Document parse(Path path, String schemaResource) {
        validate(path, schemaResource);
        return parse(path);
    }

    static Document parse(String xml) {
        return parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    static Document parse(String xml, String schemaResource) {
        byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
        validate(new ByteArrayInputStream(bytes), schemaResource);
        return parse(new ByteArrayInputStream(bytes));
    }

    static Document parse(InputStream inputStream) {
        final byte[] xml;
        try {
            xml = inputStream.readAllBytes();
        } catch (IOException exception) {
            throw new MatsimParseException("Could not read XML", exception);
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setXIncludeAware(false);
            requireFeature(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
            requireFeature(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
            requireFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
            requireFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
            requireFeature(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            requireAttribute(factory, XMLConstants.ACCESS_EXTERNAL_DTD, "");
            requireAttribute(factory, XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(stripLegacyDoctype(xml)));
            document.getDocumentElement().normalize();
            return document;
        } catch (IOException | ParserConfigurationException | SAXException exception) {
            throw new MatsimParseException("Could not parse XML", exception);
        }
    }

    static Document parse(InputStream inputStream, String schemaResource) {
        try {
            byte[] bytes = inputStream.readAllBytes();
            validate(new ByteArrayInputStream(bytes), schemaResource);
            return parse(new ByteArrayInputStream(bytes));
        } catch (IOException exception) {
            throw new MatsimParseException("Could not read XML", exception);
        }
    }

    /**
     * Removes a legacy MATSIM {@code <!DOCTYPE ...>} declaration before the
     * hardened parser sees the document. The declaration is scanned
     * structurally and excised; the referenced DTD is never fetched and
     * external entity resolution stays disabled in the parser. Internal DTD
     * subsets are refused explicitly rather than stripped, because rewriting
     * them would require resolving entities.
     */
    static byte[] stripLegacyDoctype(byte[] xml) {
        int n = xml.length;
        int i = 0;
        if (n >= 3 && (xml[0] & 0xFF) == 0xEF && (xml[1] & 0xFF) == 0xBB && (xml[2] & 0xFF) == 0xBF) {
            i = 3;
        }
        while (i < n) {
            int b = xml[i] & 0xFF;
            if (isXmlWhitespace(b)) {
                i++;
                continue;
            }
            if (b != '<') {
                return xml;
            }
            if (startsWith(xml, i, "<!--")) {
                int close = indexOfAscii(xml, i + 4, "-->");
                if (close < 0) {
                    return xml;
                }
                i = close + 3;
                continue;
            }
            if (startsWith(xml, i, "<?")) {
                int close = indexOfAscii(xml, i + 2, "?>");
                if (close < 0) {
                    return xml;
                }
                i = close + 2;
                continue;
            }
            if (startsWithCaseInsensitive(xml, i, "<!DOCTYPE")) {
                int end = scanLegacyDoctypeEnd(xml, i + "<!DOCTYPE".length());
                byte[] stripped = new byte[n - (end - i)];
                System.arraycopy(xml, 0, stripped, 0, i);
                System.arraycopy(xml, end, stripped, i, n - end);
                return stripped;
            }
            return xml;
        }
        return xml;
    }

    private static int scanLegacyDoctypeEnd(byte[] xml, int i) {
        int n = xml.length;
        while (i < n && isXmlWhitespace(xml[i] & 0xFF)) {
            i++;
        }
        if (i >= n || !isNameStart(xml[i] & 0xFF)) {
            throw new MatsimParseException(
                    "Malformed legacy DOCTYPE declaration: missing document element name.");
        }
        i++;
        while (i < n && isNameChar(xml[i] & 0xFF)) {
            i++;
        }
        while (i < n) {
            int b = xml[i] & 0xFF;
            if (isXmlWhitespace(b)) {
                i++;
            } else if (b == '>') {
                return i + 1;
            } else if (b == '"' || b == '\'') {
                int close = i + 1;
                while (close < n && (xml[close] & 0xFF) != b) {
                    close++;
                }
                if (close >= n) {
                    throw new MatsimParseException(
                            "Malformed legacy DOCTYPE declaration: unterminated quoted string.");
                }
                i = close + 1;
            } else if (b == '[') {
                throw new MatsimParseException(
                        "Unsupported legacy DOCTYPE declaration: internal DTD subset found. "
                                + "Remove the DTD block from the file; external DTDs are never loaded.");
            } else if (b == ']') {
                throw new MatsimParseException(
                        "Malformed legacy DOCTYPE declaration: unexpected ']' outside an internal subset.");
            } else if (isNameStart(b)) {
                i++;
                while (i < n && isNameChar(xml[i] & 0xFF)) {
                    i++;
                }
            } else {
                throw new MatsimParseException(
                        "Malformed legacy DOCTYPE declaration: unexpected character.");
            }
        }
        throw new MatsimParseException("Malformed legacy DOCTYPE declaration: missing closing '>'.");
    }

    private static boolean isXmlWhitespace(int b) {
        return b == ' ' || b == '\t' || b == '\r' || b == '\n';
    }

    private static boolean isNameStart(int b) {
        return (b >= 'A' && b <= 'Z') || (b >= 'a' && b <= 'z') || b == '_';
    }

    private static boolean isNameChar(int b) {
        return isNameStart(b) || (b >= '0' && b <= '9') || b == '.' || b == '-' || b == ':';
    }

    private static boolean startsWith(byte[] xml, int at, String token) {
        if (at + token.length() > xml.length) {
            return false;
        }
        for (int k = 0; k < token.length(); k++) {
            if ((xml[at + k] & 0xFF) != (byte) token.charAt(k)) {
                return false;
            }
        }
        return true;
    }

    private static boolean startsWithCaseInsensitive(byte[] xml, int at, String asciiToken) {
        if (at + asciiToken.length() > xml.length) {
            return false;
        }
        for (int k = 0; k < asciiToken.length(); k++) {
            if (Character.toUpperCase(xml[at + k] & 0xFF) != asciiToken.charAt(k)) {
                return false;
            }
        }
        return true;
    }

    private static int indexOfAscii(byte[] xml, int from, String token) {
        for (int i = from; i <= xml.length - token.length(); i++) {
            boolean matched = true;
            for (int k = 0; k < token.length(); k++) {
                if ((xml[i + k] & 0xFF) != (byte) token.charAt(k)) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                return i;
            }
        }
        return -1;
    }

    static void validate(Path path, String schemaResource) {
        try (InputStream inputStream = openInputStream(path)) {
            validate(inputStream, schemaResource);
        } catch (IOException exception) {
            throw new MatsimParseException("Could not read XML from " + path, exception);
        }
    }

    static void validate(InputStream inputStream, String schemaResource) {
        try (InputStream schemaStream = XmlSupport.class.getResourceAsStream(schemaResource)) {
            if (schemaStream == null) {
                throw new MatsimValidationException("Schema resource not found: " + schemaResource);
            }
            final byte[] xml;
            try {
                xml = inputStream.readAllBytes();
            } catch (IOException exception) {
                throw new MatsimValidationException("Could not read XML", exception);
            }
            var schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            schemaFactory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            schemaFactory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var schema = schemaFactory.newSchema(new StreamSource(schemaStream));
            var validator = schema.newValidator();
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            validator.validate(new StreamSource(new ByteArrayInputStream(stripLegacyDoctype(xml))));
        } catch (MatsimValidationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new MatsimValidationException("XML schema validation failed", exception);
        }
    }

    static Document newDocument() {
        try {
            return DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException exception) {
            throw new MatsimModelException("Could not create XML document", exception);
        }
    }

    static void write(Document document, Path path) {
        try (OutputStream outputStream = openOutputStream(path)) {
            write(document, outputStream);
        } catch (IOException exception) {
            throw new MatsimWriteException("Could not write XML to " + path, exception);
        }
    }

    static void write(Document document, OutputStream outputStream) {
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            var transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
            transformer.transform(new DOMSource(document), new StreamResult(outputStream));
        } catch (Exception exception) {
            throw new MatsimWriteException("Could not write XML", exception);
        }
    }

    static String writeToString(Document document) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        write(document, outputStream);
        return outputStream.toString(StandardCharsets.UTF_8);
    }

    static Element child(Element parent, String name) {
        for (Element child : children(parent, name)) {
            return child;
        }
        return null;
    }

    static List<Element> children(Element parent, String name) {
        List<Element> children = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element element && (name == null || name.equals(element.getTagName()))) {
                children.add(element);
            }
        }
        return children;
    }

    static String attr(Element element, String name) {
        return element.hasAttribute(name) ? element.getAttribute(name) : null;
    }

    static Map<String, String> attributes(Element element) {
        Map<String, String> result = new TreeMap<>();
        var attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            var attr = (org.w3c.dom.Attr) attributes.item(i);
            result.put(attr.getName(), attr.getValue());
        }
        return result;
    }

    static double requiredDouble(Element element, String name) {
        String value = attr(element, name);
        if (value == null || value.isBlank()) {
            throw new MatsimModelException("Missing required numeric attribute: " + name);
        }
        return Double.parseDouble(value);
    }

    static double optionalDouble(Element element, String name, double defaultValue) {
        String value = attr(element, name);
        return value == null || value.isBlank() ? defaultValue : Double.parseDouble(value);
    }

    static boolean optionalBoolean(Element element, String name, boolean defaultValue) {
        String value = attr(element, name);
        return value == null || value.isBlank() ? defaultValue : Boolean.parseBoolean(value);
    }

    static void readAttributes(Element parent, Attributes attributes) {
        Element attributesElement = child(parent, "attributes");
        if (attributesElement == null) {
            return;
        }
        for (Element attributeElement : children(attributesElement, "attribute")) {
            String name = attr(attributeElement, "name");
            if (name == null || name.isBlank()) {
                continue;
            }
            attributes.putAttribute(name, convert(attributeElement.getTextContent(), attr(attributeElement, "class")));
        }
    }

    static void appendAttributes(Document document, Element parent, Attributes attributes) {
        if (attributes.getAsMap().isEmpty()) {
            return;
        }
        Element attributesElement = document.createElement("attributes");
        for (Map.Entry<String, Object> entry : new TreeMap<>(attributes.getAsMap()).entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            Element attributeElement = document.createElement("attribute");
            attributeElement.setAttribute("name", entry.getKey());
            attributeElement.setAttribute("class", classHint(entry.getValue()));
            attributeElement.setTextContent(entry.getValue().toString());
            attributesElement.appendChild(attributeElement);
        }
        parent.appendChild(attributesElement);
    }

    static void setIfPresent(Element element, String name, Object value) {
        if (value != null) {
            element.setAttribute(name, value.toString());
        }
    }

    static InputStream openInputStream(Path path) throws IOException {
        InputStream inputStream = Files.newInputStream(path);
        if (path.toString().endsWith(".gz")) {
            try {
                return new GZIPInputStream(inputStream);
            } catch (IOException exception) {
                inputStream.close();
                throw exception;
            }
        }
        return inputStream;
    }

    static OutputStream openOutputStream(Path path) throws IOException {
        OutputStream outputStream = Files.newOutputStream(path);
        if (path.toString().endsWith(".gz")) {
            try {
                return new GZIPOutputStream(outputStream);
            } catch (IOException exception) {
                outputStream.close();
                throw exception;
            }
        }
        return outputStream;
    }

    private static Object convert(String value, String className) {
        if (Double.class.getName().equals(className)) {
            return Double.valueOf(value);
        }
        if (Integer.class.getName().equals(className)) {
            return Integer.valueOf(value);
        }
        if (Long.class.getName().equals(className)) {
            return Long.valueOf(value);
        }
        if (Boolean.class.getName().equals(className)) {
            return Boolean.valueOf(value);
        }
        return value;
    }

    private static String classHint(Object value) {
        if (value instanceof Double) {
            return Double.class.getName();
        }
        if (value instanceof Integer) {
            return Integer.class.getName();
        }
        if (value instanceof Long) {
            return Long.class.getName();
        }
        if (value instanceof Boolean) {
            return Boolean.class.getName();
        }
        return String.class.getName();
    }

    private static void requireFeature(DocumentBuilderFactory factory, String name, boolean value) {
        try {
            factory.setFeature(name, value);
        } catch (ParserConfigurationException exception) {
            throw new MatsimModelException("Could not apply required XML parser security feature: " + name, exception);
        }
    }

    private static void requireAttribute(DocumentBuilderFactory factory, String name, String value) {
        try {
            factory.setAttribute(name, value);
        } catch (IllegalArgumentException exception) {
            throw new MatsimModelException("Could not apply required XML parser security attribute: " + name, exception);
        }
    }
}
