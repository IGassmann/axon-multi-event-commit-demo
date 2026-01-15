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

### Key Points

1. **Atomic Commits**: All events appended within a single command handler are committed together. If the transaction fails, no events are persisted and state is rolled back.

2. **Invariant Enforcement**: Multiple events can be applied to transition through intermediate states and end in a valid final state—all committed as one atomic unit.

## Project Structure

```
src/main/java/com/example/issuetracker/
├── commands/
│   └── UnassignIssue.java            # Triggers multi-event scenario
├── events/
│   ├── IssueCreated.java             # Uses @EventTag for consistency boundary
│   ├── IssueAssigneeChanged.java
│   ├── IssueAssigneeRemoved.java
│   └── IssueStatusChanged.java
└── write/
    ├── Issue.java                    # Entity with @CommandHandler + @EventSourcingHandler
    └── IssueConfiguration.java       # Axon 5 configuration

src/test/java/com/example/issuetracker/
├── IssueTestFixture.java             # Test infrastructure
├── IssueCommandHandlerTest.java      # Comprehensive tests
│
│   # Rollback verification infrastructure
├── FailingIssue.java                 # Extends Issue, overrides handler to throw
├── FailingIssueQueryHandler.java     # Query handler to verify entity state
├── FailingIssueConfiguration.java    # Configuration for rollback tests
├── GetIssueStateQuery.java           # Query to fetch entity state
└── IssueStateResponse.java           # Response with entity state
```

## Axon Framework 5 Patterns Used

### Entity-Centric Command Handlers

Command handlers are defined directly in the entity class alongside event sourcing handlers:

```java
@EventSourcedEntity(tagKey = "issueId")
public class Issue {

    @EntityCreator
    public Issue() { ... }

    // Command handler in the entity itself
    @CommandHandler
    public void handle(UnassignIssue command, EventAppender appender) {
        // First event
        appender.append(new IssueAssigneeRemoved(command.issueId(), previousAssigneeId));
        // Second event (if needed)
        if (currentStatus == Status.IN_PROGRESS) {
            appender.append(new IssueStatusChanged(...));
        }
    }

    // Event sourcing handlers apply state changes
    @EventSourcingHandler
    public void on(IssueAssigneeRemoved event) {
        this.assigneeId = null;
    }
}
```

### Event Tagging for Consistency Boundaries

```java
public record IssueCreated(
    @EventTag String issueId,  // Tag key defaults to field name "issueId"
    String title
) {}
```

### Query Handler with Entity Injection

```java
@QueryHandler
public IssueStateResponse handle(GetIssueStateQuery query,
                                 @InjectEntity(idProperty = "issueId") FailingIssue issue) {
    return new IssueStateResponse(
            issue.getAssigneeId(),
            issue.getStatus()
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
2. **Atomic Rollback**: `commandFailsWhenEventSourcingHandlerThrows_stateIsRolledBack` proves that when an event sourcing handler fails, all state changes are rolled back (verified via point-to-point query)

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

### 2. Atomic Rollback Demonstration

This test uses a `FailingIssue` entity (extends `Issue`) that throws an exception in its `IssueStatusChanged` handler to demonstrate atomic rollback:

```java
@Test
void commandFailsWhenEventSourcingHandlerThrows_stateIsRolledBack() {
    var issueId = "issue-1";

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
1. All events from a single command are part of the **same unit of work**
2. If ANY handler fails, the **entire command fails**
3. The **entity state is rolled back** - verified by querying after the failure
4. **No events are persisted** to the event store

## Implications for LiveStore

This demonstration proves that in a mature, production-tested event sourcing framework:

- **All events from a single command are committed atomically**
- **If any handler fails, all state changes are rolled back**
- **This is required to maintain aggregate invariants**

The same pattern applies to LiveStore: materializers must commit events atomically to allow aggregates to maintain their invariants during multi-event state transitions.

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
