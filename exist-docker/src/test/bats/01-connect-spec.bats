#!/usr/bin/env bats

# Basic start-up and connection tests
@test "container jvm responds from client" {
  run docker exec exist-ci java -version
  [ "$status" -eq 0 ]
}

@test "container can be reached via http" {
  result=$(curl -Is http://127.0.0.1:8080/ | grep -o 'Jetty')
  [ "$result" == 'Jetty' ]
}

@test "container reports healthy to docker" {
  result=$(docker ps | grep -o 'healthy')
  [ "$result" == 'healthy' ]
}

@test "logs show clean start" {
  result=$(docker logs exist-ci | grep -o 'Server has started')
  [ "$result" == 'Server has started' ]
}

@test "logs are error free" {
  # Exclude the SECURITY WARNING that fires when 'admin' has the empty
  # default password -- that line is the C1 startup warning and is the
  # expected state for an unconfigured fresh container image.
  result=$(docker logs exist-ci 2>&1 | grep -v 'SECURITY WARNING' | grep -ow -c 'ERROR' || true)
  [ "$result" -eq 0 ]
}

@test "logs contain repo.log output" {
  result=$(docker logs exist-ci | grep -o -m 1 'Deployment.java')
  [ "$result" == 'Deployment.java' ]
}
