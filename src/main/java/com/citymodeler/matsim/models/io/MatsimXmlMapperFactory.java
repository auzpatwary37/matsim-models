package com.citymodeler.matsim.models.io;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;

import com.citymodeler.matsim.models.api.Attributes;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.dataformat.xml.XmlFactory;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

public final class MatsimXmlMapperFactory {
    private MatsimXmlMapperFactory() {
    }

    public static XmlMapper createXmlMapper() {
        XmlFactory xmlFactory = new XmlFactory(createSafeInputFactory(), XMLOutputFactory.newFactory());
        XmlMapper mapper = new XmlMapper(xmlFactory);
        mapper.registerModule(new JavaTimeModule());

        SimpleModule attributesModule = new SimpleModule();
        attributesModule.addSerializer(Attributes.class, new AttributesSerializer());
        attributesModule.addDeserializer(Attributes.class, new AttributesDeserializer());
        mapper.registerModule(attributesModule);
        return mapper;
    }

    private static XMLInputFactory createSafeInputFactory() {
        XMLInputFactory inputFactory = XMLInputFactory.newFactory();
        requireProperty(inputFactory, XMLInputFactory.SUPPORT_DTD, false);
        requireProperty(inputFactory, "javax.xml.stream.isSupportingExternalEntities", false);
        // ACCESS_EXTERNAL_* are JAXP properties and are not recognized by Woodstox's StAX factory.
        // Apply them when supported; DTD and external-entity controls above remain fail-closed.
        setPropertyIfSupported(inputFactory, XMLConstants.ACCESS_EXTERNAL_DTD, "");
        setPropertyIfSupported(inputFactory, XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return inputFactory;
    }

    private static void requireProperty(XMLInputFactory inputFactory, String propertyName, Object value) {
        try {
            inputFactory.setProperty(propertyName, value);
        } catch (IllegalArgumentException exception) {
            throw new MatsimModelException("Could not apply required XML mapper security property: " + propertyName, exception);
        }
    }

    private static void setPropertyIfSupported(XMLInputFactory inputFactory, String propertyName, Object value) {
        try {
            inputFactory.setProperty(propertyName, value);
        } catch (IllegalArgumentException ignored) {
            // StAX implementations differ on JAXP access properties; unsupported properties cannot be applied here.
        }
    }
}
