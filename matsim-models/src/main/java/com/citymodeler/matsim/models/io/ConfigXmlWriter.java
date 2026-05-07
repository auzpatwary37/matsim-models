package com.citymodeler.matsim.models.io;

import java.io.OutputStream;
import java.nio.file.Path;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.citymodeler.matsim.models.config.Config;
import com.citymodeler.matsim.models.config.ConfigGroup;

public final class ConfigXmlWriter {
    public void write(Config config, Path path) {
        XmlSupport.write(document(config), path);
    }

    public void write(Config config, OutputStream outputStream) {
        XmlSupport.write(document(config), outputStream);
    }

    public String writeToString(Config config) {
        return XmlSupport.writeToString(document(config));
    }

    private Document document(Config config) {
        Document document = XmlSupport.newDocument();
        Element root = document.createElement("config");
        document.appendChild(root);

        for (ConfigGroup module : config.getModules().values()) {
            Element moduleElement = document.createElement("module");
            moduleElement.setAttribute("name", module.getName());
            root.appendChild(moduleElement);

            for (var entry : module.getParams().entrySet()) {
                Element paramElement = document.createElement("param");
                paramElement.setAttribute("name", entry.getKey());
                paramElement.setAttribute("value", entry.getValue());
                moduleElement.appendChild(paramElement);
            }

            for (var entry : module.getParamSets().entrySet()) {
                String setType = entry.getKey();
                for (var params : entry.getValue()) {
                    Element paramSetElement = document.createElement("parameterset");
                    paramSetElement.setAttribute("type", setType);
                    moduleElement.appendChild(paramSetElement);

                    for (var paramEntry : params.entrySet()) {
                        Element paramElement = document.createElement("param");
                        paramElement.setAttribute("name", paramEntry.getKey());
                        paramElement.setAttribute("value", paramEntry.getValue());
                        paramSetElement.appendChild(paramElement);
                    }
                }
            }

            for (ConfigGroup subModule : module.getSubModules().values()) {
                appendSubModule(document, moduleElement, subModule);
            }
        }
        return document;
    }

    private void appendSubModule(Document document, Element parent, ConfigGroup subModule) {
        Element subModuleElement = document.createElement("module");
        subModuleElement.setAttribute("name", subModule.getName());
        parent.appendChild(subModuleElement);

        for (var entry : subModule.getParams().entrySet()) {
            Element paramElement = document.createElement("param");
            paramElement.setAttribute("name", entry.getKey());
            paramElement.setAttribute("value", entry.getValue());
            subModuleElement.appendChild(paramElement);
        }

        for (var entry : subModule.getParamSets().entrySet()) {
            String setType = entry.getKey();
            for (var params : entry.getValue()) {
                Element paramSetElement = document.createElement("parameterset");
                paramSetElement.setAttribute("type", setType);
                subModuleElement.appendChild(paramSetElement);

                for (var paramEntry : params.entrySet()) {
                    Element paramElement = document.createElement("param");
                    paramElement.setAttribute("name", paramEntry.getKey());
                    paramElement.setAttribute("value", paramEntry.getValue());
                    paramSetElement.appendChild(paramElement);
                }
            }
        }

        for (ConfigGroup nestedSubModule : subModule.getSubModules().values()) {
            appendSubModule(document, subModuleElement, nestedSubModule);
        }
    }
}