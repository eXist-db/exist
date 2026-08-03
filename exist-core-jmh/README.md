# exist-core-jmh

JMH micro-benchmarks for `exist-core` (and the Lucene index extension, which several benchmarks here also exercise). Each class exists to make a specific optimization or regression claim defensible with numbers instead of prose — see the class-level Javadoc for the issue/PR each one is a companion to.

## What's benchmarked

| Class | Package | Measures |
|---|---|---|
| `AxisBenchmark` | `org.exist.dom.persistent` | Sibling/non-sibling axis evaluation over three corpus shapes ([#2697](https://github.com/eXist-db/exist/issues/2697)) |
| `PrecedingAxisBenchmark` | `org.exist.xquery` | `preceding::*` position-dependence on a flat 50,000-element document ([#2129](https://github.com/eXist-db/exist/issues/2129)) |
| `ArrowOperatorBenchmark` | `org.exist.xquery` | Overhead of `=>` vs. the equivalent direct call |
| `TypeSubTypeOfBenchmark` | `org.exist.xquery.value` | `Type#subTypeOf(int, int)`, the hot path behind every atomic comparison ([#6322](https://github.com/eXist-db/exist/issues/6322)) |
| `StringJoinBenchmark` | `org.exist.xquery.utils` | Java `String` join strategies |
| `URIUtilsBenchmark` | `org.exist.xquery.utils` | URI utility helpers |
| `LockTableBenchmark` | `org.exist.storage.lock` | Lock table contention |
| `ReindexBenchmark` | `org.exist.storage` | End-to-end `xmldb:reindex()` fast path ([#572](https://github.com/eXist-db/exist/issues/572)) |
| `LucenePhraseQueryBenchmark` | `org.exist.indexing.lucene` | Query-time Lucene phrase lookup over many small documents ([#2812](https://github.com/eXist-db/exist/issues/2812)) |
| `ReindexDeleteStrategyBenchmark` | `org.exist.indexing.lucene` | Reindex-time Lucene delete strategies for mixed document shapes |
| `UtilExpandHighlightingBenchmark` | `org.exist.indexing.lucene` | `util:expand` match-highlighting cost vs. highlighting off, single-hit and batch ([#5738](https://github.com/eXist-db/exist/issues/5738), [#6387](https://github.com/eXist-db/exist/issues/6387)) |

## Running

Build the module first — this also installs a fresh `exist-core` into the local Maven repo so the benchmark picks up your branch's code:

```bash
JAVA_HOME=/path/to/java-21 \
  mvn install -pl exist-core-jmh -am -DskipTests \
  -Ddependency-check.skip=true -Ddocker=false
```

The `package` phase shades an uber-jar with `org.openjdk.jmh.Main` as its entry point (`target/exist-core-jmh-<version>-benchmarks.jar`). Run it directly:

```bash
java -jar exist-core-jmh/target/exist-core-jmh-*-benchmarks.jar AxisBenchmark -wi 3 -i 5 -f 1
```

Useful variants:

- `... -rf json -rff target/jmh-result.json` — machine-readable output (this is what CI feeds to the gh-pages dashboard)
- `... -prof gc` — GC profile
- `... TypeSubTypeOfBenchmark.identical` — filter by regex
- No class filter runs every `@Benchmark` in the jar

`ArrowOperatorBenchmark` needs the *unshaded* classes plus runtime classpath instead of the shaded jar — the shade transformer trips a log4j2 caller-class assertion when booting a `BrokerPool`. See that class's Javadoc for the exact invocation.

## Continuous tracking

[`.github/workflows/ci-benchmarks.yml`](../.github/workflows/ci-benchmarks.yml) runs the full suite weekly (and on manual dispatch) with reduced iteration counts (`-wi 2 -i 5 -f 1` — a trend signal, not a publishable number) and publishes JSON results via [`benchmark-action/github-action-benchmark`](https://github.com/benchmark-action/github-action-benchmark) to the `gh-pages` branch, under `dev/bench/core`. Once GitHub Pages is enabled for this repository, the dashboard is served at `https://exist-db.github.io/exist/dev/bench/core/`.
