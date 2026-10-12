# FR-07 Unit Test Report

## Summary

The FR-07 unit tests cover DMS timeout activation, Primary-to-Backup escalation, executor acknowledgement, notification outcomes, audit logging, and error isolation.

| Test class | Scenarios | Latest recorded result |
|---|---:|---|
| `DmsActivationProcessorTest` | 14 | Passed |
| `DmsAutomationServiceImplTest` | 9 | Passed |
| **FR-07 total** | **23** | **Passed** |

Run the FR-07 tests from the project root in PowerShell:

```powershell
.\mvnw.cmd -Dtest=DmsActivationProcessorTest,DmsAutomationServiceImplTest test
```

Run the complete test suite:

```powershell
.\mvnw.cmd test
```

## `DmsActivationProcessorTest`

| # | Test context | Expected behavior verified |
|---:|---|---|
| 1 | An Owner's DMS check-in is overdue and a valid Primary exists. | DMS becomes `TRIGGERED`; Primary becomes `ACTIVE`; Backup remains `INACTIVE`; check-in, `EXECUTOR_ACTIVATED` notification, and audit entries are recorded. |
| 2 | At processing time, the Owner is no longer overdue or the DMS is no longer `ACTIVE`. | Activation is skipped and no notification is sent. |
| 3 | Sending the Primary activation notification fails. | DMS remains `TRIGGERED` and Primary remains `ACTIVE`; notification is recorded as `FAILED` without `sentAt`. |
| 4 | No valid Primary exists, but a Backup does. | Backup is activated directly; an `EXECUTOR_ACTIVATED` notification and audit entry are recorded. |
| 5 | Primary was already `REVOKED` when the overdue DMS is processed. | Primary remains revoked and Backup is activated. |
| 6 | Neither Primary nor Backup exists. | DMS becomes `TRIGGERED` without throwing; no notification is sent and an audit warning is recorded. |
| 7 | Primary has not acknowledged and exactly 14 days have elapsed. | Primary becomes `REVOKED`, Backup becomes `ACTIVE`, and assignment updates and audit entries (`EXECUTOR_REVOKE` with reason `BR03_TIMEOUT`, then `EXECUTOR_ACTIVATED`) are recorded. |
| 8 | Primary has not acknowledged, but only 13 days have elapsed. | No escalation occurs; Primary stays `ACTIVE` and Backup stays `INACTIVE`. |
| 9 | Primary acknowledged, even though more than 14 days elapsed. | No escalation occurs; Primary stays `ACTIVE` and Backup stays `INACTIVE`. |
| 10 | The DMS configuration sets the wait period to 3 days. | The configured duration is used; escalation occurs at 3 days. |
| 11 | The previous activation notification failed, and a retry succeeds. | Notification becomes `SENT`; the wait period restarts from the successful send time, so Backup is not activated immediately. |
| 12 | Retrying a failed notification also fails. | Notification remains `FAILED` and Backup is not activated. |
| 13 | The escalation deadline has passed, but no eligible inactive Backup exists. | Primary is not revoked. |
| 14 | The DMS is not `TRIGGERED` when escalation is checked. | Escalation and notification sending are skipped. |

## `DmsAutomationServiceImplTest`

| # | Test context | Expected behavior verified |
|---:|---|---|
| 1 | One Owner fails during an overdue-DMS scan. | Processing continues for the other Owners in the same scan. |
| 2 | One DMS fails during the escalation pass. | Other `TRIGGERED` DMS configurations are still processed. |
| 3 | Primary acknowledges activation for the first time. | `acknowledgedAt` is set, the activation notification becomes `READ`, and an audit entry identifies the Executor as actor. |
| 4 | Primary acknowledges activation again. | The original acknowledgement timestamp is preserved and no additional audit entry is written. |
| 5 | Executor identifies themselves by user ID instead of email. | The identifier is accepted and the acknowledgement is recorded. |
| 6 | The caller is not the assigned Executor, the assignment does not exist, or the identifier is missing. | `ExecutorAssignmentNotFoundException` is raised and no audit entry is written. |
| 7 | Executor is `INACTIVE` or `REVOKED`. | `ExecutorNotActiveException` is raised. |
| 8 | The DMS is not `TRIGGERED` when acknowledgement is attempted. | `DmsNotTriggeredException` is raised and no audit entry is written. |
| 9 | An active Backup acknowledges activation. | Acknowledgement is recorded and the notification lookup uses type `EXECUTOR_ACTIVATED`. |

## Scope and limitations

These are unit tests using Mockito mocks. They validate service/processor behavior and interactions, but do not connect to MySQL or send real email.
