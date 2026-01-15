package com.example.issuetracker;

import com.example.issuetracker.commands.AssignIssue;
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
 * A test-only Issue entity that throws an exception in the IssueStatusChanged handler.
 *
 * <p>This entity uses entity-centric command handlers and is used to demonstrate
 * that Axon Framework commits events atomically. When the IssueStatusChanged handler
 * fails, the entire unit of work is rolled back, meaning the IssueAssigneeRemoved
 * event is also NOT persisted.</p>
 *
 * <p>This proves that event sourcing handlers run within the same transaction boundary,
 * and a failure in any handler causes all events to be rolled back.</p>
 */
@EventSourcedEntity(tagKey = IssueTags.ISSUE_ID)
public class FailingIssue {

    private IssueId id;
    private String title;
    private Status status;
    private String assigneeId;
    private boolean created;

    @EntityCreator
    public FailingIssue() {
        this.created = false;
    }

    // ========================================================================
    // Command Handlers
    // ========================================================================

    @CommandHandler
    public void handle(CreateIssue command, EventAppender appender) {
        if (created) {
            return;
        }
        appender.append(new IssueCreated(command.issueId(), command.title()));
    }

    @CommandHandler
    public void handle(AssignIssue command, EventAppender appender) {
        assertCreated();
        if (command.assigneeId() == null || command.assigneeId().isBlank()) {
            throw new IllegalArgumentException("Assignee ID cannot be null or blank");
        }
        appender.append(new IssueAssigneeChanged(command.issueId(), command.assigneeId()));
    }

    /**
     * Unassigns an issue - this will trigger the failing handler.
     *
     * <p>When the issue is IN_PROGRESS, this handler appends TWO events:
     * <ol>
     *   <li>IssueAssigneeRemoved - its handler runs successfully</li>
     *   <li>IssueStatusChanged - its handler THROWS</li>
     * </ol>
     *
     * <p>Because the second handler fails, the entire unit of work is rolled back,
     * and NEITHER event is persisted. This demonstrates atomic commit behavior.</p>
     */
    @CommandHandler
    public void handle(UnassignIssue command, EventAppender appender) {
        assertCreated();
        if (assigneeId == null) {
            throw new IllegalStateException("Issue has no assignee to remove");
        }

        String previousAssigneeId = assigneeId;
        Status currentStatus = status;

        // First event - handler runs successfully and sets assigneeId to null
        appender.append(new IssueAssigneeRemoved(command.issueId(), previousAssigneeId));

        // Second event - handler THROWS, causing rollback
        if (currentStatus == Status.IN_PROGRESS) {
            appender.append(new IssueStatusChanged(command.issueId(), Status.IN_PROGRESS, Status.BACKLOG));
        }
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

    /**
     * This handler FAILS intentionally when transitioning FROM IN_PROGRESS.
     *
     * <p>The handler only fails when the old status is IN_PROGRESS (which happens
     * when unassigning from an IN_PROGRESS issue). This allows the .given() phase
     * to set up the aggregate state (transitioning TO IN_PROGRESS), while failing
     * during the .when() phase (transitioning FROM IN_PROGRESS).</p>
     *
     * <p>When this handler throws, the entire unit of work is rolled back.
     * This means that even though IssueAssigneeRemoved was already "applied"
     * (its handler ran), the event will NOT be persisted to the event store.</p>
     */
    @EventSourcingHandler
    public void on(IssueStatusChanged event) {
        // Only fail when transitioning FROM IN_PROGRESS (i.e., during unassign)
        // This allows setup (transitioning TO IN_PROGRESS) to succeed
        if (event.oldStatus() == Status.IN_PROGRESS) {
            throw new RuntimeException("Simulated failure in IssueStatusChanged handler - testing atomic rollback");
        }
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

    public void assertCreated() {
        if (!created) {
            throw new IllegalStateException("Issue does not exist");
        }
    }
}
