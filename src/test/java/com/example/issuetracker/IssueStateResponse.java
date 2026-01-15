package com.example.issuetracker;

import com.example.issuetracker.shared.Status;

/**
 * Response containing the current state of a FailingIssue entity.
 * Used in tests to verify rollback behavior.
 */
public record IssueStateResponse(
        String assigneeId,
        Status status
) {
}
