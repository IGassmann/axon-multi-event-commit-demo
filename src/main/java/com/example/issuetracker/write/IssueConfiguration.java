package com.example.issuetracker.write;

import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;

/**
 * Configuration for Issue entity in Axon Framework 5.
 *
 * <p>Uses entity-centric command handlers where {@code @CommandHandler} methods
 * are defined directly in the {@link Issue} entity class.</p>
 */
public class IssueConfiguration {

    /**
     * Configures the Issue entity with the EventSourcingConfigurer.
     *
     * <p>No separate CommandHandlingModule is needed because the Issue entity
     * contains its own {@code @CommandHandler} methods (entity-centric pattern).</p>
     */
    public static EventSourcingConfigurer configure(EventSourcingConfigurer configurer) {
        var issueEntity = EventSourcedEntityModule
                .autodetected(String.class, Issue.class);

        return configurer.registerEntity(issueEntity);
    }

    private IssueConfiguration() {
    }
}
