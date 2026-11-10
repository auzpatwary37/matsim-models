package com.citymodeler.matsim.models.validation;

import java.util.Objects;
import java.util.Optional;

/**
 * Structured entity identity attached to a {@link ValidationIssue} so
 * consumers can resolve ownership without parsing the human-readable message.
 *
 * @param entityType     domain token such as {@code "lane"} or {@code "link"}
 * @param entityId       entity id string
 * @param parentLinkId   owning link id for scoped entities (lane references);
 *                       empty for unscoped entities
 */
public record IssueEntity(String entityType, String entityId, Optional<String> parentLinkId) {

    public IssueEntity {
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(parentLinkId, "parentLinkId");
    }

    public static IssueEntity of(String entityType, String entityId) {
        return new IssueEntity(entityType, entityId, Optional.empty());
    }

    public static IssueEntity lane(String laneId, String parentLinkId) {
        return new IssueEntity("lane", laneId, Optional.ofNullable(parentLinkId));
    }

    public static IssueEntity link(String linkId) {
        return of("link", linkId);
    }
}
