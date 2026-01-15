package com.example.issuetracker.events;

import com.example.issuetracker.shared.IssueId;
import org.axonframework.eventsourcing.annotation.EventTag;

public record IssueAssigneeChanged(
        @EventTag IssueId issueId,
        String newAssigneeId
) {
}
