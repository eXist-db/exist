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

## Baseline numbers (develop @ b917e1ab1d, 2026-05-05)

Quick run (`-wi 1 -i 2 -f 1`) on a single laptop, illustrative only &mdash; re-run with longer parameters for anything publishable:

```
Benchmark                                               Mode  Cnt    Score   Error  Units
NgramWhereClauseBenchmark.shapeALiteral                 avgt    2    0.118          ms/op
NgramWhereClauseBenchmark.shapeALetVar                  avgt    2    0.140          ms/op
NgramWhereClauseBenchmark.shapeBForVarPredicate         avgt    2    0.345          ms/op
NgramWhereClauseBenchmark.shapeBForVarWhere             avgt    2  928.391          ms/op
RangeEqWhereClauseBenchmark.shapeALiteral               avgt    2    0.192          ms/op
RangeEqWhereClauseBenchmark.shapeALetVar                avgt    2    0.200          ms/op
RangeEqWhereClauseBenchmark.shapeBForVarPredicate       avgt    2    0.479          ms/op
RangeEqWhereClauseBenchmark.shapeBForVarWhere           avgt    2    0.488          ms/op
RangeFieldEqWhereClauseBenchmark.shapeALiteral          avgt    2    0.138          ms/op
RangeFieldEqWhereClauseBenchmark.shapeALetVar           avgt    2    0.143          ms/op
RangeFieldEqWhereClauseBenchmark.shapeBForVarPredicate  avgt    2    0.213          ms/op
RangeFieldEqWhereClauseBenchmark.shapeBForVarWhere      avgt    2    0.206          ms/op
LuceneWhereClauseBenchmark.shapeALiteral                avgt    2    0.302          ms/op
LuceneWhereClauseBenchmark.shapeALetVar                 avgt    2    0.304          ms/op
LuceneWhereClauseBenchmark.shapeBForVarPredicate        avgt    2    1.172          ms/op
LuceneWhereClauseBenchmark.shapeBForVarWhere            avgt    2    1.114          ms/op
```

The story:

- **Shape A is flat across all four indexes.** Literal and let-bound paths are equivalent.
- **`shapeBForVarPredicate` is roughly N times Shape A** (where N=5, the size of the term list), exactly as expected from "one bulk probe per outer iteration."
- **The shapeBForVarWhere regression is ngram-only**, at least on develop today. Ngram's where-clause shape is **roughly 8,000x slower** than its predicate form. Range, range-field, and lucene all show no measurable regression &mdash; their `shapeBForVarWhere` and `shapeBForVarPredicate` numbers are within noise of each other.

That asymmetry is itself a finding: GH-2204 may be specific to ngram's interaction with the FLWOR optimizer, rather than a generic FLWOR/where-clause bug. Range and lucene's `getDependencies()` paths handle the loop-variable case correctly already &mdash; meaning the per-index guards reverted by #6295 were partly preventing a bug that only ngram actually exhibits. Worth confirming before any FLWOR-level rewrite is designed: maybe the right fix is a smaller-scoped one to ngram.

If a future change makes `shapeALetVar` regress toward `shapeBForVarWhere`, the bulk-probe path has been broken (the situation that motivated the revert in #6295).

If a future optimizer collapses `shapeBForVarWhere` to `shapeBForVarPredicate` for ngram, the GH-2204 regression is fixed &mdash; the goal of Lever 1 in the where-clause optimization tasking.

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
            └── collection.xconf                  (ngram + range + range field + lucene)
```

## Roadmap

Planned follow-ups (not in this PR):

- Larger corpus via on-demand XMark generation (`xmlgen -f 0.1 / 1 / 10`) &mdash; the current Hamlet corpus is small enough that the difference between bulk and per-tuple paths is dramatic on ngram but may not surface on lucene/range until larger documents amplify the pattern
- CI nightly job posting numbers to a tracking issue
- `range:eq` / `range:field-eq` family extensions (`range:gt`, `range:starts-with`, etc.) if the regression turns out to be operator-specific
- A proper baseline branch (run benchmark on `develop` *with* #6093 + #6286 still in place) to confirm the per-index guards didn't change shape A's bulk-probe path measurably
