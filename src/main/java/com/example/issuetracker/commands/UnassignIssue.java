package com.example.issuetracker.commands;

import com.example.issuetracker.shared.IssueId;
import org.axonframework.modelling.annotation.TargetEntityId;

public record UnassignIssue(
        @TargetEntityId
        IssueId issueId
) {
}
