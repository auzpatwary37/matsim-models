package com.citymodeler.matsim.models.api;

public record Tuple<A, B>(A first, B second) {
    public A getFirst() {
        return first;
    }

    public B getSecond() {
        return second;
    }
}
