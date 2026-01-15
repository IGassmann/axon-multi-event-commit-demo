package com.example.issuetracker;

import com.example.issuetracker.write.Issue.Status;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.messaging.queryhandling.annotation.QueryHandler;
import org.axonframework.messaging.queryhandling.configuration.QueryHandlingModule;
import org.axonframework.modelling.annotation.InjectEntity;

/**
 * Configuration for the FailingIssue entity used in atomic rollback tests.
 */
public class FailingIssueConfiguration {

    /** Query to fetch entity state for rollback verification. */
    public record GetIssueStateQuery(String issueId) {}

    /** Response containing entity state for rollback verification. */
    public record IssueStateResponse(String assigneeId, Status status) {}

    public static EventSourcingConfigurer configure(EventSourcingConfigurer configurer) {
        var failingIssueEntity = EventSourcedEntityModule
                .autodetected(String.class, FailingIssue.class);

        var queryHandlingModule = QueryHandlingModule
                .named("FailingIssueQueries")
                .queryHandlers()
                .annotatedQueryHandlingComponent(c -> new Object() {
                    @QueryHandler
                    public IssueStateResponse handle(GetIssueStateQuery query,
                                                     @InjectEntity(idProperty = "issueId") FailingIssue issue) {
                        return new IssueStateResponse(issue.getAssigneeId(), issue.getStatus());
                    }
                });

        return configurer
                .registerEntity(failingIssueEntity)
                .registerQueryHandlingModule(queryHandlingModule);
    }

    private FailingIssueConfiguration() {
    }
}
