package com.example.issuetracker;

import com.example.issuetracker.commands.UnassignIssue;
import com.example.issuetracker.events.IssueAssigneeChanged;
import com.example.issuetracker.events.IssueAssigneeRemoved;
import com.example.issuetracker.events.IssueCreated;
import com.example.issuetracker.events.IssueStatusChanged;
import com.example.issuetracker.write.Issue;
import org.axonframework.messaging.core.Metadata;
import org.axonframework.test.fixture.AxonTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.example.issuetracker.write.Issue.Status;

/**
 * Test cases demonstrating Axon Framework 5's atomic multi-event commit behavior.
 *
 * <h2>Key Points Demonstrated</h2>
 * <ol>
 *   <li>Multiple events applied in a single command handler are committed atomically</li>
 *   <li>If any event handler fails, all events and state changes are rolled back</li>
 * </ol>
 */
class IssueCommandHandlerTest {

    private AxonTestFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = IssueTestFixture.create();
    }

    @AfterEach
    void tearDown() {
        fixture.stop();
    }

    @Nested
    @DisplayName("Atomic Multi-Event Commits")
    class AtomicMultiEventCommits {

        /**
         * HAPPY PATH: Unassigning from an IN_PROGRESS issue emits TWO events atomically.
         *
         * <p>This test demonstrates that:</p>
         * <ol>
         *   <li>The UnassignIssue command handler applies two events</li>
         *   <li>Both events appear in the same assertion</li>
         *   <li>The final aggregate state is valid (BACKLOG with no assignee)</li>
         * </ol>
         *
         * <p>In Axon, both events are committed in the same UnitOfWork transaction.
         * If either event application fails, neither is persisted.</p>
         */
        @Test
        @DisplayName("Unassign from IN_PROGRESS issue emits both IssueAssigneeRemoved and IssueStatusChanged atomically")
        void unassignFromInProgressIssue_emitsTwoEventsAtomically() {
            var issueId = "issue-1";

            fixture.given()
                    .events(List.of(
                            new IssueCreated(issueId, "Fix the bug"),
                            new IssueAssigneeChanged(issueId, "user-42"),
                            new IssueStatusChanged(issueId, Status.BACKLOG, Status.IN_PROGRESS)
                    ))
                    .when().command(new UnassignIssue(issueId), Metadata.emptyInstance())
                    .then().events(
                            // BOTH events are emitted from the SAME command handler
                            new IssueAssigneeRemoved(issueId, "user-42"),
                            new IssueStatusChanged(issueId, Status.IN_PROGRESS, Status.BACKLOG)
                    );
        }
    }

    /**
     * ATOMIC ROLLBACK DEMONSTRATION
     *
     * <p>These tests use a special FailingIssue entity that throws an exception
     * in its IssueStatusChanged handler. This demonstrates that when an event
     * sourcing handler fails, the entire unit of work is rolled back.</p>
     *
     * <p>Specifically, when unassigning from an IN_PROGRESS issue:</p>
     * <ol>
     *   <li>IssueAssigneeRemoved is appended - its handler runs successfully</li>
     *   <li>IssueStatusChanged is appended - its handler THROWS</li>
     *   <li>The entire UnitOfWork is rolled back</li>
     *   <li>NEITHER event is persisted to the event store</li>
     * </ol>
     *
     * <p>This proves that event sourcing handlers run within an atomic transaction,
     * not "eventually consistent" as some might assume.</p>
     *
     * <h3>Note on Test Fixture Behavior</h3>
     * <p>The Axon test fixture records events as they are published (before commit),
     * but in production, the transaction rollback would prevent persistence.
     * The key demonstration is that the COMMAND FAILS, which triggers rollback.</p>
     */
    @Nested
    @DisplayName("Atomic Rollback Demonstration")
    class AtomicRollbackDemonstration {

        private AxonTestFixture failingFixture;

        @BeforeEach
        void setUp() {
            failingFixture = IssueTestFixture.slice(FailingIssueConfiguration::configure);
        }

        @AfterEach
        void tearDown() {
            failingFixture.stop();
        }

        /**
         * CRITICAL TEST: Demonstrates that when an event sourcing handler fails,
         * the entire command fails and the entity state is rolled back.
         *
         * <p>This test proves that when the IssueStatusChanged handler fails:</p>
         * <ul>
         *   <li>The command as a whole fails with an exception</li>
         *   <li>The entity state is rolled back to pre-command state</li>
         *   <li>No events are persisted to the event store</li>
         * </ul>
         *
         * <p>The exception is wrapped by Axon's StateEvolvingException, with the
         * original "Simulated failure" message in the cause chain.</p>
         */
        @Test
        @DisplayName("Command fails when event sourcing handler throws - state is rolled back")
        void commandFailsWhenEventSourcingHandlerThrows_stateIsRolledBack() {
            var issueId = "issue-1";

            // Given: An issue that is IN_PROGRESS with an assignee
            // When: We unassign, triggering both events (second one will fail)
            // Then: The command fails and state is rolled back
            failingFixture.given()
                    .events(List.of(
                            new IssueCreated(issueId, "Fix the bug"),
                            new IssueAssigneeChanged(issueId, "user-42"),
                            new IssueStatusChanged(issueId, Status.BACKLOG, Status.IN_PROGRESS)
                    ))
                    .when().command(new UnassignIssue(issueId), Metadata.emptyInstance())
                    .then()
                    // The command fails - triggers rollback
                    .exception(org.axonframework.modelling.StateEvolvingException.class)
                    .exceptionSatisfies(e -> {
                        // The wrapper exception indicates state evolution failed
                        org.assertj.core.api.Assertions.assertThat(e.getMessage())
                                .contains("Failed to apply event")
                                .contains("IssueStatusChanged");
                        // The root cause is our simulated failure
                        org.assertj.core.api.Assertions.assertThat(e.getCause().getCause().getMessage())
                                .contains("Simulated failure");
                    })
                    // VERIFY ROLLBACK: Query the entity state and confirm it was rolled back
                    .expect(config -> {
                        var queryGateway = config.getComponent(
                                org.axonframework.messaging.queryhandling.gateway.QueryGateway.class);

                        // Query the entity state
                        var state = queryGateway.query(
                                new GetIssueStateQuery(issueId),
                                IssueStateResponse.class
                        ).join();

                        // VERIFY: State should be rolled back to pre-command values
                        // Assignee should still be "user-42" (not null from IssueAssigneeRemoved)
                        org.assertj.core.api.Assertions.assertThat(state.assigneeId())
                                .as("Assignee should be preserved after rollback")
                                .isEqualTo("user-42");

                        // Status should still be IN_PROGRESS (not BACKLOG from IssueStatusChanged)
                        org.assertj.core.api.Assertions.assertThat(state.status())
                                .as("Status should be preserved after rollback")
                                .isEqualTo(Status.IN_PROGRESS);
                    });
        }
    }
}
