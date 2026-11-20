# exist-distribution

## Distribution artifacts (zip, tar.bz2, DMG)

Produces release archives and platform-specific packages. Output lands in `exist-distribution/target/`.

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) \
  mvn -T1.5C clean package \
  -pl exist-distribution -am \
  -DskipTests \
  -Ddependency-check.skip=true \
  -Ddocker=false \
  -Drevision=7.0.0-SNAPSHOT
```

**macOS**: the `mac-dmg-on-mac` profile is active by default and produces an unsigned `.app` bundle and DMG. Suppress both with `-P '!mac-dmg-on-mac'`. For the fully signed and notarized DMG used in releases, see `exist-versioning-release.md`.

**Linux**: the `mac-dmg-on-unix` profile is active by default on non-CI Linux machines (suppressed when `env.CI=true`) and produces an unsigned DMG. Requires `hfsplus-tools` (`apt-get install hfsprogs hfsplus` / `yum install hfsutils hfsplus-tools`); warns and skips gracefully if missing. Suppress with `-P '!mac-dmg-on-unix'`.

Both DMG profiles are suppressed automatically by `-P skip-build-dist-archives` via the `skip.mac.dist` property.

## Vector embeddings (`-Pvector`)

`-Pvector` is the one switch for vector embedding support: it downloads the test model (in `exist-parent`) so the vector embedding tests run and fail rather than skip without it, adds `exist-vector` to the distribution, ships the models from `vector.models` in `onnx-models/<id>`, and enables them in `etc/conf.xml` (`<vector-models>`, `vector:` module) via `src/main/xslt/conf-vector.xslt`. `vector.models` is a whitespace-separated list of `id|dimension|baseUrl` download locations (default `all-MiniLM-L6-v2`); downloads are cached in `~/.m2/onnx-models/<id>`. The macOS `.app` gets the same models and `conf.xml`. The `bin/` scripts get `vector.java.opts` (SIMD: `--add-modules=jdk.incubator.vector --enable-native-access=ALL-UNNAMED`) through the `@VECTOR_JAVA_OPTS@` token in `src/main/templates`. The GUI launcher (and so the `.app`) and the Windows service start the server in a JVM of their own and do not get it. The `vector.*` defaults live in `exist-parent`. With `-Pdocker,vector`, exist-docker builds the `latest-vector` image from this; see the image README.
