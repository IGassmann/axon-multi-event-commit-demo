package com.example.issuetracker.events;

import com.example.issuetracker.shared.IssueId;
import com.example.issuetracker.shared.IssueTags;
import org.axonframework.eventsourcing.annotation.EventTag;

public record IssueCreated(
        @EventTag(key = IssueTags.ISSUE_ID)
        IssueId issueId,
        String title
) {
}
