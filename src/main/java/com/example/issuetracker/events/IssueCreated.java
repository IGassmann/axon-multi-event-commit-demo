package com.example.issuetracker.events;

import org.axonframework.eventsourcing.annotation.EventTag;

public record IssueCreated(
        @EventTag String issueId,
        String title
) {
}
