# exist-indexes-jmh

JMH benchmarks for eXist-db's index modules (ngram, range, lucene -- the latter two arrive in follow-up PRs).

The benchmarks here exist to make optimization work on FLWOR / where-clause / index-lookup interaction defensible with numbers, rather than prose. They were prompted by the discussion on [PR #6295](https://github.com/eXist-db/exist/pull/6295), which reverts the per-index `dependsOnLocalVar` guards from PRs #6093 and #6286 in favour of fixing the underlying issue ([GH-2204](https://github.com/eXist-db/exist/issues/2204)) at the FLWOR-clause level.

## What the benchmarks measure

`NgramWhereClauseBenchmark` runs four query shapes against Hamlet (~270 KB, ~4000 `LINE` elements), with an ngram index on `LINE`:

| Benchmark | Form | Path it should take |
|---|---|---|
| `shapeALiteral` | `//LINE[ngram:contains(., 'Denmark')]` | Single bulk index probe |
| `shapeALetVar` | `let $q := 'Denmark' return //LINE[ngram:contains(., $q)]` | Single bulk index probe |
| `shapeBForVarPredicate` | `for $q in $TERMS return //LINE[ngram:contains(., $q)]` | One bulk probe per outer iteration -- the rewrite target |
| `shapeBForVarWhere` | `for $q in $TERMS where //LINE[ngram:contains(., $q)] return $q` | Currently per-tuple EBV -- the regression case |

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

## Baseline numbers (develop @ b917e1ab1d, 2026-05-04)

Quick run (`-wi 1 -i 2 -f 1`) on a single laptop, illustrative only — re-run with longer parameters for anything publishable:

```
Benchmark                                        Mode  Cnt    Score   Error  Units
NgramWhereClauseBenchmark.shapeALiteral          avgt    2    0.117          ms/op
NgramWhereClauseBenchmark.shapeALetVar           avgt    2    0.142          ms/op
NgramWhereClauseBenchmark.shapeBForVarPredicate  avgt    2    0.358          ms/op
NgramWhereClauseBenchmark.shapeBForVarWhere      avgt    2  850.163          ms/op
```

The story:

- Shape A is flat (~0.12 ms): literal and let-bound paths are equivalent, both single bulk probes.
- `shapeBForVarPredicate` is ~3x Shape A — five invariant bulk probes, one per term, exactly as expected.
- `shapeBForVarWhere` is **roughly 6,000x slower** than the predicate form — the where-clause path collapses into per-tuple EBV evaluation. This is the regression case from [GH-2204](https://github.com/eXist-db/exist/issues/2204).

If `shapeALetVar` regresses toward `shapeBForVarWhere`, the bulk-probe path has been broken (the situation that motivated the revert in #6295).

If `shapeBForVarWhere` collapses to `shapeBForVarPredicate`, an optimizer has successfully rewritten the where-clause shape into predicate form -- the goal of Lever 1 in the where-clause optimization tasking.

## Layout

```
exist-indexes-jmh/
├── pom.xml
├── README.md
├── bin/
│   └── run-bench.sh
└── src/main/
    ├── java/org/exist/indexing/jmh/
    │   └── NgramWhereClauseBenchmark.java
    └── resources/
        ├── conf.xml                              (vendored from extensions/indexes/ngram test resources)
        └── org/exist/indexing/jmh/
            ├── hamlet.xml                        (vendored from exist-samples/.../shakespeare/)
            └── collection.xconf                  (ngram on LINE and SPEAKER)
```

## Roadmap

This is the v1 PR. Planned follow-ups:

- Range-index benchmark (`range:eq`, `range:field-eq` -- the path PR #6093 originally guarded)
- Lucene benchmark (`ft:query` -- the path PR #6286 originally guarded)
- Larger corpus via on-demand XMark generation (`xmlgen -f 0.1 / 1 / 10`)
- CI nightly job posting numbers to a tracking issue
