package com.citymodeler.matsim.models.population;

import com.citymodeler.matsim.models.api.Id;
import com.citymodeler.matsim.models.transit.Departure;
import com.citymodeler.matsim.models.transit.TransitLine;
import com.citymodeler.matsim.models.transit.TransitRoute;
import com.citymodeler.matsim.models.transit.TransitStopFacility;

public final class TransitPassengerRoute implements Route {
    private Id<TransitStopFacility> accessStopId;
    private Id<TransitStopFacility> egressStopId;
    private Id<TransitLine> lineId;
    private Id<TransitRoute> routeId;
    private Id<Departure> departureId;

    public Id<TransitStopFacility> getAccessStopId() {
        return accessStopId;
    }

    public void setAccessStopId(Id<TransitStopFacility> accessStopId) {
        this.accessStopId = accessStopId;
    }

    public Id<TransitStopFacility> getEgressStopId() {
        return egressStopId;
    }

    public void setEgressStopId(Id<TransitStopFacility> egressStopId) {
        this.egressStopId = egressStopId;
    }

    public Id<TransitLine> getLineId() {
        return lineId;
    }

    public void setLineId(Id<TransitLine> lineId) {
        this.lineId = lineId;
    }

    public Id<TransitRoute> getRouteId() {
        return routeId;
    }

    public void setRouteId(Id<TransitRoute> routeId) {
        this.routeId = routeId;
    }

    public Id<Departure> getDepartureId() {
        return departureId;
    }

    public void setDepartureId(Id<Departure> departureId) {
        this.departureId = departureId;
    }
}
