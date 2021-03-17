#!/usr/bin/env bats

# Basic start-up and connection tests
@test "jvm responds from client" {
  run docker exec exist-ci java -version
  [ "$status" -eq 0 ]
}

@test "eXist can be reached via http" {
  result=$(curl -Is http://127.0.0.1:8080/ | grep -o 'Jetty')
  [ "$result" == 'Jetty' ]
}

@test "eXist reports healthy to docker" {
  result=$(docker ps | grep -o 'healthy')
  [ "$result" == 'healthy' ]
}

@test "eXist logs show clean start" {
  result=$(docker logs exist-ci | grep -o 'Server has started')
  [ "$result" == 'Server has started' ]
}

@test "logs are error free" {
  result=$(docker logs exist-ci | grep -ow -c 'ERROR' || true)
  [ "$result" -eq 0 ]
}