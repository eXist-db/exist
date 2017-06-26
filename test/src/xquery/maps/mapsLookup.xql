xquery version "3.1";

module namespace mlt="http://exist-db.org/xquery/test/maps_lookup";

declare namespace output="http://www.w3.org/2010/xslt-xquery-serialization";

import module namespace test="http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";

declare
    %test:assertEquals("Jenna")
function mlt:postfix_lookup_on_map() {
    map { "first" : "Jenna", "last" : "Scott" }?first
};

declare
    %test:assertEquals("Jenna", "Scott")
function mlt:wildcard_lookup_on_map() {
    map { "first" : "Jenna", "last" : "Scott" }?*
};

declare
    %test:assertEquals("Tom", "Dick", "Harry")
function mlt:postfix_lookup_on_maps() {
    (map {"first": "Tom"}, map {"first": "Dick"}, map {"first": "Harry"})?first
};

declare
%test:assertEquals( "null", "null")
function mlt:null_lookup(){
    let $serializationParams := <output:serialization-parameters> <output:method>json</output:method> </output:serialization-parameters>

    let $json1 := parse-json( '{"total":[{"data":null}]}' )
    let $test1 := $json1?total?*?foobar

    let $json2 := parse-json( '{"total":[{"data":null}]}' )
    let $test2 :=  $json2?total?*?foobar?aaa

    return ( serialize($test1, $serializationParams) , serialize($test2, $serializationParams) )
};