package com.citymodeler.matsim.models.io;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import com.citymodeler.matsim.models.events.ActivityEndEvent;
import com.citymodeler.matsim.models.events.ActivityStartEvent;
import com.citymodeler.matsim.models.events.ArrivalEvent;
import com.citymodeler.matsim.models.events.DepartureEvent;
import com.citymodeler.matsim.models.events.GenericEvent;
import com.citymodeler.matsim.models.events.LinkEnterEvent;
import com.citymodeler.matsim.models.events.LinkLeaveEvent;
import com.citymodeler.matsim.models.events.MatsimEvent;
import com.citymodeler.matsim.models.events.MatsimEventHandler;
import com.citymodeler.matsim.models.events.PersonEntersVehicleEvent;
import com.citymodeler.matsim.models.events.PersonLeavesVehicleEvent;

public final class EventsXmlReader {

    public void read(Path path, MatsimEventHandler handler) {
        try (InputStream inputStream = Files.newInputStream(path)) {
            read(inputStream, handler);
        } catch (IOException exception) {
            throw new MatsimParseException("Could not read events from " + path, exception);
        }
    }

    public void readString(String xml, MatsimEventHandler handler) {
        read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), handler);
    }

    public void read(InputStream inputStream, MatsimEventHandler handler) {
        XMLStreamReader reader = null;
        try {
            reader = createSafeInputFactory().createXMLStreamReader(inputStream);
            while (reader.hasNext()) {
                int token = reader.next();
                if (token == XMLStreamConstants.START_ELEMENT && "event".equals(reader.getLocalName())) {
                    dispatch(parseEvent(reader), handler);
                }
            }
        } catch (XMLStreamException exception) {
            throw new MatsimParseException("Could not parse events XML", exception);
        } finally {
            closeQuietly(reader);
        }
    }

    public Stream<MatsimEvent> stream(Path path) {
        InputStream inputStream;
        try {
            inputStream = Files.newInputStream(path);
        } catch (IOException exception) {
            throw new MatsimParseException("Could not read events from " + path, exception);
        }
        try {
            XMLStreamReader reader = createSafeInputFactory().createXMLStreamReader(inputStream);
            EventSpliterator spliterator = new EventSpliterator(reader, inputStream);
            return StreamSupport.stream(spliterator, false).onClose(spliterator::close);
        } catch (XMLStreamException exception) {
            closeQuietly(inputStream);
            throw new MatsimParseException("Could not create events XML stream", exception);
        }
    }

    private static MatsimEvent parseEvent(XMLStreamReader reader) {
        Map<String, String> attributes = attributes(reader);
        String type = attributes.get("type");
        if (type == null || type.isBlank()) {
            throw new MatsimParseException("Missing required event type attribute");
        }
        double time = Double.parseDouble(attributes.getOrDefault("time", "0.0"));

        if ("actstart".equals(type)) {
            return new ActivityStartEvent(time, type, attributes);
        }
        if ("actend".equals(type)) {
            return new ActivityEndEvent(time, type, attributes);
        }
        if ("departure".equals(type)) {
            return new DepartureEvent(time, type, attributes);
        }
        if ("arrival".equals(type)) {
            return new ArrivalEvent(time, type, attributes);
        }
        if ("entered link".equals(type) || "enteredLink".equals(type) || "linkEnter".equals(type)) {
            return new LinkEnterEvent(time, type, attributes);
        }
        if ("left link".equals(type) || "leftLink".equals(type) || "linkLeave".equals(type)) {
            return new LinkLeaveEvent(time, type, attributes);
        }
        if ("PersonEntersVehicle".equals(type) || "personEntersVehicle".equals(type)) {
            return new PersonEntersVehicleEvent(time, type, attributes);
        }
        if ("PersonLeavesVehicle".equals(type) || "personLeavesVehicle".equals(type)) {
            return new PersonLeavesVehicleEvent(time, type, attributes);
        }
        return new GenericEvent(time, type, attributes);
    }

    private static Map<String, String> attributes(XMLStreamReader reader) {
        Map<String, String> attributes = new LinkedHashMap<>();
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            attributes.put(reader.getAttributeLocalName(i), reader.getAttributeValue(i));
        }
        return attributes;
    }

    private static void dispatch(MatsimEvent event, MatsimEventHandler handler) {
        if (event instanceof ActivityStartEvent activityStartEvent) {
            handler.handleActivityStart(activityStartEvent);
        } else if (event instanceof ActivityEndEvent activityEndEvent) {
            handler.handleActivityEnd(activityEndEvent);
        } else if (event instanceof DepartureEvent departureEvent) {
            handler.handleDeparture(departureEvent);
        } else if (event instanceof ArrivalEvent arrivalEvent) {
            handler.handleArrival(arrivalEvent);
        } else if (event instanceof LinkEnterEvent linkEnterEvent) {
            handler.handleLinkEnter(linkEnterEvent);
        } else if (event instanceof LinkLeaveEvent linkLeaveEvent) {
            handler.handleLinkLeave(linkLeaveEvent);
        } else if (event instanceof PersonEntersVehicleEvent entersVehicleEvent) {
            handler.handlePersonEntersVehicle(entersVehicleEvent);
        } else if (event instanceof PersonLeavesVehicleEvent leavesVehicleEvent) {
            handler.handlePersonLeavesVehicle(leavesVehicleEvent);
        } else if (event instanceof GenericEvent genericEvent) {
            handler.handleGeneric(genericEvent);
        } else {
            handler.handle(event);
        }
    }

    private static XMLInputFactory createSafeInputFactory() {
        XMLInputFactory inputFactory = XMLInputFactory.newFactory();
        requireProperty(inputFactory, XMLInputFactory.SUPPORT_DTD, false);
        requireProperty(inputFactory, "javax.xml.stream.isSupportingExternalEntities", false);
        setPropertyIfSupported(inputFactory, XMLConstants.ACCESS_EXTERNAL_DTD, "");
        setPropertyIfSupported(inputFactory, XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return inputFactory;
    }

    private static void requireProperty(XMLInputFactory inputFactory, String propertyName, Object value) {
        try {
            inputFactory.setProperty(propertyName, value);
        } catch (IllegalArgumentException exception) {
            throw new MatsimModelException("Could not apply required XML parser security property: " + propertyName, exception);
        }
    }

    private static void setPropertyIfSupported(XMLInputFactory inputFactory, String propertyName, Object value) {
        try {
            inputFactory.setProperty(propertyName, value);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private static void closeQuietly(XMLStreamReader reader) {
        if (reader != null) {
            try {
                reader.close();
            } catch (XMLStreamException ignored) {
            }
        }
    }

    private static void closeQuietly(InputStream inputStream) {
        try {
            inputStream.close();
        } catch (IOException ignored) {
        }
    }

    private static final class EventSpliterator extends Spliterators.AbstractSpliterator<MatsimEvent> {
        private final XMLStreamReader reader;
        private final InputStream inputStream;
        private boolean closed;

        EventSpliterator(XMLStreamReader reader, InputStream inputStream) {
            super(Long.MAX_VALUE, ORDERED | NONNULL);
            this.reader = reader;
            this.inputStream = inputStream;
        }

        @Override
        public boolean tryAdvance(Consumer<? super MatsimEvent> action) {
            if (closed) {
                return false;
            }
            try {
                while (reader.hasNext()) {
                    int token = reader.next();
                    if (token == XMLStreamConstants.START_ELEMENT && "event".equals(reader.getLocalName())) {
                        action.accept(parseEvent(reader));
                        return true;
                    }
                }
                close();
                return false;
            } catch (XMLStreamException exception) {
                close();
                throw new MatsimParseException("Could not parse events XML stream", exception);
            }
        }

        private void close() {
            if (!closed) {
                closed = true;
                closeQuietly(reader);
                closeQuietly(inputStream);
            }
        }
    }
}
