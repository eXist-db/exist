# Api Contracts

## Endpoint contracts, DTO schemas, parameters, exception handlers, and fixture examples

Complete raw material for generating OpenAPI 3.0.3 specs and consumer/provider contract tests deterministically. Five CSVs: endpoint-request-response-schemas.csv (one row per endpoint*status, with request body FQN, response body FQN, collection flag), endpoint-parameters.csv (path/query/header/form params), dto-field-schemas.csv (per-field wire name, type, format, required flag, validation JSON, @Schema example), exception-handlers.csv (@ControllerAdvice + controller-local exception->status->body mappings), and field-example-values.csv (raw jsonPath->value rows mined from src/test/resources fixtures). Join endpoint tables via the endpointId column to service-endpoints.csv, and DTO tables via class FQN to data-assets.csv.

## Data Tables

### DTO field schemas

**File:** [`dto-field-schemas.csv`](dto-field-schemas.csv)

Per-field schema detail for request/response DTOs: wire name, type, required flag, OpenAPI format, validation constraints, and any @Schema(example=) example values.

| Column | Description |
|--------|-------------|
| Source path | The path to the source file containing the DTO class. |
| Owner class FQN | Fully qualified name of the DTO class owning this field. Joins to data-assets.csv via 'Class name'. |
| Field name | The Java field name. |
| Serialized name | The JSON name on the wire, after applying @JsonProperty overrides and class-level @JsonNaming. |
| Serialized name source | How the serialized name was derived: 'java-name' (no override), 'json-property' (@JsonProperty value), 'jackson-strategy' (recognized @JsonNaming applied), or 'unknown-strategy' (@JsonNaming present but strategy class isn't a known one - the Java field name is used as a best-effort fallback). |
| Type FQN | Fully qualified name of the field's type (after unwrapping Optional/Collection wrappers). |
| Type resolution | How confidently the type was resolved: 'resolved' (FQN known), 'simple-name' (only the class simple name could be recovered, may not uniquely identify the DTO), or 'unresolved' (the type could not be determined at all). |
| Is collection | True when the declared type is a collection (List/Set/array/etc.) - the type FQN is then the element type. |
| Is map | True when the declared type is a Map - the type FQN is then the value type. |
| Is optional | True when the declared type is java.util.Optional. |
| Format | OpenAPI 3.0.3 format (int32, int64, date-time, uuid, email, binary, ...) or null. |
| Required | Whether the field is required. Set true for @NotNull/@NotBlank/@NotEmpty, primitives, and @JsonProperty(required=true). |
| Validations JSON | JSON map of validation constraint simple name to argument map. Example: {"NotNull":{},"Size":{"min":1,"max":100}}. |
| Example value | Example value from @Schema(example = "...") if declared on the field, else null. |

