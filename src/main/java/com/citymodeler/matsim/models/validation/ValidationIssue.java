package com.citymodeler.matsim.models.validation;

import java.util.Objects;
import java.util.Optional;

public final class ValidationIssue {
    private final ValidationSeverity severity;
    private final String domain;
    private final String code;
    private final String message;
    private final String objectId;
    private final String suggestedFix;
    private final SourceLocation sourceLocation;
    private final IssueEntity owner;
    private final IssueEntity referencedEntity;

    public ValidationIssue(ValidationSeverity severity, String domain, String code,
                           String message, String objectId, String suggestedFix) {
        this(severity, domain, code, message, objectId, suggestedFix, null, null, null);
    }

    public ValidationIssue(ValidationSeverity severity, String domain, String code,
                           String message, String objectId, String suggestedFix,
                           SourceLocation sourceLocation, IssueEntity owner,
                           IssueEntity referencedEntity) {
        this.severity = Objects.requireNonNull(severity);
        this.domain = Objects.requireNonNull(domain);
        this.code = Objects.requireNonNull(code);
        this.message = Objects.requireNonNull(message);
        this.objectId = objectId;
        this.suggestedFix = suggestedFix;
        this.sourceLocation = sourceLocation;
        this.owner = owner;
        this.referencedEntity = referencedEntity;
    }

    public Optional<SourceLocation> getSourceLocation() {
        return Optional.ofNullable(sourceLocation);
    }

    public Optional<IssueEntity> getOwner() {
        return Optional.ofNullable(owner);
    }

    public Optional<IssueEntity> getReferencedEntity() {
        return Optional.ofNullable(referencedEntity);
    }

    public ValidationSeverity getSeverity() {
        return severity;
    }

    public String getDomain() {
        return domain;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getObjectId() {
        return objectId;
    }

    public String getSuggestedFix() {
        return suggestedFix;
    }

    public boolean isError() {
        return severity == ValidationSeverity.ERROR;
    }

    public boolean isWarning() {
        return severity == ValidationSeverity.WARNING;
    }

    public boolean isInfo() {
        return severity == ValidationSeverity.INFO;
    }

    @Override
    public String toString() {
        return severity + " [" + domain + "/" + code + "] " + message +
                (objectId != null ? " (object: " + objectId + ")" : "") +
                (suggestedFix != null ? " Fix: " + suggestedFix : "");
    }
}