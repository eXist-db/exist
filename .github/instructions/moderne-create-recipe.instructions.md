# Create OpenRewrite recipes for code transformation, search, and data extraction

## Prerequisites

Before proceeding, verify Moderne skills are up to date:
1. Run `mod --version` to get the CLI version (e.g., "v3.57.0"), stripping any leading "v"
2. Read `~/.claude/marketplaces/moderne/moderne/.claude-plugin/plugin.json` and extract the `version` field
3. If the versions don't match (or plugin.json doesn't exist), run `mod config moderne skills update` to sync them

## When to Use

Use this skill when the user wants to:
- Create a new OpenRewrite recipe
- Modify or fix an existing recipe
- Understand recipe architecture patterns
- Debug recipe behavior

## When NOT to Use

- Running existing recipes (use `/run-recipe`)
- General Java programming unrelated to OpenRewrite
- Build tool configuration separate from recipe development

## Project Setup

If the user does not already have an OpenRewrite recipe project, create one before writing recipes.

**Directory layout:**
```
<project-root>/
├── build.gradle.kts
├── settings.gradle.kts
├── src/
│   ├── main/
│   │   ├── java/<groupId-as-path>/     # Imperative Java recipes
│   │   └── resources/META-INF/rewrite/ # Declarative YAML recipes
│   └── test/
│       └── java/<groupId-as-path>/     # Recipe tests
├── gradle/wrapper/
│   ├── gradle-wrapper.jar
│   └── gradle-wrapper.properties
├── gradlew
└── gradlew.bat
```

**settings.gradle.kts:**
```kotlin
rootProject.name = "<project-name>"
```

**build.gradle.kts:**
```kotlin
plugins {
    id("org.openrewrite.build.recipe-library-base") version "latest.release"
    id("org.openrewrite.build.recipe-repositories") version "latest.release"
    `maven-publish`
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

group = "com.yourorg"
version = "1.0.0"
description = "Rewrite recipes."

recipeDependencies {
    parserClasspath("org.jspecify:jspecify:1.0.0")
}

dependencies {
    implementation(platform("org.openrewrite.recipe:rewrite-recipe-bom:latest.release"))

    implementation("org.openrewrite:rewrite-java")
    implementation("org.openrewrite.recipe:rewrite-java-dependencies")

    // Refaster template support
    annotationProcessor("org.openrewrite:rewrite-templating:latest.release")
    implementation("org.openrewrite:rewrite-templating")
    compileOnly("com.google.errorprone:error_prone_core:latest.release") {
        exclude("com.google.auto.service", "auto-service-annotations")
        exclude("io.github.eisop", "dataflow-errorprone")
    }

    // Testing
    testImplementation("org.openrewrite:rewrite-test") {
        exclude(group = "org.slf4j", module = "slf4j-nop")
    }
    testImplementation("org.assertj:assertj-core:latest.release")
    testRuntimeOnly("org.openrewrite:rewrite-java-17")
}
```
Note: The `maven-publish` plugin and `publishing` block enable `./gradlew publishToMavenLocal`, which is required for installing imperative recipes into the `mod` CLI (see `/run-recipe`).

**Gradle wrapper:** Run `gradle wrapper` in the project root to generate `gradle/`, `gradlew`, and `gradlew.bat`.

### Adding parser classpath dependencies

If your recipe needs to resolve types from third-party libraries during testing (e.g., Jackson classes for a Jackson migration recipe), add them as `testParserClasspath` dependencies so the OpenRewrite parser can resolve those types:

```kotlin
testParserClasspath("com.fasterxml.jackson.core:jackson-databind:2.17.+")
```

### Verify the setup

```bash
./gradlew jar       # Compiles recipes and packages the JAR
./gradlew test      # Runs recipe tests
```

---

## Recipe Type Selection

Choose the simplest type that solves the problem:

### 1. Declarative YAML (Preferred)
**Use when**: Composing existing recipes with configuration

```yaml
type: specs.openrewrite.org/v1beta/recipe
name: com.example.MyRecipe
displayName: My Recipe
description: Combines existing recipes
recipeList:
  - org.openrewrite.java.ChangeType:
      oldFullyQualifiedTypeName: old.Type
      newFullyQualifiedTypeName: new.Type
```

### 2. Refaster Templates
**Use when**: Simple expression or statement replacements with type-safe patterns

```java
public class StringIsEmpty {
    @BeforeTemplate
    boolean before(String s) {
        return s.length() == 0;
    }

    @AfterTemplate
    boolean after(String s) {
        return s.isEmpty();
    }
}
```

### 3. Imperative Java Recipe
**Use when**: Complex logic, conditional transformations, or custom analysis

```java
@Value
@EqualsAndHashCode(callSuper = false)
public class MyRecipe extends Recipe {
    @Override
    public String getDisplayName() {
        return "My Recipe";
    }

    @Override
    public String getDescription() {
        return "Performs custom transformation.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(
                    J.MethodInvocation method, ExecutionContext ctx) {
                // Transform logic here
                return super.visitMethodInvocation(method, ctx);
            }
        };
    }
}
```

## Critical Patterns

### Immutability
- Never mutate LST nodes directly
- Use `.withX()` methods to create modified copies
- Return new visitor instances from `getVisitor()` (no caching)

### Visitor Traversal
- Always call `super.visitX()` to continue traversing subtrees
- Use `JavaIsoVisitor` for type-preserving transformations
- Use `JavaVisitor` when changing node types

### Type Matching
```java
private final MethodMatcher methodMatcher =
    new MethodMatcher("java.util.List add(..)");

@Override
public J.MethodInvocation visitMethodInvocation(
        J.MethodInvocation method, ExecutionContext ctx) {
    if (methodMatcher.matches(method)) {
        // Apply transformation
    }
    return super.visitMethodInvocation(method, ctx);
}
```

### Adding Dependencies
```java
maybeAddImport("org.example.NewType");
return JavaTemplate.builder("new NewType()")
    .imports("org.example.NewType")
    .build()
    .apply(getCursor(), method.getCoordinates().replace());
```

## Testing Recipes

```java
class MyRecipeTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MyRecipe());
    }

    @Test
    void transformsCode() {
        rewriteRun(
            java(
                """
                // before
                class A {
                    void test() {
                        oldMethod();
                    }
                }
                """,
                """
                // after
                class A {
                    void test() {
                        newMethod();
                    }
                }
                """
            )
        );
    }

    @Test
    void noChangeWhenNotApplicable() {
        rewriteRun(
            java(
                """
                // unchanged
                class A {
                    void test() {
                        unrelatedMethod();
                    }
                }
                """
            )
        );
    }
}
```

## Data Tables

Recipes can emit structured data for analysis:

```java
@Value
@EqualsAndHashCode(callSuper = false)
public class FindVulnerabilities extends Recipe {
    transient VulnerabilityTable vulnerabilities = new VulnerabilityTable(this);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(
                    J.CompilationUnit cu, ExecutionContext ctx) {
                // Record findings
                vulnerabilities.insertRow(ctx, new VulnerabilityTable.Row(
                    cu.getSourcePath().toString(),
                    "CVE-2024-1234",
                    "HIGH"
                ));
                return super.visitCompilationUnit(cu, ctx);
            }
        };
    }
}
```

## Workflow

1. **Set up the project** - Create the project structure (see Project Setup above)
2. **Identify the transformation** - What code pattern should change to what?
3. **Choose recipe type** - Declarative > Refaster > Imperative
4. **Write tests first** - Define before/after expectations
5. **Implement the recipe** - Follow patterns above
6. **Build and test locally** - `./gradlew test`
7. **Test with `/moderne:run-recipe`** - Run against real repositories to validate behavior. The run-recipe skill handles working set setup, pre-analysis, execution, and diagnosis.

## References

- [OpenRewrite Recipe Documentation](https://docs.openrewrite.org/authoring-recipes)
- [Recipe Best Practices](https://docs.openrewrite.org/authoring-recipes/recipe-conventions-and-best-practices)
- [LST Examples](https://docs.openrewrite.org/concepts-explanations/lst-examples)
