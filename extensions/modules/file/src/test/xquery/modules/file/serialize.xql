xquery version "3.0";

module namespace serialization="http://exist-db.org/testsuite/modules/file/serialization";

import module namespace test="http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";
import module namespace file="http://exist-db.org/xquery/file";


declare
    %test:pending("need to mechanism to setup a temporary file to work with")
    %test:assertEquals("datadata", "true", "true", "true")
function serialization:append() {

    let $node-set := text {"data"}
    let $path := system:get-exist-home() || "/test.txt"
    let $parameters := ()
    let $append := true()
    let $remove := file:delete($path)
    let $ser1 := 	file:serialize($node-set, $path, (), false())
    let $ser2 := 	file:serialize($node-set, $path, (), true())
    let $read := file:read($path)
    let $remove := file:delete($path)
    return ($read, $ser1, $ser2, $remove)
};

declare
    %test:pending("need to mechanism to setup a temporary file to work with")
    %test:assertEquals("data", "true", "true", "true")
function serialization:overwrite() {

    let $node-set := text {"data"}
    let $path := system:get-exist-home() || "/test.txt"
    let $parameters := ()
    let $append := true()
    let $remove := file:delete($path)
    let $ser1 := 	file:serialize($node-set, $path, (), false())
    let $ser2 := 	file:serialize($node-set, $path, (), false())
    let $read := file:read($path)
    let $remove := file:delete($path)
    return ($read, $ser1, $ser2, $remove)
};