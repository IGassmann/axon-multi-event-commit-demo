package com.example.issuetracker.events;

import com.example.issuetracker.shared.IssueId;
import org.axonframework.eventsourcing.annotation.EventTag;

public record IssueAssigneeRemoved(
        @EventTag IssueId issueId,
        String previousAssigneeId
) {
}
