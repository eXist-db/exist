# Code Smells

## Detected design problems with severity and evidence

Code smells detected using composite metric thresholds including God Class, Feature Envy, and Data Class. Each smell includes severity rating and the metric evidence that triggered detection. Use this to prioritize refactoring.

## Data Tables

### Code smells

**File:** [`code-smells.csv`](code-smells.csv)

Detected code smells including God Class, Feature Envy, and Data Class with severity ratings and the metric evidence that triggered detection.

| Column | Description |
|--------|-------------|
| Source path | The path to the source file containing the smell. |
| Class name | The fully qualified name of the class. |
| Method name | The method name, if the smell is method-level (e.g., Feature Envy). Null for class-level smells. |
| Smell type | The type of code smell: GOD_CLASS, FEATURE_ENVY, or DATA_CLASS. |
| Severity | Severity based on how far metrics exceed thresholds: LOW, MEDIUM, HIGH, or CRITICAL. |
| Evidence | The metric values that triggered detection, e.g., 'WMC=52, TCC=0.21, ATFD=8'. |

