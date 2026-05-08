package com.citymodeler.matsim.models.transit;

import java.util.Objects;

import com.citymodeler.matsim.models.api.Id;

public final class TransitRouteStop {
    private Id<TransitStopFacility> stopFacilityId;
    private TransitStopFacility stopFacility;
    private double arrivalOffset;
    private double departureOffset;
    private boolean awaitDeparture;

    public TransitRouteStop(
            Id<TransitStopFacility> stopFacilityId,
            double arrivalOffset,
            double departureOffset,
            boolean awaitDeparture) {
        this.stopFacilityId = Objects.requireNonNull(stopFacilityId, "stopFacilityId");
        this.arrivalOffset = arrivalOffset;
        this.departureOffset = departureOffset;
        this.awaitDeparture = awaitDeparture;
    }

    public Id<TransitStopFacility> getStopFacilityId() {
        return stopFacilityId;
    }

    public TransitStopFacility getStopFacility() {
        return stopFacility;
    }

    public void setStopFacility(TransitStopFacility stopFacility) {
        this.stopFacility = stopFacility;
    }

    public void clearStopFacility() {
        stopFacility = null;
    }

    public double getArrivalOffset() {
        return arrivalOffset;
    }

    public void setArrivalOffset(double arrivalOffset) {
        this.arrivalOffset = arrivalOffset;
    }

    public double getDepartureOffset() {
        return departureOffset;
    }

    public void setDepartureOffset(double departureOffset) {
        this.departureOffset = departureOffset;
    }

    public boolean isAwaitDeparture() {
        return awaitDeparture;
    }

    public void setAwaitDeparture(boolean awaitDeparture) {
        this.awaitDeparture = awaitDeparture;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TransitRouteStop that)) return false;
        return Double.compare(that.arrivalOffset, arrivalOffset) == 0 &&
               Double.compare(that.departureOffset, departureOffset) == 0 &&
               Objects.equals(stopFacilityId, that.stopFacilityId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stopFacilityId, arrivalOffset, departureOffset);
    }
}
