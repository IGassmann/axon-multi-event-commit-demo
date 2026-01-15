# Axon Framework 5: Atomic Multi-Event Commit Demonstration

This project demonstrates how [Axon Framework 5](https://docs.axoniq.io/) handles atomic multi-event commits within a single aggregate. It proves that **all events appended within a single command handler are committed atomically**—either all events are persisted together, or none are (on failure, all state changes are rolled back).

## Background

This demonstration supports the argument made in [livestorejs/livestore#503](https://github.com/livestorejs/livestore/issues/503) that materializers (equivalent to Axon's event sourcing handlers) must commit events atomically within the same transaction to maintain aggregate invariants.

## Business Domain: Issue Tracker

### The Invariant

> **Every issue in "In Progress" status MUST have an assignee.**

An issue cannot be "In Progress" without an assignee. This is a strict business rule that must never be violated, even temporarily.

### The Scenario

When a user removes an assignee from an issue that is currently "In Progress", the system must atomically:

1. Remove the assignee
2. Change the status to "Backlog"

Both state changes must happen together. If only the first change is applied, the invariant is violated.

## How Axon Framework 5 Handles This

### Atomic Event Commits

When `EventAppender.append()` is called in a command handler:

1. The event is passed to the entity's `@EventSourcingHandler` to update state
2. The event is staged for persistence but NOT yet committed
3. After the command handler completes, ALL staged events are committed **atomically**
4. If any handler fails, the entire unit of work is rolled back—no events are persisted

```java
@EventSourcedEntity(tagKey = "issueId")
public class Issue {

    @CommandHandler
    public void handle(UnassignIssue command, EventAppender appender) {
        // Capture current state
        String previousAssigneeId = this.assigneeId;
        Status currentStatus = this.status;

        // First event
        appender.append(new IssueAssigneeRemoved(command.issueId(), previousAssigneeId));

        // Second event (if needed to maintain invariant)
        if (currentStatus == Status.IN_PROGRESS) {
            appender.append(new IssueStatusChanged(command.issueId(), Status.IN_PROGRESS, Status.BACKLOG));
        }

        // Both events committed atomically when this method returns
    }

    @EventSourcingHandler
    public void on(IssueAssigneeRemoved event) {
        this.assigneeId = null;
    }

    @EventSourcingHandler
    public void on(IssueStatusChanged event) {
        this.status = event.newStatus();
    }
}
```

## Running the Tests

### Prerequisites

- Java 21 or later
- No other dependencies required (uses in-memory event store)

### Run Tests

```bash
./gradlew test
```

### Expected Output

All tests should pass, demonstrating:

1. **Atomic Multi-Event Commits**: `unassignFromInProgressIssue_emitsTwoEventsAtomically` proves both events are emitted from a single command
2. **Atomic Rollback**: `commandFailsWhenEventSourcingHandlerThrows_stateIsRolledBack` proves that when an event sourcing handler fails, all state changes are rolled back (verified via point-to-point query)

## References

- [Axon Framework 5 Command Handlers](https://docs.axoniq.io/axon-framework-reference/5.0/commands/command-handlers/)
- [Axon Framework 5 Event Publishing](https://docs.axoniq.io/axon-framework-reference/5.0/events/event-publishing/)
- [Axon Framework 5 Processing Context](https://docs.axoniq.io/axon-framework-reference/5.0/messaging-concepts/processing-context/)
- [Axon Framework 5 Event Processors](https://docs.axoniq.io/axon-framework-reference/5.0/events/event-processors/)

## License

MIT
