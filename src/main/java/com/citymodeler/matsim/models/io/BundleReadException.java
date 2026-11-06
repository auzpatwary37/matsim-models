package com.citymodeler.matsim.models.io;

import java.util.List;

import com.citymodeler.matsim.models.validation.ValidationIssue;

/**
 * Whole-document failure of a bundle read: unreadable file, malformed XML, or
 * an unconstructible network. Recognizable-but-invalid lane content in
 * well-formed XML does NOT throw; it returns as lane-result ERROR issues.
 */
public class BundleReadException extends MatsimParseException {
    private final List<ValidationIssue> issues;

    public BundleReadException(String message, Throwable cause,
            List<ValidationIssue> issues) {
        super(message, cause);
        this.issues = List.copyOf(issues);
    }

    public List<ValidationIssue> getIssues() {
        return issues;
    }
}
