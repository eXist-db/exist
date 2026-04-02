# Run OpenRewrite recipes against real repositories

## Arguments

$ARGUMENTS - Optional arguments:
1. Recipe source: path to Java file (development mode) or GAV coordinates like `org.openrewrite:rewrite-migrate-java:LATEST` (existing recipe mode)
2. Moderne organization name to test against. If not provided, help the user select one.
3. `--use-release` - Force using the installed release version of the CLI instead of a newer local build.

## Instructions

### Step 0: Check Plugin Version

Before proceeding, verify Moderne skills are up to date:
1. Run `<CLI> --version` to get the CLI version (e.g., "v3.57.0"), stripping any leading "v"
2. Read `~/.claude/marketplaces/moderne/moderne/.claude-plugin/plugin.json` and extract the `version` field
3. If the versions don't match (or plugin.json doesn't exist), run `<CLI> config moderne skills update` to sync them

### Step 1: Determine CLI Command

Use `mod` as the CLI command. The wrapper automatically prefers the newest JAR from Maven Local or the remote distribution. Store the CLI command for subsequent steps.

### Step 2: Determine Recipe Mode

**Development mode** (local recipe):
- Use when: User provides a path to a Java file, or is actively editing a recipe
- Setup: `<CLI> config recipes active set <RECIPE_PATH>`
- Requires: Compile step before running

**Existing recipe mode** (pre-built):
- Use when: User provides GAV coordinates (e.g., `org.openrewrite:rewrite-migrate-java:LATEST`)
- Setup: `<CLI> config recipes jar install <GAV>`
- No compile needed

If neither is provided, check for a Java file open in the editor or ask the user.

### Step 3: Setup Working Set

If the `working-set` directory does not exist in the recipe project:

1. Run `<CLI> config moderne organizations show --json` and help select an organization:
   - **Development (10-50 repos)**: Ideal for iterative testing
   - **Broad testing (50-100 repos)**: Good coverage, still manageable
   - **Large (100+ repos)**: Warn about cost and time

2. Ensure working set directories are gitignored: `grep -q "working-set" .gitignore || echo "working-set*/" >> .gitignore`

3. Sync the organization:
   - **For orgs < 100 repos**: Use `--with-sources` by default to enable pre-analysis
   - **For orgs 100+ repos**: Ask user before adding `--with-sources` (expensive)

   ```bash
   <CLI> git sync moderne working-set --organization "<ORGANIZATION>" --with-sources
   ```

### Step 4: Pre-Analysis (when sources available)

Before running the recipe, analyze source code to set expectations:

1. **Identify target patterns**: What code patterns does this recipe look for?
   - For method matchers: grep for the method name
   - For type changes: grep for the old type
   - For annotation changes: grep for the annotation

2. **Search working-set sources**:
   ```bash
   grep -r "targetPattern" working-set/*/src --include="*.java" -l
   ```

3. **Record predictions**: Note which repositories and files you expect to be affected

4. **Prune irrelevant repos (optional)**: If many repos clearly don't contain target patterns, delete those folders from `working-set/` to speed up runs

This creates testable hypotheses for diagnosing recipe behavior.

### Step 5: Build LSTs for the Working Set

Before running a recipe, the working set repositories must have LSTs (Lossless Semantic Trees) built. Check whether LSTs already exist:

```bash
# Check if any repos have been built
ls working-set/*/*/*/.moderne/build/*/lst.* 2>/dev/null | head -5
```

If no LSTs exist, build them:

```bash
<CLI> build working-set --no-download --streaming
```

**Notes:**
- Building is sequential per repo and can take several minutes for large working sets.
- `--no-download` forces local builds instead of attempting to download pre-built LSTs from Moderne (which requires a Moderne connection).
- `--streaming` shows progress as repos complete: `+` = success, `!` = build error.
- Some repos may fail to build due to missing dependencies or build tool issues. This is normal — the recipe will still run against repos that built successfully.
- If all repos show `runOutcome=Skipped` in the trace CSV after a recipe run, it means LSTs were not built. Come back to this step.

### Step 6: Compile and Install Recipe

1. **Compile and publish to Maven local:**
   - For Gradle: `./gradlew publishToMavenLocal`
   - For Maven: `mvn install`

2. **Install the recipe JAR:**
   ```bash
   <CLI> config recipes jar install <groupId>:<artifactId>:<version>
   ```
   For example: `<CLI> config recipes jar install com.yourorg:rewrite-recipe-starter:LATEST`

3. **Set the active recipe:**
   ```bash
   <CLI> config recipes active set <RECIPE_PATH>
   ```
   Where `<RECIPE_PATH>` is the path to the recipe source file (`.java` or `.yaml`).

**When iterating**, re-publish and re-install after each change:
```bash
./gradlew publishToMavenLocal && <CLI> config recipes jar install <GAV>
```

### Step 7: Run Recipe

Choose parallelism based on working set size to balance speed and resource usage:
- **1-5 repos:** `--parallel 1` (sequential is fast enough)
- **6-20 repos:** `--parallel 2` (good balance of speed and CPU usage)
- **21+ repos:** `--parallel 4` (diminishing returns above this)

```bash
<CLI> run working-set --active-recipe --streaming --parallel 2
```

**Note:** The default parallelism (cores - 1) can overwhelm systems. `--parallel 2` provides ~40% faster execution than sequential while keeping CPU usage manageable for agent workloads.

### Step 8: Monitor Run and Process Output

**Monitor Progress:**
The run creates output in `.moderne/run/{runId}/` for each repository. The trace CSV is updated as each repository completes, regardless of whether there are changes:
- Watch progress: `tail -f .moderne/run/{runId}/trace.csv`
- Key columns: `outcome` (SUCCESS/FAILED), `filesWithFixResults`, `filesWithSearchResults`, `dataTables`
- **Early termination:** If you expect changes but see multiple repositories completing with `filesWithFixResults=0` and `filesWithSearchResults=0`, terminate the run early (Ctrl+C) to investigate the recipe logic before wasting time on the full working set.

**Parse Streaming Output:**
When using `--streaming`, lines are printed only when there are results:
- `+<path>,<offsets>` = modified file with search markers
- `*<path>` = patch file created
- `!<path>` = error log

**Run Output Directory Structure:**
```
.moderne/run/{runId}/
├── trace.json          # Run metadata (outcome, timing, counts)
├── trace.csv           # Aggregated trace for all repos
├── run.log             # Execution log
├── fix.patch           # Unified diff of all changes
├── changes.json        # File change metadata with markers
├── datatables/         # Data table outputs
│   └── {name}.csv.gz   # Compressed CSV per data table
```

**Understanding changes.json:**
Contains an array of file changes with metadata:
```json
[
  {
    "beforePath": "src/Main.java",
    "afterPath": "src/Main.java",
    "changeType": "MODIFY",
    "markers": [...]
  }
]
```
- `beforePath`: Original path (null for new files)
- `afterPath`: New path (null for deletions, differs for renames)
- `changeType`: ADD, MODIFY, DELETE, or RENAME
- `markers`: SearchResult markers with position information

**Accessing Data Tables:**
Data tables are stored as gzip-compressed CSVs:
- Location: `.moderne/run/{runId}/datatables/{dataTableName}.csv.gz`
- Headers include: `repositoryOrigin`, `repositoryPath`, `repositoryBranch`, plus data table columns
- Read with: `zcat .moderne/run/{runId}/datatables/*.csv.gz | head`
- Or use: `<CLI> study working-set --data-table <Name> --last-recipe-run --csv`

### Step 9: Compare Results vs Predictions

When pre-analysis was done in Step 4:

1. **Check predicted repos**: Do the repos you expected to change have changes?

2. **If expected changes are missing**:
   - Read the source file that should have matched
   - Verify the pattern exists as expected
   - Check recipe matchers (MethodMatcher patterns, type names)
   - Review visitor logic (are you calling `super.visitX()`?)

3. **If there are unexpected changes**:
   - Examine the additional files
   - Understand why the recipe matched there
   - May reveal broader applicability or overly broad matchers

4. **Report findings**: Summarize what matched, what didn't, and why

### Step 10: Iterate (development mode)

If recipe needs fixes:
1. Edit recipe code based on diagnosis
2. Recompile (`./gradlew jar` or `mvn compile`)
3. Re-run (active recipe config persists)
4. Repeat until predictions match results

## Common Mistakes to Avoid

- **Do not read LST files.** The `.moderne/` directory contains serialized LST (Lossless Semantic Tree) files that are not designed to be human or machine readable. Attempting to parse or analyze these files is wasted effort.
- **Do not hypothesize about missing types.** LSTs downloaded from Moderne have complete type information. If a recipe isn't matching, the issue is almost always in the recipe's visitor logic or matchers, not missing type attribution in the LST.

## Reference

Recipe options: `<CLI> run working-set --active-recipe --streaming -P option=value`

Data tables: `<CLI> study working-set --data-table <Name> --last-recipe-run --csv`

List recent runs: `<CLI> audit runs list`

Read trace: `cat .moderne/run/{runId}/trace.json | jq .`

Read data tables: `zcat .moderne/run/{runId}/datatables/*.csv.gz | head`

Iterative development: recompile then re-run. Active recipe config persists.

## CLI Version Selection (Moderne Employees Only)

Both `./gradlew :mod:devFatJar` and `./gradlew :mod:fatJar` publish to Maven Local. The `modw` wrapper automatically prefers the Maven Local JAR over the remote distribution when it is newer. Run `mod --version` to see the source (e.g., "from Maven local ~/.m2").
