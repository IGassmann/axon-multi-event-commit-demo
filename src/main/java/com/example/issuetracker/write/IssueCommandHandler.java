package com.example.issuetracker.write;

import com.example.issuetracker.commands.AssignIssue;
import com.example.issuetracker.commands.ChangeIssueStatus;
import com.example.issuetracker.commands.CreateIssue;
import com.example.issuetracker.commands.UnassignIssue;
import com.example.issuetracker.events.IssueAssigneeChanged;
import com.example.issuetracker.events.IssueAssigneeRemoved;
import com.example.issuetracker.events.IssueCreated;
import com.example.issuetracker.events.IssueStatusChanged;
import com.example.issuetracker.shared.IssueTags;
import com.example.issuetracker.shared.Status;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.annotation.InjectEntity;

/**
 * Command handlers for the Issue entity demonstrating Axon Framework 5 patterns.
 *
 * <h2>Key Demonstration: Atomic Multi-Event Commits</h2>
 * The {@link #handle(UnassignIssue, Issue, EventAppender)} method shows how multiple
 * events can be appended atomically within a single command handler. When an issue
 * is unassigned while in IN_PROGRESS status, both {@link IssueAssigneeRemoved} and
 * {@link IssueStatusChanged} events are appended together.
 *
 * <h2>Synchronous Event Application</h2>
 * In Axon Framework 5, when {@link EventAppender#append(Object...)} is called:
 * <ol>
 *   <li>Events are staged for persistence</li>
 *   <li>The entity's {@code @EventSourcingHandler} methods run IMMEDIATELY (synchronously)</li>
 *   <li>State is updated before the next line of code executes</li>
 *   <li>All events are committed atomically at the end of the unit of work</li>
 * </ol>
 *
 * This synchronous behavior allows command handlers to:
 * <ul>
 *   <li>Apply an event and immediately see the state change</li>
 *   <li>Make decisions based on the updated state</li>
 *   <li>Apply additional events to maintain invariants</li>
 * </ul>
 */
public class IssueCommandHandler {

    /**
     * Creates a new issue. Issues start in BACKLOG status with no assignee.
     */
    @CommandHandler
    public void handle(CreateIssue command,
                       @InjectEntity(idProperty = IssueTags.ISSUE_ID) Issue issue,
                       EventAppender appender) {
        if (issue.isCreated()) {
            return; // Idempotent: issue already exists
        }
        appender.append(new IssueCreated(command.issueId(), command.title()));
    }

    /**
     * Assigns an issue to a user.
     */
    @CommandHandler
    public void handle(AssignIssue command,
                       @InjectEntity(idProperty = IssueTags.ISSUE_ID) Issue issue,
                       EventAppender appender) {
        issue.assertCreated();
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
     * String previousAssigneeId = issue.getAssigneeId();
     * Status currentStatus = issue.getStatus();
     *
     * // 2. Append first event - @EventSourcingHandler runs IMMEDIATELY
     * appender.append(new IssueAssigneeRemoved(...));
     * // At this point, issue.getAssigneeId() is NOW null!
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
    public void handle(UnassignIssue command,
                       @InjectEntity(idProperty = IssueTags.ISSUE_ID) Issue issue,
                       EventAppender appender) {
        issue.assertCreated();
        if (issue.getAssigneeId() == null) {
            throw new IllegalStateException("Issue has no assignee to remove");
        }

        // Capture current state before applying events
        String previousAssigneeId = issue.getAssigneeId();
        Status currentStatus = issue.getStatus();

        // Apply first event - the @EventSourcingHandler runs IMMEDIATELY (synchronously)
        // After this line, issue.getAssigneeId() is ALREADY null!
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
    public void handle(ChangeIssueStatus command,
                       @InjectEntity(idProperty = IssueTags.ISSUE_ID) Issue issue,
                       EventAppender appender) {
        issue.assertCreated();
        if (command.newStatus() == issue.getStatus()) {
            return; // No change needed
        }

        // Validate: cannot move to IN_PROGRESS without an assignee
        if (command.newStatus() == Status.IN_PROGRESS && issue.getAssigneeId() == null) {
            throw new IllegalStateException(
                    "Cannot change status to IN_PROGRESS: issue must have an assignee"
            );
        }

        appender.append(new IssueStatusChanged(command.issueId(), issue.getStatus(), command.newStatus()));
    }
}
