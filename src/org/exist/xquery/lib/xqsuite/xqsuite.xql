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
declare variable $test:WRONG_ARG_COUNT := QName($test:TEST_NAMESPACE, "wrong-number-of-arguments");

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
                    let $startTime := util:system-time()
                    let $results :=
                        test:function-by-annotation($modFunctions, "assert", test:run-tests#2)
                    let $elapsed :=
                        util:system-time() - $startTime
                    return
                        <testsuite package="{$module}" timestamp="{util:system-dateTime()}"
                            failures="{count($results/failure)}" tests="{count($results)}"
                            time="{$elapsed}">
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
 : Calls the test function one or more times, depending on the number of
 : %args() annotations found. Each %arg annotation triggers one test run
 : using the supplied parameters.
 :)
declare %private function test:run-tests($func as function(*), $meta as element(function)) {
    let $argsAnnot := $meta/annotation[matches(@name, ":args")]
    return
        if ($argsAnnot) then
            for $args in $argsAnnot
            return
                test:test($func, $meta, $args)
        else
            test:test($func, $meta, ())
};

(:~
 : The main function for running a single test. Executes the test function
 : and compares the result against each assertXXX annotation.
 :)
declare %private function test:test($func as function(*), $meta as element(function), $args as element(annotation)?) {
    let $assertions := test:get-assertions($meta, $args)
    return
        if (exists($assertions)) then
            try {
                let $result := test:call-test($func, $meta, $args)
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
                        <error code="{$err:code}" message="{$err:description}">
                            <xquery-trace>{$exerr:xquery-stack-trace}</xquery-trace>
                            <java-trace>{$exerr:java-stack-trace}</java-trace>
                        </error>
            }
        else
            ()
};

(:~
 : Get all %assertXXX annotations of the function. If %args is used multiple times,
 : assertions apply to the result of running the function with the parameters given
 : in the preceding %args.
 :)
declare %private function test:get-assertions($meta as element(function), $args as element(annotation)?) {
    if ($args) then
        let $nextArgs := $args/following-sibling::annotation[contains(@name, ":args")]
        return
            $args/following-sibling::annotation[contains(@name, ":assert")]
            except
            $nextArgs/following-sibling::annotation[contains(@name, ":assert")]
    else
        $meta/annotation[contains(@name, ":assert")]
};

(:~
 : Map any arguments from the %args annotation into function parameters and evaluate
 : the resulting function. 
 :)
declare %private function test:call-test($func as function(*), $meta as element(function), $args as element(annotation)*) {
    let $argCount := count($meta/argument)
    let $testArgs := $args/value/string()
    return
        if (count($testArgs) != $argCount) then
            error(
                $test:WRONG_ARG_COUNT, 
                "The number of arguments specified in test:args must match the arguments of the function to test"
            )
        else
            let $args := test:map-arguments($testArgs, $meta/argument)
            return
                test:apply($func, $args)
};

(:~
 : Transform the annotation to the type required by the function parameter.
 :)
declare %private function test:map-arguments($testArgs as xs:string*, $funcArgs as element(argument)*) {
    if (exists($testArgs)) then
        map-pairs(function($targ, $farg) {
            switch (string($farg/@type))
                case "xs:string" return
                    string($targ)
                case "xs:integer" case "xs:int" return
                    xs:integer($targ)
                case "xs:decimal" return
                    xs:decimal($targ)
                case "xs:float" case "xs:double" return
                    xs:double($targ)
                case "xs:date" return
                    xs:date($targ)
                case "xs:dateTime" return
                    xs:dateTime($targ)
                case "xs:time" return
                    xs:time($targ)
                case "element()" return
                    util:parse($targ)/*
                case "text()" return
                    text { string($targ) }
                default return
                    $targ
        }, $testArgs, $funcArgs)
    else
        ()
};

(:~
 : Call the given function with parameters taken from $args.
 :)
declare %private function test:apply($func as function(*), $args as item()*) {
    switch (count($args))
        case 0 return
            $func()
        case 1 return
            $func($args[1])
        case 2 return
            $func($args[1], $args[2])
        case 3 return
            $func($args[1], $args[2], $args[3])
        case 4 return
            $func($args[1], $args[2], $args[3], $args[4])
        case 5 return
            $func($args[1], $args[2], $args[3], $args[4], $args[5])
        case 6 return
            $func($args[1], $args[2], $args[3], $args[4], $args[5], $args[6])
        case 7 return
            $func($args[1], $args[2], $args[3], $args[4], $args[5], $args[6], $args[7])
        default return
            error($test:WRONG_ARG_COUNT, "Function takes too many arguments")
};

(:~
 : Print out evaluation results for a given test.
 :)
declare %private function test:print-result($meta as element(function), $result as item()*, 
    $assertResult as element(report)*) {
    let $nameAnnot := $meta/annotation[matches(@name, ":name")]
    let $name := if ($nameAnnot) then $nameAnnot/value else replace($meta/@name, "^\w+:([^:]+)$", "$1")
    return
        <testcase name="{$name}" class="{$meta/@name}">
        {
            if (exists($assertResult)) then
                ($assertResult[failure] | $assertResult)[1]/*
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
                test:assertXPath($annotation, $result)
            case "assertError" return
                test:assertError($annotation/value/string(), $result)
            default return
                error($test:UNKNOWN_ASSERTION, $annotation/@name)
};

(:~
 : Check for equality of the function's result with the value in the annotation.
 : This function transforms the result to a string before checking for equality.
 :)
declare %private function test:assertEquals($values as item()*, $result as item()*) as element(report)? {
    if (exists($values)) then
        if (count($values) eq count($result)) then
            let $tests := map-pairs(test:equals#2, $values, $result)
            let $equal := every $test in $tests satisfies $test
            return
                if ($equal) then
                    ()
                else
                   <report>
                        <failure message="assertEquals failed."
                            type="failure-error-code-1">
                        { $values }
                        </failure>
                        <output>{ $result }</output>
                    </report>
        else
            <report>
                <failure message="assertEquals failed: wrong number of items returned by function. Expected: {count($values)}. Got: {count($result)}"
                    type="failure-error-code-1">
                { $values }
                </failure>
                <output>{ $result }</output>
            </report>
    else
        ()
};

declare %private function test:equals($value as item(), $result as item()) as xs:boolean {
    let $normResult :=
        typeswitch ($result)
            case node() return
                test:normalize($result)
            default return
                $result
    let $normValue := test:cast-to-type($value, $result)
    return
        typeswitch ($normResult)
            case node() return
                deep-equal($normValue, $normResult)
            default return
                $normResult eq $normValue
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
declare %private function test:assertXPath($annotation as element(annotation), $output as item()*) as element(report)? {
    let $expr := $annotation/value[1]/string()
    let $result :=
        if (count($annotation/value) = 2 and boolean($annotation/value[2])) then
            util:expand($output)
        else
            $output
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

declare %private function test:cast-to-type($value as item(), $result as item()) {
    typeswitch ($result)
        case node() return
            util:parse("<r>" || $value || "</r>")/r/node()
        case xs:integer return
            xs:integer($value)
        case xs:int return
            xs:int($value)
        case xs:long return
            xs:long($value)
        case xs:decimal return
            xs:decimal($value)
        case xs:double return
            xs:double($value)
        case xs:float return
            xs:float($value)
        case xs:date return
            xs:date($value)
        case xs:dateTime return
            xs:dateTime($value)
        case xs:time return
            xs:time($value)
        case xs:boolean return
            xs:boolean($value)
        case xs:anyURI return
            xs:anyURI($value)
        default return
            string($value)
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

declare function test:to-html($output as element(testsuites)) {
    <div class="testresult">
    {
        for $suite in $output/testsuite
        return
            <section>
                <h2>{$suite/@package/string()}</h2>
                <table>
                {
                    for $case in $suite/testcase
                    return
                        test:print-testcase($case)
                }
                </table>
            </section>
    }
    </div>
};

declare %private function test:print-testcase($case as element(testcase)) {
    <tr class="{if ($case/failure) then 'fail' else 'pass'}">
        <td>{$case/@name/string()}</td>
        <td>
        {
            if ($case/failure) then
                ($case/failure/@message/string(), " Expected: ", $case/failure/text())
            else
                "OK"
        }
        </td>
    </tr>
};