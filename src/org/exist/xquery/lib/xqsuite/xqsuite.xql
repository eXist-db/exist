xquery version "3.0";

(:~
 : XQSuite - a functional test framework based on XQuery 3.0 annotations.
 : Relies on the functions util:list-functions and util:inspect-function
 : to inspect annotations. The rest is pretty much standard XQuery.
 :
 : Usage:
 :
 : <pre>
 : import module namespace test="http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";
 : import module namespace t="http://exist-db.org/xquery/test/examples" at "examples.xql";
 :
 : test:suite(util:list-functions("http://exist-db.org/xquery/test/examples"))
 : </pre>
 :
 : @author Wolfgang Meier
 :)
module namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $test:TEST_NAMESPACE := "http://exist-db.org/xquery/xqsuite";

declare variable $test:UNKNOWN_ASSERTION := QName($test:TEST_NAMESPACE, "no-such-assertion");

(:~
 : Main entry point into the module. Takes a sequence of function items.
 : Functions with known test annotations will be evaluated and their results
 : checked against the annotations.
 :
 : @param $functions a sequence of function items
 : @return an XML report (in xUnit format)
 :)
declare function test:suite($functions as function(*)+) {
    let $modules := test:distinct-modules($functions)
    return
        <testsuites>
        {
            (: Run tests for each module in turn :)
            for $module in $modules
            let $modFunctions := filter(function($func) {
                    namespace-uri-from-QName(function-name($func)) = $module
                }, $functions)
            let $setup := test:call-func-with-annotation($modFunctions, "setUp")
            let $result :=
                if (empty($setup) or $setup/self::ok) then
                    let $results :=
                        test:function-by-annotation($modFunctions, "assert", test:test#2)
                    return
                        <testsuite package="{$module}" timestamp="{util:system-dateTime()}"
                            failures="{count($results/failure)}">
                            { $results }
                        </testsuite>
                else
                    <testsuite package="{$module}" timestamp="{util:system-dateTime()}"
                        errors="{count($functions)}">
                        {$setup/string()}
                    </testsuite>
            return
                ($result, test:call-func-with-annotation($modFunctions, "tearDown"))[1]
        }
        </testsuites>
};

(:~
 : Find functions having the given annotation and call the callback function.
 :)
declare %private function test:function-by-annotation($functions as function(*)+, $annot as xs:string, 
    $callback as function(function(*), element(function)) as item()*) {
    for $function in $functions
    let $meta := util:inspect-function($function)
    where $meta/annotation[@namespace = $test:TEST_NAMESPACE][matches(@name, ":" || $annot)]
    return
        $callback($function, $meta)
};

(:~
 : Return the namespace URI of all distinct modules in the input set.
 :)
declare %private function test:distinct-modules($functions as function(*)+) as xs:string+ {
    distinct-values(
        for $func in $functions
        return
            namespace-uri-from-QName(function-name($func))
    )
};

(:~
 : Find functions having the specified annotation and call them,
 : return <ok/> upon success, an error description otherwise.
 : Used for setUp and tearDown.
 :)
declare %private function test:call-func-with-annotation($functions as function(*)+, $annot as xs:string) as element()? {
    test:function-by-annotation($functions, $annot, function($func, $meta) {
        try {
            (<ok/>, $func())[1]
        } catch * {
            <system-err>{$err:description}</system-err>
        }
    })
};

(:~
 : The main function for running a single test. Executes the test function
 : and compares the result against each assertXXX annotation.
 :)
declare %private function test:test($func as function(*), $meta as element(function)) {
    let $assertions := $meta/annotation[matches(@name, ":assert")]
    return
        if (exists($assertions)) then
            try {
                let $result := $func()
                let $assertResult := test:check-assertions($assertions, $result)
                return
                    test:print-result($meta, $result, $assertResult)
            } catch * {
                let $assertError := $meta/annotation[contains(@name, ":assertError")]
                return
                    if ($assertError) then
                        if ($assertError/value and not(contains($err:code, $assertError/value/string())
                            or contains($err:description, $assertError/value/string())))then
                            <report>
                                <failure message="Expected error {$assertError/value/string()}, got: {$err:code}"
                                    type="failure-error-code-1"/>
                            </report>
                        else
                            test:print-result($meta, (), ())
                    else
                        <error>{$err:code}: {$err:description}</error>
            }
        else
            ()
};

(:~
 : Print out evaluation results for a given test.
 :)
declare %private function test:print-result($meta as element(function), $result as item()*, 
    $assertResult as element(report)?) {
    let $nameAnnot := $meta/annotation[matches(@name, ":name")]
    let $name := if ($nameAnnot) then $nameAnnot/value else replace($meta/@name, "^\w+:([^:]+)$", "$1")
    return
        <testcase name="{$name}" class="{$meta/@name}">
        {
            if (exists($assertResult)) then
                $assertResult/*
            else
                ()
        }
        </testcase>
};

(:~
 : Check the function's return value against each assertion.
 :)
declare %private function test:check-assertions($assertions as element(annotation)*, $result as item()*) as element(report)* {
    for $annotation in $assertions
    let $assert := replace($annotation/@name, "^\w+:(.*)$", "$1")
    return
        switch ($assert)
            case "assertEquals" return
                test:assertEquals($annotation/value/string(), $result)
            case "assertEmpty" return
                test:assertEmpty($result)
            case "assertExists" return
                test:assertExists($result)
            case "assertTrue" return
                test:assertTrue($result)
            case "assertFalse" return
                test:assertFalse($result)
            case "assertXPath" return
                test:assertXPath($annotation/value/string(), $result)
            case "assertError" return
                test:assertError($annotation/value/string(), $result)
            default return
                error($test:UNKNOWN_ASSERTION, $annotation/@name)
};

(:~
 : Check for equality of the function's result with the value in the annotation.
 : This function transforms the result to a string before checking for equality.
 :)
declare %private function test:assertEquals($value as xs:string, $result as item()*) as element(report)? {
    let $normResult :=
        typeswitch ($result)
            case node()* return
                test:normalize($result)
            default return
                string-join($result, " ")
    let $normValue :=
        typeswitch ($result)
            case node()* return
                util:parse("<r>" || $value || "</r>")/r/node()
            default return
                $value
    let $equal :=
        typeswitch ($normResult)
            case node()* return
                deep-equal($normValue, $normResult)
            default return
                $normResult = $normValue
    return
        if ($equal) then
            ()
        else
           <report>
                <failure message="assertEquals failed."
                    type="failure-error-code-1">
                { $normValue }
                </failure>
                <output>{ $result }</output>
            </report>
};

declare %private function test:normalize($nodes as node()*) {
    for $node in $nodes return test:normalize-node($node)
};

declare %private function test:normalize-node($node as node()) {
    typeswitch ($node)
        case document-node() return
            test:normalize-node($node/*)
        case element() return
            element { node-name($node) } {
                $node/@*, for $child in $node/node() return test:normalize-node($child)
            }
        case text() return
            let $norm := normalize-space($node)
            return
                if (string-length($norm) eq 0) then () else $node
        default return
            $node
};

(:~
 : Check if the function's result is empty.
 :)
declare %private function test:assertEmpty($result as item()*) as element(report)? {
    if (empty($result)) then
        ()
    else
        <report>
            <failure message="assertEmpty failed."
                type="failure-error-code-1"/>,
            <output>{ $result }</output>
        </report>
};

(:~
 : Check if the function's result is not empty.
 :)
declare %private function test:assertExists($result as item()*) as element(report)? {
    if (exists($result)) then
        ()
    else
        <report>
            <failure message="assertExists failed."
                type="failure-error-code-1"/>,
            <output>{ $result }</output>
        </report>
};

(:~
 : Check if the function's result is true.
 :)
declare %private function test:assertTrue($result as item()*) as element(report)? {
    if ($result) then
        ()
    else
        <report>
            <failure message="assertExists failed."
                type="failure-error-code-1"/>,
            <output>{ $result }</output>
        </report>
};

(:~
 : Check if the function caused an error.
 :)
declare %private function test:assertError($value as xs:string, $result as item()*) as element(report)? {
    if ($result) then
        ()
    else
        <report>
            <failure message="assertError failed. Expected error {$value}"
                type="failure-error-code-1"/>,
            <output>{ $result }</output>
        </report>
};

(:~
 : Check if the function's result is false.
 :)
declare %private function test:assertFalse($result as item()*) as element(report)? {
    if (not($result)) then
        ()
    else
        <report>
            <failure message="assertExists failed."
                type="failure-error-code-1"/>,
            <output>{ $result }</output>
        </report>
};

(:~
 : Evaluate an arbitrary XPath expression against result. The test passes if
 : the XPath returns either true or a non-empty sequence.
 :)
declare %private function test:assertXPath($expr as xs:string?, $result as item()*) as element(report)? {
    let $xr :=
        test:checkXPathResult(
            if (matches($expr, "^\s*/")) then
                util:eval(concat("$result", $expr))
            else
                util:eval($expr)
        )
    return
        if ($xr) then
            ()
        else
            <report>
                <failure message="assertXPath failed."
                    type="failure-error-code-1">{ $expr }</failure>,
                <output>{ $result }</output>
            </report>
};

declare %private function test:checkXPathResult($result as item()*) {
    if ($result instance of xs:boolean) then
        $result
    else
        exists($result)
};

(: Helper functions to be used by test modules :)

declare function test:mkcol($root as xs:string, $path as xs:string) as empty() {
    test:mkcol-recursive($root, tokenize($path, "/"))
};

declare %private function test:mkcol-recursive($collection, $components) as empty() {
    if (exists($components)) then
        let $newColl := concat($collection, "/", $components[1])
        return (
            xmldb:create-collection($collection, $components[1])[2],
            test:mkcol-recursive($newColl, subsequence($components, 2))
        )
    else
        ()
};