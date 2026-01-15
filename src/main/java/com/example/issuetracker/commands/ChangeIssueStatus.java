package com.example.issuetracker.commands;

import com.example.issuetracker.shared.IssueId;
import com.example.issuetracker.shared.Status;
import org.axonframework.modelling.annotation.TargetEntityId;

public record ChangeIssueStatus(
        @TargetEntityId
        IssueId issueId,
        Status newStatus
) {
}
