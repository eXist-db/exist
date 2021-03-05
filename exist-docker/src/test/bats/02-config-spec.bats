#!/usr/bin/env bats

# Tests for modifying eXist's configuration files
@test "copy configuration file from container to disk" {
  run docker cp exist-ci:exist/etc/conf.xml ./conf.xml && [[ -e ./conf.xml ]] && ls -l ./conf.xml
  [ "$status" -eq 0 ]
}

@test "modify the copied config file" {
  run sed -i.bak 's/wait-before-shutdown="120000"/wait-before-shutdown="60000"/' ./conf.xml
  [ "$status" -eq 0 ]
}

@test "create modified image" {
  run docker create --name ex-mod -p 9090:8080 existdb/exist-ci-build:latest
  [ "$status" -eq 0 ]
  run docker cp ./conf.xml ex-mod:exist/config/conf.xml
  [ "$status" -eq 0 ]
  run docker start ex-mod
  [ "$status" -eq 0 ]
}

@test "modification is applied in container" {
  # Make sure container is running
  result=$(docker ps | grep -o 'ex-mod')
  [ "$result" == 'ex-mod' ]
  sleep 30
  result=$(docker logs ex-mod | grep -o "60,000 ms during shutdown")
  [ "$result" == '60,000 ms during shutdown' ]
}

@test "teardown modified image" {
  run docker stop ex-mod
  [ "$status" -eq 0 ]
  [ "$output" == "ex-mod" ]
  run docker rm ex-mod
  [ "$status" -eq 0 ]
  [ "$output" == "ex-mod" ]
  run rm ./conf.xml
  [ "$status" -eq 0 ]
  run rm ./conf.xml.bak
  [ "$status" -eq 0 ]
}

@test "log queries to system are visible to docker" {
  run docker exec exist-ci java org.exist.start.Main client -q -u admin -P '' -x 'util:log-system-out("HELLO SYSTEM-OUT")'
  [ "$status" -eq 0 ]
  result=$(docker logs exist-ci | grep -o "HELLO SYSTEM-OUT" | head -1)
  [ "$result" == "HELLO SYSTEM-OUT" ]
}

@test "regular log queries are visible to docker" {
  run docker exec exist-ci java org.exist.start.Main client -q -u admin -P '' -x 'util:log("INFO", "HELLO logged INFO")'
  [ "$status" -eq 0 ]
  result=$(docker logs exist-ci | grep -o "HELLO logged INFO" | head -1)
  [ "$result" == "HELLO logged INFO" ]
}