package com.example.issuetracker;

import com.example.issuetracker.write.Issue;

/**
 * Response containing the current state of a FailingIssue entity.
 * Used in tests to verify rollback behavior.
 */
public record IssueStateResponse(
        String assigneeId,
        Issue.Status status
) {
}
