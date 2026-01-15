package com.example.issuetracker;

import com.example.issuetracker.write.IssueConfiguration;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.test.fixture.AxonTestFixture;

import java.util.function.UnaryOperator;

/**
 * Factory for creating Axon test fixtures for Issue tests.
 */
public class IssueTestFixture {

    /**
     * Creates a test fixture with the standard Issue configuration.
     */
    public static AxonTestFixture create() {
        return slice(IssueConfiguration::configure);
    }

    /**
     * Creates a test fixture with custom configuration.
     */
    public static AxonTestFixture slice(UnaryOperator<EventSourcingConfigurer> customization) {
        var configurer = EventSourcingConfigurer.create();
        configurer = customization.apply(configurer);
        return AxonTestFixture.with(configurer, c -> c.disableAxonServer());
    }

    private IssueTestFixture() {
    }
}
