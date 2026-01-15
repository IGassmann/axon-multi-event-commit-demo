package com.example.issuetracker.commands;

import org.axonframework.modelling.annotation.TargetEntityId;

public record UnassignIssue(
        @TargetEntityId
        String issueId
) {
}
