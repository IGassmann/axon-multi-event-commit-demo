package com.example.issuetracker;

import com.example.issuetracker.events.IssueStatusChanged;
import com.example.issuetracker.shared.Status;
import com.example.issuetracker.write.Issue;
import org.axonframework.eventsourcing.annotation.EventSourcedEntity;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator;

/**
 * A test-only Issue entity that throws an exception in the IssueStatusChanged handler.
 *
 * <p>This entity extends {@link Issue} and overrides only the {@link IssueStatusChanged}
 * handler to demonstrate atomic rollback behavior in Axon Framework 5.</p>
 *
 * <p>When unassigning from an IN_PROGRESS issue, two events are appended:
 * <ol>
 *   <li>{@code IssueAssigneeRemoved} - removes the assignee</li>
 *   <li>{@code IssueStatusChanged} - handler THROWS, causing entire unit of work to roll back</li>
 * </ol>
 *
 * <p>This proves that all events from a single command are committed atomically,
 * and a failure in any handler causes all events and state changes to be rolled back.</p>
 */
@EventSourcedEntity(tagKey = "issueId")
public class FailingIssue extends Issue {

    @EntityCreator
    public FailingIssue() {
        super();
    }

    /**
     * Overrides the status change handler to fail when transitioning FROM IN_PROGRESS.
     *
     * <p>The handler only fails when the old status is IN_PROGRESS (which happens
     * when unassigning from an IN_PROGRESS issue). This allows the .given() phase
     * to set up the aggregate state (transitioning TO IN_PROGRESS), while failing
     * during the .when() phase (transitioning FROM IN_PROGRESS).</p>
     *
     * <p>When this handler throws, the entire unit of work is rolled back—no events
     * are persisted and the entity state is restored to its pre-command values.</p>
     */
    @Override
    @EventSourcingHandler
    protected void on(IssueStatusChanged event) {
        if (event.oldStatus() == Status.IN_PROGRESS) {
            throw new RuntimeException("Simulated failure in IssueStatusChanged handler - testing atomic rollback");
        }
        super.on(event);
    }
}
