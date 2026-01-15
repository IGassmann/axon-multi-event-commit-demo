package com.example.issuetracker;

import com.example.issuetracker.shared.IssueId;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.messaging.queryhandling.configuration.QueryHandlingModule;

/**
 * Configuration for the FailingIssue entity used in atomic rollback tests.
 *
 * <p>Uses entity-centric command handlers where {@code @CommandHandler} methods
 * are defined directly in the {@link FailingIssue} entity class.</p>
 */
public class FailingIssueConfiguration {

    /**
     * Configures the FailingIssue entity and query handler.
     *
     * <p>No separate CommandHandlingModule is needed because the FailingIssue entity
     * contains its own {@code @CommandHandler} methods (entity-centric pattern).</p>
     */
    public static EventSourcingConfigurer configure(EventSourcingConfigurer configurer) {
        var failingIssueEntity = EventSourcedEntityModule
                .autodetected(IssueId.class, FailingIssue.class);

        var queryHandlingModule = QueryHandlingModule
                .named("FailingIssueQueries")
                .queryHandlers()
                .annotatedQueryHandlingComponent(c -> new FailingIssueQueryHandler());

        return configurer
                .registerEntity(failingIssueEntity)
                .registerQueryHandlingModule(queryHandlingModule);
    }

    private FailingIssueConfiguration() {
    }
}
