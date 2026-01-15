package com.example.issuetracker.shared;

/**
 * Tag keys for event tagging in the Issue domain.
 * These tags define the consistency boundaries for event sourcing.
 */
public final class IssueTags {

    /**
     * Tag key for the issue identifier.
     * All events related to an issue are tagged with this key.
     */
    public static final String ISSUE_ID = "issueId";

    private IssueTags() {
    }
}
