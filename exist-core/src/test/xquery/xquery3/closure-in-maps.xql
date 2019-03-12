(:~
 : @see https://github.com/eXist-db/exist/issues/1550
 :)
xquery version "3.1";

module namespace ct="http://exist-db.org/xquery/closures/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $ct:test := "123";

declare variable $ct:model := function($param) {
    let $func := function() {
        $param
    }
    return
        map {
        'test1': $func,
        'test2': $func(),
        'test3': function() {
            $param
        }
        }
};

declare %test:assertEquals('Hello World') function ct:test1-function-reference() as xs:string {
    try {
        $ct:model('Hello World')?test1()
    }
    catch * {$err:code || ' ' || $err:description}
};

declare %test:assertEquals('Hello World') function ct:test2-function-call() as xs:string {
    try {
        $ct:model('Hello World')?test2
    }
    catch * {$err:code || ' ' || $err:description}
};

declare %test:assertEquals('Hello World') function ct:test3-inline-function() as xs:string {
    try {
        $ct:model('Hello World')?test3()
    }
    catch * {$err:code || ' ' || $err:description}
};