package com.example.issuetracker;

import org.axonframework.messaging.queryhandling.annotation.QueryHandler;
import org.axonframework.modelling.annotation.InjectEntity;

/**
 * Query handler to retrieve FailingIssue state.
 * Used in tests to verify rollback behavior.
 */
public class FailingIssueQueryHandler {

    @QueryHandler
    public IssueStateResponse handle(GetIssueStateQuery query,
                                     @InjectEntity(idProperty = "issueId") FailingIssue issue) {
        return new IssueStateResponse(
                issue.getAssigneeId(),
                issue.getStatus()
        );
    }
}
