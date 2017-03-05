xquery version "3.1";

module namespace lxm="http://exist-db.org/xquery/test/load-xquery-module";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $lxm:MYVAR := lxm:func("ok");

declare variable $lxm:EXTERNAL external := "external var";

declare variable $lxm:EXTERNAL_TYPED as xs:integer external := 123;

declare variable $lxm:EXTERNAL_EXPR as xs:string external := $lxm:MYVAR;

declare variable $lxm:EXTERNAL_CONTEXT as xs:string := upper-case(.);

declare variable $lxm:EXTERNAL_NO_DEFAULT external;

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
    %test:assertEquals(14, 2, "OK", "OK", 6, "OK")
function lxm:import-with-location() {
    let $module := load-xquery-module("http://exist-db.org/xquery/test/load-xquery-module", map {
        "location-hints": "load-xquery-module.xql",
        "xquery-version": 3.1,
        "variables": map { xs:QName("lxm:EXTERNAL_NO_DEFAULT"): "OK" }
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
    %test:assertError("err:FOQM0003")
function lxm:import-wrong-version() {
    let $module := load-xquery-module("http://exist-db.org/xquery/test/load-xquery-module", map {
        "location-hints": "load-xquery-module.xql",
        "xquery-version": 3.0,
        "variables": map { xs:QName("lxm:EXTERNAL_NO_DEFAULT"): "OK" }
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

declare 
    %test:assertError("err:FOQM0002")
function lxm:import-not-found1() {
    let $module := load-xquery-module("http://exist-db.org/xquery/does-not-exist")
    return
        map:size($module?functions)
};

declare 
    %test:assertError("err:FOQM0002")
function lxm:import-not-found2() {
    let $module := load-xquery-module("http://exist-db.org/xquery/does-not-exist", map {
        "location-hints": "does-not-exist.xql"
    })
    return
        map:size($module?functions)
};

declare 
    %test:assertEquals("external var")
function lxm:import-with-external-var-default() {
    let $module := load-xquery-module("http://exist-db.org/xquery/test/load-xquery-module", map {
        "location-hints": "load-xquery-module.xql",
        "xquery-version": 3.1,
        "variables": map { xs:QName("lxm:EXTERNAL_NO_DEFAULT"): "OK" }
    })
    return
        $module?variables(xs:QName("lxm:EXTERNAL"))
};

declare 
    %test:assertEquals("hello world")
function lxm:import-with-external-var-set() {
    let $module := load-xquery-module("http://exist-db.org/xquery/test/load-xquery-module", map {
        "location-hints": "load-xquery-module.xql",
        "xquery-version": 3.1,
        "variables": map { 
            xs:QName("lxm:EXTERNAL"): "hello world",
            xs:QName("lxm:EXTERNAL_NO_DEFAULT"): "OK"
        }
    })
    return
        $module?variables(xs:QName("lxm:EXTERNAL"))
};

declare 
    %test:assertEquals(999)
function lxm:import-with-external-var-set-typed() {
    let $module := load-xquery-module("http://exist-db.org/xquery/test/load-xquery-module", map {
        "location-hints": "load-xquery-module.xql",
        "xquery-version": 3.1,
        "variables": map { 
            xs:QName("lxm:EXTERNAL"): 999,
            xs:QName("lxm:EXTERNAL_NO_DEFAULT"): "OK"
        }
    })
    return
        $module?variables(xs:QName("lxm:EXTERNAL"))
};

declare
    %test:assertEquals("OK")
function lxm:import-with-external-var-default-expr() {
    let $module := load-xquery-module("http://exist-db.org/xquery/test/load-xquery-module", map {
        "location-hints": "load-xquery-module.xql",
        "xquery-version": 3.1,
        "variables": map { xs:QName("lxm:EXTERNAL_NO_DEFAULT"): "OK" }
    })
    return
        $module?variables(xs:QName("lxm:EXTERNAL_EXPR"))
};

declare
    %test:assertEquals("OK")
function lxm:import-with-external-var-no-default() {
    let $module := load-xquery-module("http://exist-db.org/xquery/test/load-xquery-module", map {
        "location-hints": "load-xquery-module.xql",
        "variables": map { xs:QName("lxm:EXTERNAL_NO_DEFAULT"): "OK" }
    })
    return
        $module?variables(xs:QName("lxm:EXTERNAL_NO_DEFAULT"))
};

declare 
    %test:assertError("err:FOQM0005")
function lxm:import-with-external-var-set-wrong-type() {
    let $module := load-xquery-module("http://exist-db.org/xquery/test/load-xquery-module", map {
        "location-hints": "load-xquery-module.xql",
        "xquery-version": 3.1,
        "variables": map { 
            xs:QName("lxm:EXTERNAL_TYPED"): "hello world",
            xs:QName("lxm:EXTERNAL_NO_DEFAULT"): "OK"
        }
    })
    return
        $module?variables(xs:QName("lxm:EXTERNAL_TYPED"))
};

declare 
    %test:assertEquals("OK")
function lxm:import-with-context-item() {
    let $module := load-xquery-module("http://exist-db.org/xquery/test/load-xquery-module", map {
        "location-hints": "load-xquery-module.xql",
        "xquery-version": 3.1,
        "context-item": "ok",
        "variables": map { xs:QName("lxm:EXTERNAL_NO_DEFAULT"): "OK" }
    })
    return
        $module?variables(xs:QName("lxm:EXTERNAL_CONTEXT"))
};