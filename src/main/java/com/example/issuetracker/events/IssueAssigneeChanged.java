package com.example.issuetracker.events;

import org.axonframework.eventsourcing.annotation.EventTag;

public record IssueAssigneeChanged(
        @EventTag String issueId,
        String newAssigneeId
) {
}
