# Create a custom Moderne organization from repositories

## Prerequisites

Before proceeding, verify Moderne skills are up to date:
1. Run `mod --version` to get the CLI version (e.g., "v3.57.0"), stripping any leading "v"
2. Read `~/.claude/marketplaces/moderne/moderne/.claude-plugin/plugin.json` and extract the `version` field
3. If the versions don't match (or plugin.json doesn't exist), run `mod config moderne skills update` to sync them

## When to Use

Use this skill when the user wants to:
- Create a custom organization from repositories they have access to
- Find repositories by programming language
- Find repositories using specific technologies (Spring Boot, databases, etc.)
- Generate a repos.csv file for use with `mod git sync csv`

## repos.csv Format

The repos.csv file defines repositories and their organizational hierarchy:

```csv
origin,path,cloneUrl,branch,org1,org2,org3
github.com,owner/repo1,https://github.com/owner/repo1,main,Team A,Engineering,ALL
github.com,owner/repo2,https://github.com/owner/repo2,,Team B,Engineering,ALL
```

**Required columns:**
- `origin` - Git host (e.g., `github.com`, `gitlab.com`, `bitbucket.org`)
- `path` - Repository path without host (e.g., `owner/repo`)
- `cloneUrl` - Git clone URL (HTTPS or SSH)

**Optional columns:**
- `branch` - Branch to use (defaults to repository's default branch)
- `org1`, `org2`, ... `orgN` - Organizational hierarchy (innermost to outermost)

**Deriving origin and path from cloneUrl:**
- `https://github.com/moderneinc/git-test` → origin=`github.com`, path=`moderneinc/git-test`
- `https://gitlab.com/group/project` → origin=`gitlab.com`, path=`group/project`
- `git@github.com:owner/repo.git` → origin=`github.com`, path=`owner/repo`

**Organization hierarchy:** The `org` columns define nested folders. `org1` is the innermost (most specific), and higher numbers are progressively broader. For example:
- `org1=Team A`, `org2=Engineering`, `org3=ALL` creates: `ALL/Engineering/Team A/`

## Workflow

### Step 1: Determine Repository Source

Ask the user which approach they want:
1. **All accessible repositories** - List everything the user has access to
2. **Repositories by language** - Filter by primary programming language
3. **Repositories by technology** - Search for specific frameworks/libraries

### Step 2: Gather Repositories

#### Option A: List All Repositories (GitHub)

```bash
# List all repositories the user has access to
gh repo list --limit 1000 --json nameWithOwner,url,defaultBranchRef --jq '.[] | ["github.com", .nameWithOwner, .url, .defaultBranchRef.name] | @csv'
```

For organizations:
```bash
# List repositories in a specific GitHub organization
gh repo list <org-name> --limit 1000 --json nameWithOwner,url,defaultBranchRef --jq '.[] | ["github.com", .nameWithOwner, .url, .defaultBranchRef.name] | @csv'
```

#### Option B: Repositories by Language (GitHub)

```bash
# List Java repositories
gh repo list --limit 1000 --language java --json nameWithOwner,url,defaultBranchRef --jq '.[] | ["github.com", .nameWithOwner, .url, .defaultBranchRef.name] | @csv'

# List Python repositories
gh repo list --limit 1000 --language python --json nameWithOwner,url,defaultBranchRef --jq '.[] | ["github.com", .nameWithOwner, .url, .defaultBranchRef.name] | @csv'
```

#### Option C: Repositories by Technology (GitHub Code Search)

Use GitHub code search to find repositories with specific dependencies or patterns:

```bash
# Find Spring Boot repositories (look for spring-boot in pom.xml or build.gradle)
gh search code "spring-boot-starter" --filename pom.xml --json repository --jq '.[].repository | ["github.com", .nameWithOwner, .url] | @csv' | sort -u

# Find repositories using PostgreSQL
gh search code "postgresql" --filename pom.xml --json repository --jq '.[].repository | ["github.com", .nameWithOwner, .url] | @csv' | sort -u

# Find repositories with specific imports
gh search code "import org.springframework.data.jpa" --extension java --json repository --jq '.[].repository | ["github.com", .nameWithOwner, .url] | @csv' | sort -u
```

**Common technology search patterns:**
| Technology | Search Query |
|------------|--------------|
| Spring Boot | `spring-boot-starter filename:pom.xml` or `spring-boot filename:build.gradle` |
| Spring Data JPA | `spring-boot-starter-data-jpa filename:pom.xml` |
| PostgreSQL | `postgresql filename:pom.xml` or `org.postgresql filename:build.gradle` |
| MySQL | `mysql-connector filename:pom.xml` |
| MongoDB | `spring-boot-starter-data-mongodb filename:pom.xml` |
| Kafka | `spring-kafka filename:pom.xml` |
| RabbitMQ | `spring-boot-starter-amqp filename:pom.xml` |
| Redis | `spring-boot-starter-data-redis filename:pom.xml` |
| Hibernate | `hibernate-core filename:pom.xml` |
| JUnit 5 | `junit-jupiter filename:pom.xml` |

#### Option D: Bitbucket Repositories

```bash
# Using Bitbucket CLI (if available)
# Or use Bitbucket REST API directly
curl -u username:app_password "https://api.bitbucket.org/2.0/repositories/{workspace}?pagelen=100" | jq -r '.values[].links.clone[] | select(.name=="https") | .href'
```

#### Option E: GitLab Repositories

```bash
# List all projects the user has access to
glab repo list --per-page 100 | awk '{print $1}'

# Or using GitLab API
curl --header "PRIVATE-TOKEN: <token>" "https://gitlab.com/api/v4/projects?membership=true&per_page=100" | jq -r '.[].http_url_to_repo'
```

#### Option F: GitHub Repository Search

Search for public repositories by keywords, topics, or language:

```bash
# Search by keyword and language
gh search repos "quarkus microservices" --language java --limit 30 --json fullName,url --jq '.[] | ["github.com", .fullName, .url] | @csv'

# Search with star count (useful for finding popular/maintained repos)
gh search repos "spring boot" --language java --limit 50 --json fullName,url,stargazersCount --jq '.[] | select(.stargazersCount > 100) | ["github.com", .fullName, .url] | @csv'

# Find repositories tagged with a specific topic
gh search repos --topic spring-boot --limit 100 --json fullName,url --jq '.[] | ["github.com", .fullName, .url] | @csv'

# Combine topic with language filter
gh search repos --topic spring-boot --language java --limit 100 --json fullName,url --jq '.[] | ["github.com", .fullName, .url] | @csv'

# Multiple topics (AND)
gh search repos --topic spring-boot --topic microservices --json fullName,url --jq '.[] | ["github.com", .fullName, .url] | @csv'
```

**Available JSON fields for `gh search repos`:**
`createdAt`, `defaultBranch`, `description`, `forksCount`, `fullName`, `hasDownloads`, `hasIssues`, `hasPages`, `hasProjects`, `hasWiki`, `homepage`, `id`, `isArchived`, `isDisabled`, `isFork`, `isPrivate`, `language`, `license`, `name`, `openIssuesCount`, `owner`, `pushedAt`, `size`, `stargazersCount`, `updatedAt`, `url`, `visibility`, `watchersCount`

#### Option G: Additional Technographic Sources

**Sourcegraph (public code search):**
```bash
# Search across public repositories (via web or API)
# https://sourcegraph.com/search?q=context:global+file:pom.xml+spring-boot-starter-data-jpa
```

**Libraries.io (dependency discovery):**
```bash
# Find repositories depending on a specific package
# API: https://libraries.io/api
curl "https://libraries.io/api/maven/org.springframework.boot:spring-boot-starter/dependents?api_key=YOUR_KEY" | jq '.[].repository_url'
```

**deps.dev (Google's dependency insight):**
```bash
# Query dependency information
# https://deps.dev/maven/org.springframework.boot%3Aspring-boot-starter
# API: https://api.deps.dev/v3/systems/maven/packages/org.springframework.boot:spring-boot-starter
```

**GitHub Dependency Graph (for repos you own/have access to):**
```bash
# List dependencies via GraphQL API
gh api graphql -f query='
  query($owner: String!, $repo: String!) {
    repository(owner: $owner, name: $repo) {
      dependencyGraphManifests {
        nodes {
          dependencies {
            nodes { packageName }
          }
        }
      }
    }
  }
' -f owner=OWNER -f repo=REPO
```

### Step 3: Create Working Set Directory

Create a dedicated directory for the working set. This keeps the repos.csv and cloned repositories out of git:

```bash
# Create a descriptively-named working set directory
mkdir -p working-set-<name>

# Ensure working set directories are gitignored
grep -q "working-set" .gitignore || echo "working-set*/" >> .gitignore
```

Use a descriptive name like `working-set-spring-jpa`, `working-set-quarkus`, or `working-set-mycompany`.

### Step 4: Generate repos.csv

Generate the repos.csv inside the working set directory:

```bash
# Example: Create repos.csv from GitHub repository list
echo "origin,path,cloneUrl,branch,org1" > working-set-<name>/repos.csv
gh repo list --limit 1000 --json nameWithOwner,url,defaultBranchRef --jq '.[] | ["github.com", .nameWithOwner, .url, .defaultBranchRef.name, "MyOrg"] | @csv' >> working-set-<name>/repos.csv
```

For more complex organization structures:

```bash
# Create repos.csv with organization hierarchy based on repository topics or naming conventions
echo "origin,path,cloneUrl,branch,org1,org2" > working-set-<name>/repos.csv
gh repo list --limit 1000 --json nameWithOwner,url,defaultBranchRef,repositoryTopics --jq '.[] | ["github.com", .nameWithOwner, .url, .defaultBranchRef.name, (.repositoryTopics[0] // "Uncategorized"), "ALL"] | @csv' >> working-set-<name>/repos.csv
```

### Step 5: Sync the Organization

```bash
# Sync the organization from the CSV (repos clone into the same directory)
mod git sync csv working-set-<name> working-set-<name>/repos.csv

# With source code (for pre-analysis and debugging recipes)
mod git sync csv working-set-<name> working-set-<name>/repos.csv --with-sources

# Specify a sub-organization to sync
mod git sync csv working-set-<name> working-set-<name>/repos.csv --organization "Team A"
```

## Example: Finding Spring Boot + JPA Repositories

```bash
# Step 1: Create working set directory and ensure it's gitignored
mkdir -p working-set-spring-jpa
grep -q "working-set" .gitignore || echo "working-set*/" >> .gitignore

# Step 2: Search for repositories using both Spring Boot and JPA
echo "origin,path,cloneUrl,branch,org1" > working-set-spring-jpa/repos.csv
gh search code "spring-boot-starter-data-jpa" --filename pom.xml --limit 100 --json repository --jq '.[].repository | ["github.com", .nameWithOwner, .url, "main", "Spring JPA Apps"] | @csv' | sort -u >> working-set-spring-jpa/repos.csv

# Step 3: Sync to the working set directory
mod git sync csv working-set-spring-jpa working-set-spring-jpa/repos.csv --with-sources
```

## Example: All Repositories in a GitHub Organization

```bash
# Step 1: Create working set directory and ensure it's gitignored
mkdir -p working-set-mycompany
grep -q "working-set" .gitignore || echo "working-set*/" >> .gitignore

# Step 2: List all repos in the organization
echo "origin,path,cloneUrl,branch,org1,org2" > working-set-mycompany/repos.csv
gh repo list mycompany --limit 1000 --json nameWithOwner,url,defaultBranchRef --jq '.[] | ["github.com", .nameWithOwner, .url, .defaultBranchRef.name, "mycompany", "ALL"] | @csv' >> working-set-mycompany/repos.csv

# Step 3: Sync
mod git sync csv working-set-mycompany working-set-mycompany/repos.csv
```

## Tips

- **Rate limits:** GitHub code search has rate limits. For large-scale discovery, consider using the GraphQL API or paginating results.
- **Private repositories:** Ensure your `gh` CLI is authenticated with appropriate scopes for private repository access.
- **Organization structure:** Think about how you want to organize repositories. Common patterns:
  - By team/ownership
  - By technology stack
  - By business domain
  - Flat (all in one organization)

## Reference

Sync from CSV: `mod git sync csv <path> <csv-uri> [--with-sources] [--organization <name>]`

List organizations in CSV: `mod git sync csv <path> <csv-uri> --organization ?`
