package com.citymodeler.matsim.models.osm.network;

/** Classifies the turn from an incoming link to an outgoing link. */
@FunctionalInterface
public interface MovementTurnClassifier {
    LaneTurnClass classify(String inLinkId, String outLinkId);
}
