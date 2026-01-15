package com.example.issuetracker;

/**
 * Query to fetch the current state of a FailingIssue entity.
 * Used in tests to verify rollback behavior.
 */
public record GetIssueStateQuery(String issueId) {
}
