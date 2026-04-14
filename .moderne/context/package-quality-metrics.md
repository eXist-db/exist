# Package Quality Metrics

## Per-package coupling, stability, and dependency cycle analysis

Per-package architectural metrics including afferent/efferent coupling, instability, abstractness, distance from main sequence, and dependency cycle detection. Use this to identify architectural decay and circular dependencies.

## Data Tables

### Package quality metrics

**File:** [`package-quality-metrics.csv`](package-quality-metrics.csv)

Per-package architectural metrics including afferent/efferent coupling, instability, abstractness, distance from main sequence, and dependency cycle membership.

| Column | Description |
|--------|-------------|
| Package name | The fully qualified package name. |
| Afferent coupling (Ca) | Number of external packages that depend on this package. |
| Efferent coupling (Ce) | Number of external packages this package depends on. |
| Instability | Ce / (Ce + Ca). 0.0 = maximally stable, 1.0 = maximally unstable. |
| Abstractness | Ratio of abstract classes + interfaces to total classes in the package. |
| Distance from main sequence | |A + I - 1|. 0.0 = ideal balance, high = Zone of Pain or Zone of Uselessness. |
| In cycle | Whether this package is part of a dependency cycle. |
| Cycle members | Comma-separated list of packages in the same dependency cycle, or null if not in a cycle. |

