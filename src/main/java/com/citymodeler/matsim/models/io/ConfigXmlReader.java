package com.citymodeler.matsim.models.io;

import java.io.InputStream;
import java.nio.file.Path;

import org.w3c.dom.Element;

import com.citymodeler.matsim.models.config.Config;
import com.citymodeler.matsim.models.config.ConfigGroup;

public final class ConfigXmlReader {
    private static final String SCHEMA = "/schemas/config.xsd";
    private final boolean validateSchema;

    public ConfigXmlReader() {
        this(false);
    }

    public ConfigXmlReader(boolean validateSchema) {
        this.validateSchema = validateSchema;
    }

    public Config read(Path path) {
        return read((validateSchema ? XmlSupport.parse(path, SCHEMA) : XmlSupport.parse(path)).getDocumentElement());
    }

    public Config read(InputStream inputStream) {
        return read((validateSchema ? XmlSupport.parse(inputStream, SCHEMA) : XmlSupport.parse(inputStream)).getDocumentElement());
    }

    public Config readString(String xml) {
        return read((validateSchema ? XmlSupport.parse(xml, SCHEMA) : XmlSupport.parse(xml)).getDocumentElement());
    }

    private Config read(Element root) {
        Config config = new Config();
        XmlSupport.readAttributes(root, config.getAttributes());
        for (Element moduleElement : XmlSupport.children(root, "module")) {
            ConfigGroup module = readModule(moduleElement);
            config.addModule(module);
        }
        return config;
    }

    private ConfigGroup readModule(Element moduleElement) {
        ConfigGroup module = new ConfigGroup(XmlSupport.attr(moduleElement, "name"));

        for (Element paramElement : XmlSupport.children(moduleElement, "param")) {
            String name = XmlSupport.attr(paramElement, "name");
            String value = XmlSupport.attr(paramElement, "value");
            if (name != null && value != null) {
                module.addParam(name, value);
            }
        }

        for (Element paramSetElement : XmlSupport.children(moduleElement, "parameterset")) {
            String type = XmlSupport.attr(paramSetElement, "type");
            java.util.Map<String, String> params = new java.util.LinkedHashMap<>();
            for (Element paramElement : XmlSupport.children(paramSetElement, "param")) {
                String name = XmlSupport.attr(paramElement, "name");
                String value = XmlSupport.attr(paramElement, "value");
                if (name != null && value != null) {
                    params.put(name, value);
                }
            }
            module.addParamSet(type, params);
        }

        for (Element subModuleElement : XmlSupport.children(moduleElement, "module")) {
            ConfigGroup subModule = readModule(subModuleElement);
            module.addSubModule(subModule);
        }

        return module;
    }
}
