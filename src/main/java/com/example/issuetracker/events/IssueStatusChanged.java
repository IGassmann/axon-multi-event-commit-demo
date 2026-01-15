package com.example.issuetracker.events;

import com.example.issuetracker.write.Issue;
import org.axonframework.eventsourcing.annotation.EventTag;

public record IssueStatusChanged(
        @EventTag String issueId,
        Issue.Status oldStatus,
        Issue.Status newStatus
) {
}
