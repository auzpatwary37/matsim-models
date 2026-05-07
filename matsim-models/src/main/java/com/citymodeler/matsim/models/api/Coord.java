package com.citymodeler.matsim.models.api;

import java.util.Objects;

public final class Coord {
    private final double x;
    private final double y;

    public Coord(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof Coord coord)) {
            return false;
        }
        return Double.compare(x, coord.x) == 0 && Double.compare(y, coord.y) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    @Override
    public String toString() {
        return "Coord{" + "x=" + x + ", y=" + y + '}';
    }
}
