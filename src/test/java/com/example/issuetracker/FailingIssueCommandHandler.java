package com.example.issuetracker;

import com.example.issuetracker.commands.AssignIssue;
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
 * Command handler that uses the FailingIssue entity.
 *
 * <p>This is identical to IssueCommandHandler but uses FailingIssue instead of Issue.
 * The FailingIssue entity throws an exception in its IssueStatusChanged handler,
 * which allows us to test atomic rollback behavior.</p>
 */
public class FailingIssueCommandHandler {

    @CommandHandler
    public void handle(CreateIssue command,
                       @InjectEntity(idProperty = IssueTags.ISSUE_ID) FailingIssue issue,
                       EventAppender appender) {
        if (issue.isCreated()) {
            return;
        }
        appender.append(new IssueCreated(command.issueId(), command.title()));
    }

    @CommandHandler
    public void handle(AssignIssue command,
                       @InjectEntity(idProperty = IssueTags.ISSUE_ID) FailingIssue issue,
                       EventAppender appender) {
        issue.assertCreated();
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
     *   <li>IssueStatusChanged - its handler THROWS (in FailingIssue)</li>
     * </ol>
     *
     * <p>Because the second handler fails, the entire unit of work is rolled back,
     * and NEITHER event is persisted. This demonstrates atomic commit behavior.</p>
     */
    @CommandHandler
    public void handle(UnassignIssue command,
                       @InjectEntity(idProperty = IssueTags.ISSUE_ID) FailingIssue issue,
                       EventAppender appender) {
        issue.assertCreated();
        if (issue.getAssigneeId() == null) {
            throw new IllegalStateException("Issue has no assignee to remove");
        }

        String previousAssigneeId = issue.getAssigneeId();
        Status currentStatus = issue.getStatus();

        // First event - handler runs successfully and sets assigneeId to null
        appender.append(new IssueAssigneeRemoved(command.issueId(), previousAssigneeId));

        // Second event - handler THROWS in FailingIssue, causing rollback
        if (currentStatus == Status.IN_PROGRESS) {
            appender.append(new IssueStatusChanged(command.issueId(), Status.IN_PROGRESS, Status.BACKLOG));
        }
    }
}
