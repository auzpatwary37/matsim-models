package com.citymodeler.matsim.models.io;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

import org.w3c.dom.Element;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.households.Household;
import com.citymodeler.matsim.models.households.Households;
import com.citymodeler.matsim.models.households.Person;

public final class HouseholdsXmlReader {
    public Households read(Path path) {
        return read(XmlSupport.parse(path).getDocumentElement());
    }

    public Households read(InputStream inputStream) {
        return read(XmlSupport.parse(inputStream).getDocumentElement());
    }

    public Households read(String xml) {
        return read(XmlSupport.parse(xml).getDocumentElement());
    }

    private Households read(Element root) {
        Households households = new Households();
        for (Element householdElement : XmlSupport.children(root, "household")) {
            Household household = new Household(
                    Id.create(XmlSupport.attr(householdElement, "id"), Household.class),
                    XmlSupport.attr(householdElement, "income"));

            Element membersElement = XmlSupport.child(householdElement, "members");
            if (membersElement != null) {
                for (Element personIdElement : XmlSupport.children(membersElement, "personId")) {
                    household.getMemberIds().add(
                            Id.create(XmlSupport.attr(personIdElement, "ref"), Person.class));
                }
            }

            XmlSupport.readAttributes(householdElement, household.getAttributes());
            households.addHousehold(household);
        }
        return households;
    }
}