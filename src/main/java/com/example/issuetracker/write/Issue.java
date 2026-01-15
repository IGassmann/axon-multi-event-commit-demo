package com.example.issuetracker.write;

import com.example.issuetracker.commands.AssignIssue;
import com.example.issuetracker.commands.ChangeIssueStatus;
import com.example.issuetracker.commands.CreateIssue;
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
 * events can be appended atomically within a single command handler.
 *
 * <h2>Synchronous Event Application</h2>
 * The {@code @EventSourcingHandler} methods run SYNCHRONOUSLY when events are appended.
 * This means:
 * <ul>
 *   <li>State changes are visible immediately after {@code EventAppender.append()}</li>
 *   <li>Command handlers can read updated state between event applications</li>
 *   <li>Multiple events can be applied in sequence, with state updating after each one</li>
 * </ul>
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
     * Creates a new issue. Issues start in BACKLOG status with no assignee.
     */
    @CommandHandler
    public void handle(CreateIssue command, EventAppender appender) {
        if (created) {
            return; // Idempotent: issue already exists
        }
        appender.append(new IssueCreated(command.issueId(), command.title()));
    }

    /**
     * Assigns an issue to a user.
     */
    @CommandHandler
    public void handle(AssignIssue command, EventAppender appender) {
        assertCreated();
        if (command.assigneeId() == null || command.assigneeId().isBlank()) {
            throw new IllegalArgumentException("Assignee ID cannot be null or blank");
        }
        appender.append(new IssueAssigneeChanged(command.issueId(), command.assigneeId()));
    }

    /**
     * Unassigns an issue.
     *
     * <h3>CRITICAL: This demonstrates atomic multi-event application</h3>
     *
     * When unassigning from an IN_PROGRESS issue, we must emit TWO events atomically:
     * <ol>
     *   <li>{@link IssueAssigneeRemoved} - removes the assignee</li>
     *   <li>{@link IssueStatusChanged} - changes status to BACKLOG</li>
     * </ol>
     *
     * <h3>How It Works in Axon Framework 5</h3>
     *
     * <pre>{@code
     * // 1. Capture current state before applying events
     * String previousAssigneeId = this.assigneeId;
     * Status currentStatus = this.status;
     *
     * // 2. Append first event - @EventSourcingHandler runs IMMEDIATELY
     * appender.append(new IssueAssigneeRemoved(...));
     * // At this point, this.assigneeId is NOW null!
     *
     * // 3. Check if we need to change status to maintain invariant
     * if (currentStatus == Status.IN_PROGRESS) {
     *     appender.append(new IssueStatusChanged(...));
     * }
     *
     * // 4. Both events committed atomically when handler returns
     * }</pre>
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

        // Apply first event - the @EventSourcingHandler runs IMMEDIATELY (synchronously)
        // After this line, this.assigneeId is ALREADY null!
        appender.append(new IssueAssigneeRemoved(command.issueId(), previousAssigneeId));

        // IMPORTANT: At this point, the entity's state is ALREADY updated!
        // The @EventSourcingHandler for IssueAssigneeRemoved ran synchronously.
        // This is NOT eventually consistent - it's synchronous!

        // If the issue was IN_PROGRESS, we must change status to maintain the invariant:
        // "IN_PROGRESS issues must have an assignee"
        if (currentStatus == Status.IN_PROGRESS) {
            // Apply second event - also runs its handler immediately
            appender.append(new IssueStatusChanged(command.issueId(), Status.IN_PROGRESS, Status.BACKLOG));
        }

        // When this method returns, Axon commits ALL appended events atomically.
        // If the transaction fails, neither event is persisted.
    }

    /**
     * Changes the status of an issue.
     * Validates that the invariant will be satisfied after the status change.
     */
    @CommandHandler
    public void handle(ChangeIssueStatus command, EventAppender appender) {
        assertCreated();
        if (command.newStatus() == status) {
            return; // No change needed
        }

        // Validate: cannot move to IN_PROGRESS without an assignee
        if (command.newStatus() == Status.IN_PROGRESS && assigneeId == null) {
            throw new IllegalStateException(
                    "Cannot change status to IN_PROGRESS: issue must have an assignee"
            );
        }

        appender.append(new IssueStatusChanged(command.issueId(), status, command.newStatus()));
    }

    // ========================================================================
    // Event Sourcing Handlers
    // These run SYNCHRONOUSLY when EventAppender.append() is called!
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
    public void on(IssueStatusChanged event) {
        this.status = event.newStatus();
    }

    // ========================================================================
    // Query Methods
    // ========================================================================

    public IssueId getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public Status getStatus() {
        return status;
    }

    public String getAssigneeId() {
        return assigneeId;
    }

    public boolean isCreated() {
        return created;
    }

    private void assertCreated() {
        if (!created) {
            throw new IllegalStateException("Issue does not exist");
        }
    }
}
