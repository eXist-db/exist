# Class Quality Metrics

## Per-class cohesion, coupling, and complexity measurements

Per-class code quality metrics including Weighted Methods per Class (WMC), Lack of Cohesion (LCOM4), Tight Class Cohesion (TCC), Coupling Between Objects (CBO), and Maintainability Index. Use this to identify classes that should be split or refactored.

## Data Tables

### Class quality metrics

**File:** [`class-quality-metrics.csv`](class-quality-metrics.csv)

Per-class code quality metrics including WMC, LCOM4, TCC, CBO, and maintainability index.

| Column | Description |
|--------|-------------|
| Source path | The path to the source file containing the class. |
| Class name | The fully qualified name of the class. |
| Line count | Number of lines in the class. |
| Method count | Number of methods defined in the class. |
| Field count | Number of fields defined in the class. |
| WMC | Weighted Methods per Class: sum of cyclomatic complexities of all methods. |
| LCOM4 | Lack of Cohesion of Methods (Hitz-Montazeri): number of connected components in the method-field access graph. 1 = cohesive, >1 = should be split. |
| TCC | Tight Class Cohesion: proportion of directly connected method pairs (sharing field access). 0.0-1.0, higher is more cohesive. |
| CBO | Coupling Between Objects: number of distinct classes this class is coupled to. |
| Maintainability index | Composite score (0-100) combining Halstead Volume, cyclomatic complexity, and LOC. Higher is more maintainable. |

