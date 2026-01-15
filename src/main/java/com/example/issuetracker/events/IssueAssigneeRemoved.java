package com.example.issuetracker.events;

import org.axonframework.eventsourcing.annotation.EventTag;

public record IssueAssigneeRemoved(
        @EventTag String issueId,
        String previousAssigneeId
) {
}
