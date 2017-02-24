xquery version "3.0";

module namespace mlt="http://exist-db.org/xquery/test/maps_lookup";

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