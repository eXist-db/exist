# eXist-db W3C XQTS

This module assembles the [exist-xqts-runner](https://github.com/eXist-db/exist-xqts-runner)
together with the `exist-core` of this source tree, so that the W3C XQuery Test
Suite can be executed against the current state of eXist-db.

## Running the test suite

Build the module, then run the generated script with the desired options:

```bash
mvn -pl exist-xqts --also-make clean package -DskipTests -Ddependency-check.skip=true
find exist-xqts/target -name exist-xqts-runner.sh \
    -exec {} --xqts-version HEAD --output-dir /tmp/xqts-output \;
```

The `--output-dir` folder is the runner's *result folder*: it contains the
JUnit reports under `junit/data/*.xml` and the runner's build metadata in
`runner-info.xml`.

## Comparing two runs: the `xqts-compare` goal

The `xqts-compare` goal compares the result folders of two runs — for example
the run of a pull request against a baseline run of `develop`, or two local
runs before and after a change. It is not bound to any lifecycle phase and only
runs when invoked explicitly:

```bash
mvn -pl exist-xqts exec:exec@xqts-compare \
    -Dprevious-result=/path/to/baseline-output \
    -Dcurrent-result=/path/to/current-output
```

| Property | Required | Description |
| :--- | :--- | :--- |
| `previous-result` | yes | Result folder of the baseline run (absolute path) |
| `current-result` | yes | Result folder of the run to compare against the baseline (absolute path) |
| `comparison-output-dir` | no | Where to write the outputs; defaults to `exist-xqts/target/xqts-comparison` |
| `previous-label` | no | Display name of the baseline run in the Markdown report; defaults to `previous` |
| `current-label` | no | Display name of the compared run in the Markdown report; defaults to `current` |

The goal makes no assumption about where the two result folders come from; any
two folders produced by the runner's `--output-dir` can be compared. It fails
with an actionable message if either parameter is missing or does not point to
a comparable result folder.

Two files are written to `comparison-output-dir`:

- `comparison-results.xml` — the comparison data: totals, deltas, the newly
  passing/failing/erroring/skipped test cases, and warnings when the two runs'
  `runner-info.xml` metadata indicates environment drift
  (see [#6326](https://github.com/eXist-db/exist/issues/6326));
- `comparison-results.md` — the same information rendered as GitHub-flavoured
  Markdown, as posted on pull requests by the `ci-xqts-comment.yml` workflow.

The comparison needs this module's dependencies (Saxon, via `exist-core`) to be
resolvable, so run `mvn -pl exist-xqts --also-make install ...` once before
invoking the goal on a fresh clone.
