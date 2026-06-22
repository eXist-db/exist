# Test Coverage

## Maps test methods to implementation methods they verify

Maps test methods to the implementation methods they exercise. Use this to find existing tests for code you're modifying, understand what behaviors are already tested, and identify gaps in test coverage.

## Data Tables

### Test mapping

**File:** [`test-mapping.csv`](test-mapping.csv)

Maps test methods to the implementation methods they exercise.

| Column | Description |
|--------|-------------|
| Test source path | The path to the source file containing the test. |
| Test class | The fully qualified name of the test class. |
| Test method | The signature of the test method. |
| Implementation source path | The path to the source file containing the implementation. |
| Implementation class | The fully qualified name of the implementation class. |
| Implementation method | The signature of the implementation method being tested. |
| Test checksum | SHA-256 checksum of the test method source code for cache validation. |

