package com.citymodeler.matsim.models.io;

import java.io.OutputStream;
import java.nio.file.Path;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.citymodeler.matsim.models.households.Household;
import com.citymodeler.matsim.models.households.Households;

public final class HouseholdsXmlWriter {
    public void write(Households households, Path path) {
        XmlSupport.write(document(households), path);
    }

    public void write(Households households, OutputStream outputStream) {
        XmlSupport.write(document(households), outputStream);
    }

    public String writeToString(Households households) {
        return XmlSupport.writeToString(document(households));
    }

    private Document document(Households households) {
        Document document = XmlSupport.newDocument();
        Element root = document.createElement("households");
        document.appendChild(root);

        for (Household household : households.getHouseholds().values()) {
            Element householdElement = document.createElement("household");
            householdElement.setAttribute("id", household.getId().toString());
            householdElement.setAttribute("income", household.getIncome());

            if (!household.getMemberIds().isEmpty()) {
                Element membersElement = document.createElement("members");
                for (var memberId : household.getMemberIds()) {
                    Element personIdElement = document.createElement("personId");
                    personIdElement.setAttribute("ref", memberId.toString());
                    membersElement.appendChild(personIdElement);
                }
                householdElement.appendChild(membersElement);
            }

            XmlSupport.appendAttributes(document, householdElement, household.getAttributes());
            root.appendChild(householdElement);
        }

        return document;
    }
}