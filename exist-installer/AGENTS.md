# exist-installer

## IzPack installer JAR

Produces the cross-platform installer JAR in `exist-installer/target/`.

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) \
  mvn -T1.5C clean package \
  -Prelease-build \
  -pl exist-installer -am \
  -DskipTests \
  -Ddependency-check.skip=true \
  -Ddocker=false \
  -Drevision=7.0.0-SNAPSHOT
```

Run the installer: `java -jar exist-installer/target/exist-installer-7.0.0-SNAPSHOT.jar`
