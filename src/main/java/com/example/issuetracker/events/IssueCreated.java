package com.example.issuetracker.events;

import com.example.issuetracker.shared.IssueId;
import org.axonframework.eventsourcing.annotation.EventTag;

public record IssueCreated(
        @EventTag IssueId issueId,
        String title
) {
}
