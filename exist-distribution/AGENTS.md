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
