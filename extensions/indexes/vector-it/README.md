# Vector Integration Tests

Integration tests for Lucene vector search and ONNX embedding models.

## Running

```bash
mvn verify -pl extensions/indexes/vector-it -am -Ponnx-model -DskipUnitTests=true
```

The `-Ponnx-model` profile downloads the all-MiniLM-L6-v2 model from HuggingFace on first run.

## Windows CI: ONNX Runtime native load

ONNX Runtime >= 1.21.0 requires VC++ runtime >= 14.40. The JDK distribution used in CI must ship a compatible version.

### Root cause

The Windows DLL loader resolves `VCRUNTIME140.dll` / `MSVCP140.dll` from `JAVA_HOME\bin` (on `PATH`) **before** the system's copies in `System32`. If the JDK bundles an older VC++ runtime than ONNX Runtime expects, native load fails with `UnsatisfiedLinkError`.

### Compatible JDK distributions

| JDK Distribution | VC++ version in `JAVA_HOME\bin` | ONNX Runtime 1.24.3 |
|------------------|-------------------------------|---------------------|
| Microsoft OpenJDK 21.0.2 | 14.29 | FAILS |
| Temurin 21.0.10 | 14.40 | OK |
| Zulu 21 | 14.40 | OK |

### Current fix

All CI workflows use **Temurin**, which ships VC++ 14.40:

```yaml
- name: Set up JDK
  uses: actions/setup-java@v5
  with:
    distribution: temurin
    java-version: '21'
```

### If this breaks again

If a future ONNX Runtime version requires a newer VC++ and the JDK's bundled copy is too old again, either:

**A.** Switch to a JDK distribution with a newer bundled VC++ runtime, or

**B.** Remove the conflicting DLLs from `JAVA_HOME\bin` before running tests:

```yaml
- name: Fix VC++ runtime conflict
  if: runner.os == 'Windows'
  shell: pwsh
  run: |
    $dlls = @("msvcp140.dll", "msvcp140_1.dll", "msvcp140_2.dll",
              "vcruntime140.dll", "vcruntime140_1.dll",
              "concrt140.dll", "vccorlib140.dll")
    foreach ($dll in $dlls) {
      $p = Join-Path $env:JAVA_HOME "bin\$dll"
      if (Test-Path $p) { Remove-Item $p -Force }
    }
```

This forces the JVM to use the system-installed VC++ runtime, which is a strict superset.

### References

- [duncdrum/onnx-load-debug](https://github.com/duncdrum/onnx-load-debug) -- standalone reproducer
- [onnxruntime#24287](https://github.com/microsoft/onnxruntime/issues/24287) -- VC++ redist mismatch (won't-fix by ORT)
- [onnxruntime#26024](https://github.com/microsoft/onnxruntime/issues/26024) -- ONNX Runtime dynamic linking to VC++
