package com.example.issuetracker.shared;

/**
 * Strongly-typed identifier for an Issue.
 */
public record IssueId(String value) {

    public IssueId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("IssueId cannot be null or blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
