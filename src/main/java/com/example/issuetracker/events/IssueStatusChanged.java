package com.example.issuetracker.events;

import com.example.issuetracker.shared.IssueId;
import com.example.issuetracker.shared.IssueTags;
import com.example.issuetracker.shared.Status;
import org.axonframework.eventsourcing.annotation.EventTag;

public record IssueStatusChanged(
        @EventTag(key = IssueTags.ISSUE_ID)
        IssueId issueId,
        Status oldStatus,
        Status newStatus
) {
}
