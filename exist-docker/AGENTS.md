# exist-docker

## Docker image

```bash
# Build the Docker image
mvn -T1.5C clean package -DskipTests -Ddependency-check.skip=true -Ddocker=true \
  -Pskip-build-dist-archives \
  -pl exist-docker -am

cp exist-docker/target/classes/Dockerfile exist-docker/target/exist-docker-*-docker-dir/Dockerfile
docker build -t existdb/existdb:local exist-docker/target/exist-docker-*-docker-dir/

# Run
docker run -d --name existdb -p 8080:8080 -p 8443:8443 existdb/existdb:local
# Access at http://localhost:8080/exist/
```
