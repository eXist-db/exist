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

declare namespace stats="http://exist-db.org/xquery/profiling";

declare variable $test:TEST_NAMESPACE := "http://exist-db.org/xquery/xqsuite";

declare variable $test:UNKNOWN_ASSERTION := QName($test:TEST_NAMESPACE, "no-such-assertion");
declare variable $test:WRONG_ARG_COUNT := QName($test:TEST_NAMESPACE, "wrong-number-of-arguments");
declare variable $test:TYPE_ERROR := QName($test:TEST_NAMESPACE, "type-error");

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
            let $modFunctions := filter($functions, function($func) {
                    namespace-uri-from-QName(function-name($func)) = $module
                })
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
                            failures="{count($results/failure)}" pending="{count($results/skipped)}"
                            tests="{count($results)}" time="{$elapsed}">
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
    if($meta/annotation[ends-with(@name,  ":pending")])then
      test:print-result($meta, (), <report>{
        element pending {
          $meta/annotation[ends-with(@name,  ":pending")]/value ! text()
        }
      }</report>)
    else
      let $argsAnnot := $meta/annotation[matches(@name, ":args?")][not(preceding-sibling::annotation[1][matches(@name, ":args?")])]
      return
          if ($argsAnnot) then
              $argsAnnot ! test:test($func, $meta, .)
          else
              test:test($func, $meta, ())
};

(:~
 : The main function for running a single test. Executes the test function
 : and compares the result against each assertXXX annotation.
 :)
declare %private function test:test($func as function(*), $meta as element(function), $firstArg as element(annotation)?) {
    let $args := test:get-run-args($firstArg)
    let $assertions := test:get-assertions($meta, $firstArg)
    let $assertError := $assertions[contains(@name, ":assertError")]
    return
        if (exists($assertions)) then
            try {
                let $result := test:call-test($func, $meta, $args)
                let $assertResult := test:check-assertions($assertions, $result)
                return
                    if ($assertError) then
                        test:print-result($meta, $result,
                            <report>
                                <failure message="Expected error {$assertError/value/string()}."
                                    type="failure-error-code-1"/>
                            </report>
                        )
                    else
                        test:print-result($meta, $result, $assertResult)
            } catch * {
                if ($assertError) then
                    if ($assertError/value and not(contains($err:code, $assertError/value/string())
                        or matches($err:description, $assertError/value/string())))then
                        test:print-result($meta, (),
                            <report>
                                <failure message="Expected error {$assertError/value/string()}, got: {$err:code} {$err:description}"
                                    type="failure-error-code-1"/>
                            </report>
                        )
                    else
                        test:print-result($meta, (), ())
                else
                    test:print-result($meta, (),
                        <report>
                            <error type="{$err:code}" message="{$err:description}"/>
                        </report>
                    )
            }
        else
            ()
};

declare function test:enable-tracing($meta as element(function)) {
    let $statsAnno := $meta//annotation[contains(@name, ":stats")]
    return
        if (exists($statsAnno)) then (
            system:clear-trace(),
            system:enable-tracing(true(), false()),
            true()
        ) else
            false()
};

(:~
 : Get all %assertXXX annotations of the function. If %args is used multiple times,
 : assertions apply to the result of running the function with the parameters given
 : in the preceding %args.
 :)
declare %private function test:get-assertions($meta as element(function), $firstArg as element(annotation)?) {
    if ($firstArg) then
        let $nextBlock := 
            $firstArg/following-sibling::annotation[matches(@name, ":args?")]
                [preceding-sibling::annotation[1][contains(@name, ":assert")]][1]
        return
            if ($nextBlock) then
                $firstArg/following-sibling::annotation[contains(@name, ":assert")]
                intersect
                $nextBlock/preceding-sibling::annotation[contains(@name, ":assert")]
            else
                $firstArg/following-sibling::annotation[contains(@name, ":assert")]
    else
        $meta/annotation[contains(@name, ":assert")]
};

(:~
 : Collect %test:arg and %test:args for the current run.
 :)
declare %private function test:get-run-args($firstArg as element(annotation)?) {
    if ($firstArg) then
        let $nextBlock := 
            $firstArg/following-sibling::annotation[matches(@name, ":args?")]
                [preceding-sibling::annotation[1][contains(@name, ":assert")]][1]
        return (
            $firstArg,
            if ($nextBlock) then
                $firstArg/following-sibling::annotation[matches(@name, ":args?")]
                intersect
                $nextBlock/preceding-sibling::annotation
            else
                $firstArg/following-sibling::annotation[matches(@name, ":args?")]
        )
    else
        ()
};

(:~
 : Map any arguments from the %args or %arg annotations into function parameters and evaluate
 : the resulting function. 
 :)
declare %private function test:call-test($func as function(*), $meta as element(function), $args as element(annotation)*) {
    let $funArgs :=
        if ($args[1]/@name = "test:args") then
            test:map-arguments($args/value/string(), $meta/argument)
        else
            test:map-named-arguments($meta/argument, $args)
    return
        test:apply($func, $meta, $funArgs)
};

(:~
 : Map a sequence of %arg annotations to named function parameters.
 :)
declare %private function test:map-named-arguments($funcArgs as element(argument)*, $testArgs as element(annotation)*) {
    let $mappedArgs :=
        for $arg in $funcArgs
        let $argName := $arg/@var/string()
        let $testArg := $testArgs[value[1] = $argName]
        return
            if (empty($testArg)) then
                error($test:WRONG_ARG_COUNT, "No matching %test:arg found for parameter " || $argName)
            else
                test:map-named-argument(subsequence($testArg/value, 2), $arg)
    return
        if (count($mappedArgs) != count($funcArgs)) then
            error(
                $test:WRONG_ARG_COUNT, 
                "The number of arguments specified via test:arg must match the arguments of the function to test"
            )
        else
            $mappedArgs
};

declare %private function test:map-named-argument($testArgs as element(value)*, $funcArg as element(argument)) {
    let $data :=
        try {
            test:cast($testArgs, $funcArg)
        } catch * {
            error($test:TYPE_ERROR, "Failed to cast annotation arguments to required target type " || $funcArg/@type)
        }
    return
        (: If we need to return a sequence of values, enclose them into a closure function :)
        function() { $data }
};

(:~
 : For %args: transform each annotation parameter into the type required for the function parameter.
 :)
declare %private function test:map-arguments($testArgs as xs:string*, $funcArgs as element(argument)*) {
    if (exists($testArgs)) then
        if (count($testArgs) != count($funcArgs)) then
            error(
                $test:WRONG_ARG_COUNT, 
                "The number of arguments specified in test:args must match the arguments of the function to test"
            )
        else
            map-pairs(function($targ as xs:string, $farg as element(argument)) {
                let $data := test:cast($targ, $farg)
                return
                    function() { $data }
            }, $testArgs, $funcArgs)
    else
        ()
};

declare %private function test:cast($targs as xs:string*, $farg as element(argument)) {
    for $targ in $targs
    return
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
};

declare function test:apply($func as function(*), $meta as element(function), $args as item()*) {
    let $trace := test:enable-tracing($meta)
    let $userAnno := $meta/annotation[contains(@name, ":user")]
    let $result :=
        if ($userAnno) then
            let $user := $userAnno/value[1]/string()
            let $pass := $userAnno/value[2]/string()
            return
                system:as-user($user, $pass, test:apply($func, $args))
        else
            test:apply($func, $args)
    return
        if ($trace) then
            (: Get trace output and filter out stats :)
            let $traceOutput :=
                (system:trace(), system:clear-trace(), system:enable-tracing(false()))
            return
                element { node-name($traceOutput) } {
                    $traceOutput/stats:*[not(starts-with(@source, "org.exist") or contains(@source, "xqsuite.xql"))]
                }
        else
            $result
};

(:~
 : Call the given function with parameters taken from $args.
 :)
declare %private function test:apply($func as function(*), $args as item()*) {
    switch (count($args))
        case 0 return
            $func()
        case 1 return
            $func($args[1]())
        case 2 return
            $func($args[1](), $args[2]())
        case 3 return
            $func($args[1](), $args[2](), $args[3]())
        case 4 return
            $func($args[1](), $args[2](), $args[3](), $args[4]())
        case 5 return
            $func($args[1](), $args[2](), $args[3](), $args[4](), $args[5]())
        case 6 return
            $func($args[1](), $args[2](), $args[3](), $args[4](), $args[5](), $args[6]())
        case 7 return
            $func($args[1](), $args[2](), $args[3](), $args[4](), $args[5](), $args[6](), $args[7]())
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
            case "assertEqualsPermutation" return
                test:assertEqualsPermutation($annotation/value/string(), $result)
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

(:~
 : Check for equality of the function's result with the value in the annotation.
 : This function transforms the result to a string before checking for equality.
 :)
declare %private function test:assertEqualsPermutation($values as item()*, $result as item()*) as element(report)? {
    if (exists($values)) then
        if (count($values) eq count($result)) then
            let $tests :=
                for $item in $result
                return
                    some $value in $values satisfies test:equals($item, $value)
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
    $nodes ! test:normalize-node(.)
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
    let $prolog :=
        if ($result instance of element()*) then
            let $namespaces := fold-left($result/descendant-or-self::*, map:new(),
                function ($namespaces as map(*), $xml as element()) {
                    map:new(($namespaces,
                	    for $prefix in in-scope-prefixes($xml)
                	    where $prefix != "" and $prefix != "xml"
                	    return
                	        map:entry($prefix, namespace-uri-for-prefix($prefix, $xml))
                    ))
                }
            )
            return
                string-join(
                    for $prefix in map:keys($namespaces)
                    return
                        "declare namespace " || $prefix || "='" || $namespaces($prefix) || "';",
                    " "
                )
        else
            ()
    let $xr :=
        test:checkXPathResult(
            if (matches($expr, "^\s*/")) then
                util:eval($prolog || "$result" || $expr)
            else
                util:eval($prolog || $expr)
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
        $output/testsuite !
            <section>
                <h2>{@package/string()}</h2>
                <table>
                {
                    for $case in testcase
                    return
                        test:print-testcase($case)
                }
                </table>
            </section>
    }
    </div>
};

declare %private function test:print-testcase($case as element(testcase)) {
    <tr class="{if ($case/failure or $case/error) then 'fail' else 'pass'}">
        <td>{$case/@name/string()}</td>
        <td>
        {
            if ($case/failure) then
                ($case/failure/@message/string(), " Expected: ", $case/failure/text())
            else if ($case/error) then
                ($case/error/@type/string(), " ", $case/error/@message/string())
            else
                "OK"
        }
        </td>
    </tr>
};