xquery version "1.0";

declare function local:key($key, $options) {
    $key
};

let $term := request:get-parameter("q", ())
let $log := util:log("DEBUG", ("Q=", $term))
let $callback := util:function(xs:QName("local:key"), 2)
return
    util:index-keys(collection("/db"), $term, $callback, 20, "lucene-index")