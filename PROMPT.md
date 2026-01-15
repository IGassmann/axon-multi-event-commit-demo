# Axon Framework 5 Atomic Event Commit Demonstration

Create a demonstration showing how Axon Framework 5 handles atomic multi-event commits within a single aggregate, using the following scenario:

## Business Domain: Issue Tracker

**Invariant**: Every issue in "In Progress" status **must** have an assignee. An issue cannot be "In Progress" without an assignee.

## Scenario to Demonstrate

When a user removes an assignee from an issue that is currently "In Progress", the system must atomically:

1. Remove the assignee
2. Change the status to "Backlog"

Both state changes must happen together. If only the first change is applied, the invariant is violated.

## Requirements

### Technology Stack

- Java 21+ or Kotlin
- Axon Framework 5.x (latest stable)
- Spring Boot 3.x
- Gradle or Maven
- In-memory event store (no external dependencies)

### Aggregate: `Issue`

Fields:

- `id` - unique identifier
- `title` - issue title
- `status` - enum: `Backlog`, `InProgress`, `Review`, `Done`
- `assigneeId` - nullable, the assigned user's ID

Invariant enforcement: throw exception if status is `InProgress` and `assigneeId` is null after any event is applied.

### Events (granular, not combined)

- `IssueCreated { issueId, title }`
- `IssueAssigneeChanged { issueId, newAssigneeId }`
- `IssueAssigneeRemoved { issueId, previousAssigneeId }`
- `IssueStatusChanged { issueId, oldStatus, newStatus }`

### Commands

- `CreateIssue { title }`
- `AssignIssue { issueId, assigneeId }`
- `UnassignIssue { issueId }` - This command must emit **two events** atomically when the issue is "In Progress"
- `ChangeIssueStatus { issueId, newStatus }`

### Test Cases

Use Axon's test fixtures for unit tests:

1. **Happy path**: Unassigning from an "In Progress" issue emits both `IssueAssigneeRemoved` and `IssueStatusChanged` in the same unit of work, and the aggregate ends in a valid state.

2. **Invariant validation**: Demonstrate that Axon's event sourcing handlers apply events synchronously within the command handling—if you query aggregate state after the first event is applied (inside the same command handler), the second event's handler sees the updated state.

3. **Invalid state rejection**: If somehow only `IssueAssigneeRemoved` were applied to an "In Progress" issue without the status change, the invariant check should fail.

### Documentation

- README explaining the scenario and what the code demonstrates
- Comments in the aggregate showing where events are applied synchronously
- Note that Axon's `AggregateLifecycle.apply()` triggers event sourcing handlers immediately within the same `UnitOfWork`

## Key Points to Highlight in the Code

```java
// In the command handler:
@CommandHandler
public void handle(UnassignIssue command) {
    // Validate current state
    if (this.assigneeId == null) {
        throw new IllegalStateException("Issue has no assignee");
    }

    // Apply first event - event sourcing handler runs IMMEDIATELY
    AggregateLifecycle.apply(new IssueAssigneeRemoved(this.id, this.assigneeId));

    // At this point, this.assigneeId is already null (handler already ran)

    // If we were InProgress, we must also change status to maintain invariant
    if (this.status == Status.IN_PROGRESS) {
        AggregateLifecycle.apply(new IssueStatusChanged(this.id, Status.IN_PROGRESS, Status.BACKLOG));
    }

    // Both events are committed atomically at the end of the UnitOfWork
}
```

## Expected Output

The tests should prove that:

1. Multiple events applied in a single command handler are committed atomically
2. Event sourcing handlers run synchronously (state is updated immediately after each `apply()`)
3. The aggregate's invariant is enforced at the end of the command handling
4. This is the same pattern LiveStore uses with materializers—they must run synchronously to maintain aggregate invariants

## Background Context

This demonstration supports the argument made in [livestorejs/livestore#503](https://github.com/livestorejs/livestore/issues/503) that materializers (equivalent to Axon's event sourcing handlers) must process events synchronously within the same transaction to maintain aggregate invariants.

The scenario shows that even in Axon Framework—a mature, production-tested event sourcing framework—event sourcing handlers are **not** eventually consistent. They run immediately when `apply()` is called, and all events from a single command are committed atomically.
