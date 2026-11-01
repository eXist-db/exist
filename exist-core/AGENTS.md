# exist-core

## Adding a new `fn:` function

1. Create the class in `org.exist.xquery.functions.fn` extending `BasicFunction`
2. Define `FunctionSignature` constant(s)
3. Register in `FnModule.java` — add `FunctionDef` to the array in a labeled block:
   ```java
   // --- Feature Name ---
   new FunctionDef(MyFunction.SIGNATURE, FnModule.class),
   // --- End Feature Name ---
   ```
4. No `conf.xml` change is needed: `FnModule` is registered directly in `org.exist.util.Configuration`, so the `FunctionDef` entry is enough

## Adding error codes

Add to `ErrorCodes.java` in a labeled block for your feature area:
```java
// --- Feature Name error codes ---
public static final ErrorCode FOXX0001 = new ErrorCode("FOXX0001", "Description");
```
