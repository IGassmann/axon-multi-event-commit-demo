package com.example.issuetracker;

import com.example.issuetracker.shared.IssueId;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.messaging.commandhandling.configuration.CommandHandlingModule;
import org.axonframework.messaging.queryhandling.configuration.QueryHandlingModule;

/**
 * Configuration for the FailingIssue entity used in atomic rollback tests.
 */
public class FailingIssueConfiguration {

    /**
     * Configures the FailingIssue entity, command handler, and query handler.
     */
    public static EventSourcingConfigurer configure(EventSourcingConfigurer configurer) {
        var failingIssueEntity = EventSourcedEntityModule
                .autodetected(IssueId.class, FailingIssue.class);

        var commandHandlingModule = CommandHandlingModule
                .named("FailingIssueCommands")
                .commandHandlers()
                .annotatedCommandHandlingComponent(c -> new FailingIssueCommandHandler());

        var queryHandlingModule = QueryHandlingModule
                .named("FailingIssueQueries")
                .queryHandlers()
                .annotatedQueryHandlingComponent(c -> new FailingIssueQueryHandler());

        return configurer
                .registerEntity(failingIssueEntity)
                .registerCommandHandlingModule(commandHandlingModule)
                .registerQueryHandlingModule(queryHandlingModule);
    }

    private FailingIssueConfiguration() {
    }
}
