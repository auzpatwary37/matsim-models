package com.citymodeler.matsim.models.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ValidationReport {
    private final List<ValidationIssue> issues;

    public ValidationReport() {
        this.issues = new ArrayList<>();
    }

    public ValidationReport(List<ValidationIssue> issues) {
        this.issues = new ArrayList<>(issues);
    }

    public void addIssue(ValidationIssue issue) {
        issues.add(issue);
    }

    public void addIssues(Iterable<ValidationIssue> newIssues) {
        for (ValidationIssue issue : newIssues) {
            issues.add(issue);
        }
    }

    public List<ValidationIssue> getIssues() {
        return Collections.unmodifiableList(issues);
    }

    public List<ValidationIssue> getErrors() {
        return issues.stream()
                .filter(ValidationIssue::isError)
                .toList();
    }

    public List<ValidationIssue> getWarnings() {
        return issues.stream()
                .filter(ValidationIssue::isWarning)
                .toList();
    }

    public List<ValidationIssue> getInfo() {
        return issues.stream()
                .filter(ValidationIssue::isInfo)
                .toList();
    }

    public boolean hasErrors() {
        return issues.stream().anyMatch(ValidationIssue::isError);
    }

    public boolean hasWarnings() {
        return issues.stream().anyMatch(ValidationIssue::isWarning);
    }

    public int size() {
        return issues.size();
    }

    public boolean isEmpty() {
        return issues.isEmpty();
    }

    @Override
    public String toString() {
        return "ValidationReport{" +
                "errors=" + getErrors().size() +
                ", warnings=" + getWarnings().size() +
                ", info=" + getInfo().size() +
                ", total=" + issues.size() +
                '}';
    }
}