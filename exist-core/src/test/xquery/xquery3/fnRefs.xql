xquery version "3.0";

module namespace fnRefs="http://exist-db.org/xquery/test/function_reference";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare
    %test:assertEquals("/db")
function fnRefs:castFunctionReference() {
    let $as-uri := xs:anyURI(?)
    return
        $as-uri("/db")
};

declare
	%test:assertEquals("b")
function fnRefs:call-from-predicate() {
	let $predicate := function($x as xs:string, $y as xs:string) as xs:boolean { $x eq $y }
	return
		("a", "b", "c")[$predicate("b", .)]
};

declare function fnRefs:pred($x as xs:string, $y as xs:string) as xs:boolean {
	$x eq $y
};

declare
	%test:assertEquals("b")
function fnRefs:call-from-predicate-via-variable() {
	let $predicate := fnRefs:pred#2
	return
		("a", "b", "c")[$predicate("b", .)]
};

declare
	%test:assertEquals("b")
function fnRefs:call-from-predicate-inline() {
	("a", "b", "c")[function($x as xs:string, $y as xs:string) as xs:boolean { $x eq $y }("b", .)]
};
