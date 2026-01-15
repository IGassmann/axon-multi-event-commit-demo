package com.example.issuetracker.write;

import com.example.issuetracker.shared.IssueId;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.messaging.commandhandling.configuration.CommandHandlingModule;

/**
 * Configuration for Issue command handling in Axon Framework 5.
 */
public class IssueConfiguration {

    /**
     * Configures the Issue entity and command handler with the EventSourcingConfigurer.
     */
    public static EventSourcingConfigurer configure(EventSourcingConfigurer configurer) {
        var issueEntity = EventSourcedEntityModule
                .autodetected(IssueId.class, Issue.class);

        var commandHandlingModule = CommandHandlingModule
                .named("IssueCommands")
                .commandHandlers()
                .annotatedCommandHandlingComponent(c -> new IssueCommandHandler());

        return configurer
                .registerEntity(issueEntity)
                .registerCommandHandlingModule(commandHandlingModule);
    }

    private IssueConfiguration() {
    }
}
