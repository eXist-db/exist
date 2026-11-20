#!/usr/bin/env bats

# Tests for the image built with -Pvector.
# Expect a running container named exist-ci with an empty admin password.
# VECTOR_MODELS: the vector.models value the image was built with
# (whitespace-separated id|dimension|baseUrl); defaults to the profile default.

DEFAULT_MODELS='all-MiniLM-L6-v2|384|https://huggingface.co/onnx-models/all-MiniLM-L6-v2-onnx/resolve/main'

xq() {
  docker exec exist-ci java org.exist.start.Main client -q -u admin -P '' -x "$1"
}

specs() {
  echo ${VECTOR_MODELS:-$DEFAULT_MODELS} | tr ' ' '\n'
}

@test "baked-in models are registered" {
  for spec in $(specs); do
    id="${spec%%|*}"
    run xq "vector:models() = '$id'"
    echo "# $id: $output" >&3
    [ "$status" -eq 0 ]
    [[ "$output" == *true* ]]
  done
}

@test "vector:embed returns embeddings of the registered dimension" {
  for spec in $(specs); do
    IFS='|' read -r id dim _ <<< "$spec"
    run xq "array:size(vector:embed('hello world', '$id'))"
    echo "# $id: $output (expected $dim)" >&3
    [ "$status" -eq 0 ]
    [[ "$output" == *"$dim"* ]]
  done
}

@test "lucene vector-field indexes on store and ft:query-vector finds the nearest document" {
  IFS='|' read -r id dim _ <<< "$(specs | head -1)"
  run xq "
    let \$conf := <collection xmlns='http://exist-db.org/collection-config/1.0'><index><lucene>
        <text qname='doc'>
          <vector-field name='embedding' expression='.' dimension='$dim' similarity='cosine' embedding='local' model='$id'/>
        </text>
      </lucene></index></collection>
    let \$_ := xmldb:create-collection('/db/system/config/db', 'vector-bats')
    let \$_ := xmldb:store('/db/system/config/db/vector-bats', 'collection.xconf', \$conf)
    let \$_ := xmldb:create-collection('/db', 'vector-bats')
    let \$_ := xmldb:store('/db/vector-bats', 'a.xml', <doc>The cat sat on the mat.</doc>)
    let \$_ := xmldb:store('/db/vector-bats', 'b.xml', <doc>Stock markets fell sharply today.</doc>)
    let \$v := vector:embed('a kitten resting on a rug', '$id')
    return util:document-name(collection('/db/vector-bats')//doc[ft:query-vector(., \$v, 1)])
  "
  echo "# $output" >&3
  [ "$status" -eq 0 ]
  [[ "$output" == *a.xml* ]]
  [[ "$output" != *b.xml* ]]
}

@test "teardown vector collections" {
  run xq "(xmldb:remove('/db/vector-bats'), xmldb:remove('/db/system/config/db/vector-bats'))"
  [ "$status" -eq 0 ]
}
