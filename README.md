# Axon Framework 5: Atomic Multi-Event Commit Demonstration

This project demonstrates how [Axon Framework 5](https://docs.axoniq.io/) handles atomic multi-event commits within a single aggregate. It shows that **event sourcing handlers are NOT eventually consistent**—they run synchronously when `EventAppender.append()` is called, and all events from a single command are committed atomically.

## Background

This demonstration supports the argument made in [livestorejs/livestore#503](https://github.com/livestorejs/livestore/issues/503) that materializers (equivalent to Axon's event sourcing handlers) must process events synchronously within the same transaction to maintain aggregate invariants.

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

### Synchronous Event Application

When `EventAppender.append()` is called in a command handler:

1. The event is **immediately** passed to the entity's `@EventSourcingHandler`
2. The handler updates the entity's state **synchronously** (same thread, same call stack)
3. The event is staged for persistence but NOT yet committed
4. After the command handler completes, ALL staged events are committed **atomically**

```java
@CommandHandler
public void handle(UnassignIssue command,
                   @InjectEntity(idProperty = IssueTags.ISSUE_ID) Issue issue,
                   EventAppender appender) {
    // Capture current state
    String previousAssigneeId = issue.getAssigneeId();
    Status currentStatus = issue.getStatus();

    // Apply first event - @EventSourcingHandler runs IMMEDIATELY
    appender.append(new IssueAssigneeRemoved(command.issueId(), previousAssigneeId));

    // At this point, issue.getAssigneeId() is ALREADY null!
    // The @EventSourcingHandler ran synchronously.

    // Check if we need to change status to maintain invariant
    if (currentStatus == Status.IN_PROGRESS) {
        appender.append(new IssueStatusChanged(command.issueId(), Status.IN_PROGRESS, Status.BACKLOG));
    }

    // Both events committed atomically when this method returns
}
```

### Key Points

1. **Not Eventually Consistent**: Event sourcing handlers run in the same thread, same call stack as `append()`. You can immediately read the updated state.

2. **Atomic Commits**: All events appended within a single command handler are committed together. If the transaction fails, no events are persisted.

3. **Invariant Enforcement**: Because handlers run synchronously, you can apply multiple events to transition through intermediate states and end in a valid final state.

## Project Structure

```
src/main/java/com/example/issuetracker/
├── commands/
│   ├── CreateIssue.java
│   ├── AssignIssue.java
│   ├── UnassignIssue.java            # Triggers multi-event scenario
│   └── ChangeIssueStatus.java
├── events/
│   ├── IssueCreated.java             # Uses @EventTag for consistency boundary
│   ├── IssueAssigneeChanged.java
│   ├── IssueAssigneeRemoved.java
│   └── IssueStatusChanged.java
├── shared/
│   ├── IssueId.java                  # Strongly-typed ID
│   ├── IssueTags.java                # Tag key constants
│   └── Status.java
└── write/
    ├── Issue.java                    # @EventSourcedEntity with handlers
    ├── IssueCommandHandler.java      # Command handlers using EventAppender
    └── IssueConfiguration.java       # Axon 5 configuration

src/test/java/com/example/issuetracker/
├── IssueTestFixture.java             # Test infrastructure
├── IssueCommandHandlerTest.java      # Comprehensive tests
│
│   # Rollback verification infrastructure
├── FailingIssue.java                 # Test entity that throws in handler
├── FailingIssueCommandHandler.java   # Command handler for FailingIssue
├── FailingIssueQueryHandler.java     # Query handler to verify entity state
├── FailingIssueConfiguration.java    # Configuration for rollback tests
├── GetIssueStateQuery.java           # Query to fetch entity state
└── IssueStateResponse.java           # Response with entity state
```

## Axon Framework 5 Patterns Used

### Event-Sourced Entity

```java
@EventSourcedEntity(tagKey = IssueTags.ISSUE_ID)
public class Issue {
    @EntityCreator
    public Issue() { ... }

    @EventSourcingHandler
    public void on(IssueCreated event) { ... }
}
```

### Command Handler with Entity Injection

```java
@CommandHandler
public void handle(CreateIssue command,
                   @InjectEntity(idProperty = IssueTags.ISSUE_ID) Issue issue,
                   EventAppender appender) {
    appender.append(new IssueCreated(command.issueId(), command.title()));
}
```

### Event Tagging for Consistency Boundaries

```java
public record IssueCreated(
    @EventTag(key = IssueTags.ISSUE_ID)
    IssueId issueId,
    String title
) {}
```

### Query Handler with Entity Injection

```java
@QueryHandler
public IssueStateResponse handle(GetIssueStateQuery query,
                                 @InjectEntity(idProperty = IssueTags.ISSUE_ID) FailingIssue issue) {
    return new IssueStateResponse(
            issue.getAssigneeId(),
            issue.getStatus(),
            issue.isCreated()
    );
}
```

## Running the Tests

### Prerequisites

- Java 21 or later
- No other dependencies required (uses in-memory event store)

### Install Java 21 (macOS)

```bash
# Using Homebrew
brew install openjdk@21

# Or using SDKMAN
sdk install java 21-tem
```

### Run Tests

```bash
./gradlew test
```

### Expected Output

All tests should pass, demonstrating:

1. **Atomic Multi-Event Commits**: `unassignFromInProgressIssue_emitsTwoEventsAtomically` proves both events are emitted from a single command
2. **Synchronous Event Application**: `eventSourcingHandlersRunSynchronously` proves state is updated immediately after `append()`
3. **Invariant Enforcement**: `cannotChangeToInProgressWithoutAssignee` proves the invariant is validated
4. **Atomic Rollback with State Verification**: `commandFailsWhenEventSourcingHandlerThrows_stateIsRolledBack` proves that when an event sourcing handler fails, the entity state is rolled back (verified via point-to-point query)

## Test Cases Explained

### 1. Atomic Multi-Event Commit

```java
@Test
void unassignFromInProgressIssue_emitsTwoEventsAtomically() {
    fixture.given()
            .events(List.of(
                new IssueCreated(issueId, "Fix the bug"),
                new IssueAssigneeChanged(issueId, "user-42"),
                new IssueStatusChanged(issueId, Status.BACKLOG, Status.IN_PROGRESS)
            ))
            .when().command(new UnassignIssue(issueId), Metadata.emptyInstance())
            .then().events(
                // BOTH events emitted from SAME command handler
                new IssueAssigneeRemoved(issueId, "user-42"),
                new IssueStatusChanged(issueId, Status.IN_PROGRESS, Status.BACKLOG)
            );
}
```

### 2. Synchronous State Updates

The `UnassignIssue` handler demonstrates that after calling `appender.append(IssueAssigneeRemoved)`, the handler can immediately check the current status and decide whether to append a second event. This only works because the state is updated synchronously.

### 3. Invariant Validation

```java
@Test
void cannotChangeToInProgressWithoutAssignee() {
    fixture.given().event(new IssueCreated(issueId, "Fix the bug"), Metadata.emptyInstance())
            .when().command(new ChangeIssueStatus(issueId, Status.IN_PROGRESS), Metadata.emptyInstance())
            .then()
            .exception(IllegalStateException.class);
}
```

### 4. Atomic Rollback Demonstration

This test uses a special `FailingIssue` entity that throws an exception in its `IssueStatusChanged` handler to demonstrate atomic rollback:

```java
@Test
void commandFailsWhenEventSourcingHandlerThrows_stateIsRolledBack() {
    var issueId = new IssueId("issue-1");

    failingFixture.given()
            .events(List.of(
                new IssueCreated(issueId, "Fix the bug"),
                new IssueAssigneeChanged(issueId, "user-42"),
                new IssueStatusChanged(issueId, Status.BACKLOG, Status.IN_PROGRESS)
            ))
            .when().command(new UnassignIssue(issueId), Metadata.emptyInstance())
            .then()
            .exception(StateEvolvingException.class)
            // VERIFY ROLLBACK: Query the entity state after the failed command
            .expect(config -> {
                var queryGateway = config.getComponent(QueryGateway.class);
                var state = queryGateway.query(
                        new GetIssueStateQuery(issueId),
                        IssueStateResponse.class
                ).join();

                // State should be rolled back to pre-command values
                assertThat(state.assigneeId()).isEqualTo("user-42");  // Not null!
                assertThat(state.status()).isEqualTo(Status.IN_PROGRESS);  // Not BACKLOG!
            });
}
```

This proves that:
1. Event sourcing handlers run **synchronously** within the same unit of work
2. If ANY handler fails, the **entire command fails**
3. The **entity state is rolled back** - verified by querying after the failure
4. No events are persisted to the event store

## Implications for LiveStore

This demonstration proves that in a mature, production-tested event sourcing framework:

- **Materializers (event sourcing handlers) are NOT eventually consistent**
- **They run synchronously within the same unit of work**
- **This is required to maintain aggregate invariants**

The same pattern applies to LiveStore: materializers must process events synchronously to allow aggregates to maintain their invariants during multi-event state transitions.

## Technology Stack

- Java 21
- Axon Framework 5.0.1
- JUnit 5 with Axon Test Fixtures
- Gradle 8.12

## References

- [Axon Framework 5 Documentation](https://docs.axoniq.io/)
- [Axon Framework 5 Command Handlers](https://docs.axoniq.io/axon-framework-reference/5.0/commands/command-handlers/)
- [Axon Framework 5 Event Publishing](https://docs.axoniq.io/axon-framework-reference/5.0/events/event-publishing/)
- [University Demo Reference Project](https://github.com/AxonIQ/university-demo)

## License

MIT
