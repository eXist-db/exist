xquery version "3.1";

module namespace lxm="http://exist-db.org/xquery/test/load-xquery-module";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $lxm:MYVAR := lxm:func("ok");

declare function lxm:func($arg as xs:string) {
    upper-case($arg)
};

declare function lxm:func($arg1 as xs:string, $arg2 as xs:string) {
    lxm:func($arg1) || lxm:func($arg2)
};

declare 
    %test:assertEquals(4)
function lxm:import-standard() {
    let $module := load-xquery-module("http://www.w3.org/2005/xpath-functions")
    for $funcByName in $module?functions(xs:QName("fn:min"))?1
    return
        $funcByName((9, 8, 4))
};

declare 
    %test:assertEquals(5, 2, "OK", "OK", 1, "OK")
function lxm:import-with-location() {
    let $module := load-xquery-module("http://exist-db.org/xquery/test/load-xquery-module", map {
        "location-hints": "load-xquery-module.xql",
        "xquery-version": 3.1
    })
    let $fn := $module?functions(xs:QName("lxm:func"))
    return (
        map:size($module?functions),
        map:size($fn),
        $fn?1("ok"),
        $fn?2("o", "k"),
        map:size($module?variables),
        $module?variables(xs:QName("lxm:MYVAR"))
    )
};

declare 
    %test:assertError("err:FOQM0002")
function lxm:import-wrong-version() {
    let $module := load-xquery-module("http://exist-db.org/xquery/test/load-xquery-module", map {
        "location-hints": "load-xquery-module.xql",
        "xquery-version": 3.0
    })
    return
        map:size($module?functions)
};

declare 
    %test:assertEquals(5)
function lxm:import-from-classpath() {
    let $module := load-xquery-module("http://exist-db.org/xquery/xqsuite", map {
        "location-hints": "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql"
    })
    return
        map:size($module?functions)
};