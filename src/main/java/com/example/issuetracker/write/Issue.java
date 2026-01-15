package com.example.issuetracker.write;

import com.example.issuetracker.commands.UnassignIssue;
import com.example.issuetracker.events.IssueAssigneeChanged;
import com.example.issuetracker.events.IssueAssigneeRemoved;
import com.example.issuetracker.events.IssueCreated;
import com.example.issuetracker.events.IssueStatusChanged;
import com.example.issuetracker.shared.IssueId;
import com.example.issuetracker.shared.IssueTags;
import com.example.issuetracker.shared.Status;
import org.axonframework.eventsourcing.annotation.EventSourcedEntity;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;

/**
 * Event-sourced entity representing an Issue with entity-centric command handlers.
 *
 * <h2>Axon Framework 5 Entity-Centric Pattern</h2>
 * This class combines both command handling and event sourcing in a single entity:
 * <ul>
 *   <li>{@code @CommandHandler} methods - handle commands and append events</li>
 *   <li>{@code @EventSourcingHandler} methods - apply state changes from events</li>
 *   <li>{@code @EntityCreator} - initializes the entity with default state</li>
 * </ul>
 *
 * <h2>Key Invariant</h2>
 * An issue in {@link Status#IN_PROGRESS} status MUST have an assignee.
 *
 * <h2>Key Demonstration: Atomic Multi-Event Commits</h2>
 * The {@link #handle(UnassignIssue, EventAppender)} method shows how multiple
 * events can be appended and committed atomically within a single command handler.
 * All events are committed together when the handler completes, or all are
 * rolled back if any handler fails.
 */
@EventSourcedEntity(tagKey = IssueTags.ISSUE_ID)
public class Issue {

    private IssueId id;
    private String title;
    private Status status;
    private String assigneeId;
    private boolean created;

    /**
     * Creates a new Issue entity with default (empty) state.
     * The entity is not considered "created" until the {@link IssueCreated} event is applied.
     */
    @EntityCreator
    public Issue() {
        this.created = false;
    }

    // ========================================================================
    // Command Handlers
    // ========================================================================

    /**
     * Unassigns an issue.
     *
     * <h3>CRITICAL: This demonstrates atomic multi-event commits</h3>
     *
     * When unassigning from an IN_PROGRESS issue, we must emit TWO events atomically:
     * <ol>
     *   <li>{@link IssueAssigneeRemoved} - removes the assignee</li>
     *   <li>{@link IssueStatusChanged} - changes status to BACKLOG</li>
     * </ol>
     *
     * Both events are committed together when this handler completes. If any
     * {@code @EventSourcingHandler} fails, all events and state changes are rolled back.
     */
    @CommandHandler
    public void handle(UnassignIssue command, EventAppender appender) {
        assertCreated();
        if (assigneeId == null) {
            throw new IllegalStateException("Issue has no assignee to remove");
        }

        // Capture current state before applying events
        String previousAssigneeId = assigneeId;
        Status currentStatus = status;

        // First event - removes the assignee
        appender.append(new IssueAssigneeRemoved(command.issueId(), previousAssigneeId));

        // Second event - if IN_PROGRESS, change status to maintain invariant
        if (currentStatus == Status.IN_PROGRESS) {
            appender.append(new IssueStatusChanged(command.issueId(), Status.IN_PROGRESS, Status.BACKLOG));
        }

        // When this method returns, Axon commits ALL appended events atomically.
        // If any handler fails, all events and state changes are rolled back.
    }

    // ========================================================================
    // Event Sourcing Handlers
    // ========================================================================

    @EventSourcingHandler
    public void on(IssueCreated event) {
        this.id = event.issueId();
        this.title = event.title();
        this.status = Status.BACKLOG;
        this.assigneeId = null;
        this.created = true;
    }

    @EventSourcingHandler
    public void on(IssueAssigneeChanged event) {
        this.assigneeId = event.newAssigneeId();
    }

    @EventSourcingHandler
    public void on(IssueAssigneeRemoved event) {
        this.assigneeId = null;
    }

    @EventSourcingHandler
    protected void on(IssueStatusChanged event) {
        this.status = event.newStatus();
    }

    // ========================================================================
    // Query Methods
    // ========================================================================

    public Status getStatus() {
        return status;
    }

    public String getAssigneeId() {
        return assigneeId;
    }

    private void assertCreated() {
        if (!created) {
            throw new IllegalStateException("Issue does not exist");
        }
    }
}
