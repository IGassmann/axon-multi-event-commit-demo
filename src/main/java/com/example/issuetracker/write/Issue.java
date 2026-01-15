package com.example.issuetracker.write;

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

/**
 * Event-sourced entity representing an Issue.
 *
 * <h2>Axon Framework 5 Entity Pattern</h2>
 * This class is annotated with {@code @EventSourcedEntity} and contains:
 * <ul>
 *   <li>{@code @EntityCreator} - initializes the entity with default state</li>
 *   <li>{@code @EventSourcingHandler} methods - apply state changes from events</li>
 *   <li>{@code tagKey} on @EventSourcedEntity - defines which events belong to this entity</li>
 * </ul>
 *
 * <h2>Key Invariant</h2>
 * An issue in {@link Status#IN_PROGRESS} status MUST have an assignee.
 * This invariant is maintained by the command handler logic, not enforced in the entity itself,
 * because command handlers are responsible for business rules while entities only apply state.
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
        // NOTE: The invariant check is NOT here because event sourcing handlers
        // should only apply state, not enforce business rules.
        // The invariant is maintained by command handler logic that ensures
        // a status change event is also appended when necessary.
    }

    @EventSourcingHandler
    public void on(IssueStatusChanged event) {
        this.status = event.newStatus();
    }

    // ========================================================================
    // Query Methods (for command handlers to read state)
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

    public void assertCreated() {
        if (!created) {
            throw new IllegalStateException("Issue does not exist");
        }
    }
}
