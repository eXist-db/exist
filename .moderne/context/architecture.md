# Architecture

## FINOS CALM architecture diagram

FINOS CALM (Common Architecture Language Model) architecture diagram showing services, databases, external integrations, and messaging connections. Use this to understand the high-level system architecture and component relationships.

## Data Tables

### External service calls

**File:** [`external-service-calls.csv`](external-service-calls.csv)

Outbound HTTP/REST calls to external services.

| Column | Description |
|--------|-------------|
| Entity ID | Unique identifier for this external service entity (format: external:{className}). |
| Source path | The path to the source file containing the external service call. |
| Client class | The fully qualified name of the class making the external call. |
| Target service | The name or URL of the target external service. |
| Client type | The type of HTTP client used (RestTemplate, WebClient, Feign, etc.). |
| Protocol | The protocol used (HTTP, HTTPS). |
| Base URL | The base URL for the external service if configured. |

### Project metadata

**File:** [`project-metadata.csv`](project-metadata.csv)

Project-level identity and structure for each build module. Includes Maven GAV coordinates, display name, description, parent project lineage, and submodule count. Use this to understand what the project is, how it relates to parent projects, and whether it is a multi-module aggregator.

| Column | Description |
|--------|-------------|
| Source path | The path to the build file (pom.xml or build.gradle). |
| Artifact ID | The project's artifact ID (Maven) or project name (Gradle). |
| Group ID | The project's group ID. |
| Name | The project's display name. |
| Description | The project's description. |
| Version | The project's version. |
| Parent project | The parent project coordinates (e.g., groupId:artifactId:version for Maven). |
| Module count | The number of declared submodules for aggregator projects. |

### Data assets

**File:** [`data-assets.csv`](data-assets.csv)

Data entities, DTOs, and records that represent the application's data model.

| Column | Description |
|--------|-------------|
| Source path | The path to the source file containing the data asset. |
| Class name | The fully qualified name of the data asset class. |
| Simple name | The simple class name for display. |
| Asset type | The type of data asset (Entity, Record, DTO, Document, etc.). |
| Description | A description of the data asset based on its fields. |
| Fields | Comma-separated list of field names. |

