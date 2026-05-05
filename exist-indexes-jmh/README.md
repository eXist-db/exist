# exist-indexes-jmh

JMH benchmarks for eXist-db's index modules: ngram, range (plain `range:eq` and combined `range:field-eq`), and lucene full-text.

The benchmarks here exist to make optimization work on FLWOR / where-clause / index-lookup interaction defensible with numbers, rather than prose. They were prompted by the discussion on [PR #6295](https://github.com/eXist-db/exist/pull/6295), which reverts the per-index `dependsOnLocalVar` guards from PRs #6093 and #6286 in favour of fixing the underlying issue ([GH-2204](https://github.com/eXist-db/exist/issues/2204)) at the FLWOR-clause level.

## What the benchmarks measure

Four classes &mdash; one per index API &mdash; each running the same four shapes against the same Hamlet corpus (~270 KB, ~4000 `LINE` elements, ~1100 `SPEAKER` elements):

| Class | Function | Path |
|---|---|---|
| `NgramWhereClauseBenchmark` | `ngram:contains(LINE, $term)` | ngram index on `LINE` |
| `RangeEqWhereClauseBenchmark` | `range:eq(SPEAKER, $term)` | plain range index on `SPEAKER` &mdash; the path PR #6093 originally guarded |
| `RangeFieldEqWhereClauseBenchmark` | `range:field-eq('speaker', $term)` | combined range index with `<field name="speaker" match="SPEAKER"/>` on `SPEECH` &mdash; the path PR #6093 also covered |
| `LuceneWhereClauseBenchmark` | `ft:query(LINE, $term)` | lucene index on `LINE` &mdash; the path PR #6286 originally guarded |

Each class has the same four `@Benchmark` methods:

| Shape | Form | Path it should take |
|---|---|---|
| `shapeALiteral` | `INDEX_FN(NODES, 'literal')` | Single bulk index probe |
| `shapeALetVar` | `let $q := 'literal' return INDEX_FN(NODES, $q)` | Single bulk index probe |
| `shapeBForVarPredicate` | `for $q in $TERMS return INDEX_FN(NODES, $q)` | One bulk probe per outer iteration &mdash; the rewrite target |
| `shapeBForVarWhere` | `for $q in $TERMS where INDEX_FN(NODES, $q) return $q` | Currently per-tuple EBV in the regression case |

Shape A reads should be flat (literal vs. let-bound shouldn't differ measurably). The interesting comparison is **`shapeBForVarPredicate` vs. `shapeBForVarWhere`**: any optimizer rewrite that turns the latter into the former is the win Juri's PR is angling toward.

## Running

Build the module (the `micro-benchmarks` profile is active by default; the install step puts a fresh `exist-core` into the local Maven repo so the benchmark picks up your branch's code):

```bash
JAVA_HOME=/path/to/java-21 \
  mvn install -pl exist-indexes-jmh -am -DskipTests \
  -Ddependency-check.skip=true -Ddocker=false
```

Then run via the wrapper script:

```bash
cd exist-indexes-jmh
./bin/run-bench.sh NgramWhereClauseBenchmark
```

The script materialises the runtime classpath from `target/classpath.txt` (emitted at package time) and invokes `org.openjdk.jmh.Main`. Any extra arguments are passed through to JMH:

- `./bin/run-bench.sh NgramWhereClauseBenchmark -wi 1 -i 2 -f 1` — quick smoke check (~1 minute)
- `./bin/run-bench.sh NgramWhereClauseBenchmark -wi 5 -i 10 -f 3` — publishable numbers
- `./bin/run-bench.sh NgramWhereClauseBenchmark -prof gc` — add GC pressure stats
- `./bin/run-bench.sh NgramWhereClauseBenchmark -rf json -rff result.json` — machine-readable output
- `./bin/run-bench.sh "NgramWhereClauseBenchmark.shapeA_.*"` — filter by regex

> **Why a wrapper script and not a shaded jar?** Embedding eXist's `BrokerPool` inside a shade-plugin uberjar collides with Saxon's `ServiceLoader` and similar libraries — running off the dependency-plugin classpath sidesteps that entirely.

## Baseline numbers (2026-05-05)

`-wi 1 -i 2 -f 1` on a single laptop, illustrative only &mdash; re-run with longer parameters for anything publishable. The corpus is the three plays loaded together (~660 KB).

### develop @ b917e1ab1d (with #6093 + #6286 guards in place)

```
Benchmark                                               (termCount)  Mode  Cnt      Score  Units
NgramWhereClauseBenchmark.shapeALiteral                           5  avgt    2      0.128  ms/op
NgramWhereClauseBenchmark.shapeALiteral                          50  avgt    2      0.129  ms/op
NgramWhereClauseBenchmark.shapeALiteral                         100  avgt    2      0.132  ms/op
NgramWhereClauseBenchmark.shapeALetVar                            5  avgt    2      0.155  ms/op
NgramWhereClauseBenchmark.shapeALetVar                           50  avgt    2      0.156  ms/op
NgramWhereClauseBenchmark.shapeALetVar                          100  avgt    2      0.155  ms/op
NgramWhereClauseBenchmark.shapeBForVarPredicate                   5  avgt    2      0.640  ms/op
NgramWhereClauseBenchmark.shapeBForVarPredicate                  50  avgt    2      5.343  ms/op
NgramWhereClauseBenchmark.shapeBForVarPredicate                 100  avgt    2     10.573  ms/op
NgramWhereClauseBenchmark.shapeBForVarWhere                       5  avgt    2   1531.542  ms/op
NgramWhereClauseBenchmark.shapeBForVarWhere                      50  avgt    2  17328.208  ms/op
NgramWhereClauseBenchmark.shapeBForVarWhere                     100  avgt    2  35190.427  ms/op
RangeEqWhereClauseBenchmark.shapeBForVarPredicate                 5  avgt    2      0.817  ms/op
RangeEqWhereClauseBenchmark.shapeBForVarPredicate               100  avgt    2     14.558  ms/op
RangeEqWhereClauseBenchmark.shapeBForVarWhere                     5  avgt    2      0.838  ms/op
RangeEqWhereClauseBenchmark.shapeBForVarWhere                   100  avgt    2     14.927  ms/op
RangeFieldEqWhereClauseBenchmark.shapeBForVarPredicate            5  avgt    2      0.207  ms/op
RangeFieldEqWhereClauseBenchmark.shapeBForVarPredicate          100  avgt    2      2.150  ms/op
RangeFieldEqWhereClauseBenchmark.shapeBForVarWhere                5  avgt    2      0.197  ms/op
RangeFieldEqWhereClauseBenchmark.shapeBForVarWhere              100  avgt    2      2.225  ms/op
LuceneWhereClauseBenchmark.shapeBForVarPredicate                  5  avgt    2      2.355  ms/op
LuceneWhereClauseBenchmark.shapeBForVarPredicate                100  avgt    2     45.440  ms/op
LuceneWhereClauseBenchmark.shapeBForVarWhere                      5  avgt    2      2.384  ms/op
LuceneWhereClauseBenchmark.shapeBForVarWhere                    100  avgt    2     45.938  ms/op
```

### develop + cherry-picked #6295 (line-o:perf/reinstate-batch-operations @ b064cddafc)

```
Benchmark                                               (termCount)  Mode  Cnt    Score  Units
NgramWhereClauseBenchmark.shapeALiteral                           5  avgt    2    0.127  ms/op
NgramWhereClauseBenchmark.shapeALetVar                            5  avgt    2    0.143  ms/op
NgramWhereClauseBenchmark.shapeBForVarPredicate                   5  avgt    2    0.529  ms/op
NgramWhereClauseBenchmark.shapeBForVarPredicate                  50  avgt    2    4.324  ms/op
NgramWhereClauseBenchmark.shapeBForVarPredicate                 100  avgt    2    8.333  ms/op
NgramWhereClauseBenchmark.shapeBForVarWhere                       5  avgt    2    3.177  ms/op
NgramWhereClauseBenchmark.shapeBForVarWhere                      50  avgt    2   29.781  ms/op
NgramWhereClauseBenchmark.shapeBForVarWhere                     100  avgt    2   58.730  ms/op
RangeEqWhereClauseBenchmark.shapeBForVarPredicate               100  avgt    2   13.841  ms/op
RangeEqWhereClauseBenchmark.shapeBForVarWhere                   100  avgt    2   14.697  ms/op
RangeFieldEqWhereClauseBenchmark.shapeBForVarPredicate          100  avgt    2    2.124  ms/op
RangeFieldEqWhereClauseBenchmark.shapeBForVarWhere              100  avgt    2    2.129  ms/op
LuceneWhereClauseBenchmark.shapeBForVarPredicate                100  avgt    2   44.965  ms/op
LuceneWhereClauseBenchmark.shapeBForVarWhere                    100  avgt    2   45.330  ms/op
```

### What the comparison shows

| Benchmark / shape (termCount=100) | develop | +#6295 | Delta |
|---|---|---|---|
| Ngram shapeBForVarWhere | **35190 ms** | **58.7 ms** | **~600x faster** |
| Ngram shapeBForVarPredicate | 10.57 ms | 8.33 ms | ~unchanged |
| RangeEq shapeBForVarWhere | 14.93 ms | 14.70 ms | unchanged |
| RangeFieldEq shapeBForVarWhere | 2.23 ms | 2.13 ms | unchanged |
| Lucene shapeBForVarWhere | 45.94 ms | 45.33 ms | unchanged |

Three things to read from this:

1. **#6295 produces a real, ngram-only perf win.** The ngram where-clause shape goes from 35 seconds at termCount=100 to 59 ms &mdash; a ~600x speedup. Shape A, predicate form, and all other indexes are unaffected.
2. **The per-index guards reverted by #6295 were not load-bearing for range or lucene.** Their numbers are identical pre- and post-#6295. The guards on `range:eq`, `range:field-eq`, and `ft:query` were defensive against a bug that doesn't currently surface on those indexes &mdash; reverting them costs nothing visible.
3. **On ngram specifically, #6295 trades correctness for speed.** The `for-variable-in-where-clause` test that #6286 added was hitting the slow-but-correct path; #6295 marks it `%test:pending` and accepts wrong results in exchange for the ~600x speedup. That's the trade Juri is asking us to consider.

Term-list scaling is linear across every shape we measured (5x or 20x increase in termCount produces ~5x or ~20x in time). That confirms there's no hidden quadratic in any of these paths.

If a future change makes `shapeALetVar` regress toward `shapeBForVarWhere`, the bulk-probe path has been broken on Shape A &mdash; the situation #6295's revert is meant to prevent.

If a future optimizer collapses `shapeBForVarWhere` to `shapeBForVarPredicate` for ngram *while keeping correctness*, the GH-2204 regression is fixed at the right layer &mdash; the goal of Lever 1 in the where-clause optimization tasking.

## Layout

```
exist-indexes-jmh/
├── pom.xml
├── README.md
├── bin/
│   └── run-bench.sh
└── src/main/
    ├── java/org/exist/indexing/jmh/
    │   ├── NgramWhereClauseBenchmark.java
    │   ├── RangeEqWhereClauseBenchmark.java
    │   ├── RangeFieldEqWhereClauseBenchmark.java
    │   └── LuceneWhereClauseBenchmark.java
    └── resources/
        ├── conf.xml                              (extended from ngram test resources to register all three indexes)
        └── org/exist/indexing/jmh/
            ├── hamlet.xml                        (vendored from exist-samples/.../shakespeare/)
            ├── macbeth.xml                       (vendored from exist-samples/.../shakespeare/)
            ├── r_and_j.xml                       (vendored from exist-samples/.../shakespeare/)
            └── collection.xconf                  (ngram + range + range field + lucene)
```

## Roadmap

Planned follow-ups (not in this PR):

- Larger corpus via on-demand XMark generation (`xmlgen -f 0.1 / 1 / 10`)
- CI nightly job posting numbers to a tracking issue
- `range:eq` / `range:field-eq` family extensions (`range:gt`, `range:starts-with`, etc.) if regressions turn out to be operator-specific
- Diagnostics that explain *why* range, range-field, and lucene are immune to the bug ngram exhibits &mdash; the surface explanation ("their `getDependencies()` returns the right thing") is true but not actionable; the underlying difference would inform the GH-2204 fix design
