# Test Gaps

## Public non-trivial methods lacking test coverage

Public non-trivial methods that have no test coverage, ranked by risk score. Risk score combines cyclomatic complexity with architectural centrality (how many other methods call this one). Use this to prioritize where to add tests.

## Data Tables

### Test gaps

**File:** [`test-gaps.csv`](test-gaps.csv)

Public non-trivial methods that have no test coverage, ranked by risk score.

| Column | Description |
|--------|-------------|
| Source path | The path to the source file containing the untested method. |
| Class name | The fully qualified name of the class. |
| Method name | The simple name of the untested method. |
| Method signature | The full method signature. |
| Cyclomatic complexity | The cyclomatic complexity of the untested method. |
| Risk score | Risk score combining complexity and architectural centrality (call count). Higher = more critical gap. |
| Gap reason | Why this gap matters, e.g., 'complexity 15, called by 8 methods, no test coverage'. |
| Suggested test class | Suggested fully qualified name for the test class. |

