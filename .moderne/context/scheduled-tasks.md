# Scheduled Tasks

## Scheduled tasks, cron jobs, and background processing

Scheduled tasks and background jobs detected in the application including cron expressions, fixed rates, and fixed delays. Use this to understand what background processing the application performs.

## Data Tables

### Scheduled tasks

**File:** [`scheduled-tasks.csv`](scheduled-tasks.csv)

Scheduled tasks, cron jobs, and background processing detected in the application.

| Column | Description |
|--------|-------------|
| Source path | The path to the source file containing the scheduled task. |
| Class name | The fully qualified name of the class containing the task. |
| Method name | The name of the scheduled method. |
| Method signature | The full method signature. |
| Framework | The framework providing scheduling support (Spring, Quartz, etc.). |
| Schedule type | The type of schedule: cron, fixedRate, fixedDelay, or trigger. |
| Expression | The scheduling expression (cron pattern, rate in ms, etc.). |
| Initial delay | Initial delay before first execution, if specified. |

