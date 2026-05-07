package com.citymodeler.matsim.models.api;

/**
 * Immutable pair of two values.
 *
 * @param first the first value
 * @param second the second value
 * @param <A> first value type
 * @param <B> second value type
 */
public record Tuple<A, B>(A first, B second) {
}
