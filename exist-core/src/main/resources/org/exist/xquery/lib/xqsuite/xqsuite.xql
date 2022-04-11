(:
 : eXist-db Open Source Native XML Database
 : Copyright (C) 2001 The eXist-db Authors
 :
 : info@exist-db.org
 : http://www.exist-db.org
 :
 : This library is free software; you can redistribute it and/or
 : modify it under the terms of the GNU Lesser General Public
 : License as published by the Free Software Foundation; either
 : version 2.1 of the License, or (at your option) any later version.
 :
 : This library is distributed in the hope that it will be useful,
 : but WITHOUT ANY WARRANTY; without even the implied warranty of
 : MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 : Lesser General Public License for more details.
 :
 : You should have received a copy of the GNU Lesser General Public
 : License along with this library; if not, write to the Free Software
 : Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 :)
xquery version "3.1";

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

(: TODO(AR) temporarily disabled http-client due to circular dependency :)
(:
import module namespace http = "http://expath.org/ns/http-client";
:)
import module namespace map = "http://www.w3.org/2005/xpath-functions/map";
import module namespace util = "http://exist-db.org/xquery/util";
import module namespace system = "http://exist-db.org/xquery/system";
import module namespace xmldb = "http://exist-db.org/xquery/xmldb";

declare namespace err = "http://www.w3.org/2005/xqt-errors";
declare namespace exerr = "http://www.exist-db.org/xqt-errors/";
declare namespace stats = "http://exist-db.org/xquery/profiling";

declare variable $test:TEST_NAMESPACE := "http://exist-db.org/xquery/xqsuite";

declare variable $test:UNKNOWN_ASSERTION := QName($test:TEST_NAMESPACE, "no-such-assertion");
declare variable $test:WRONG_ARG_COUNT := QName($test:TEST_NAMESPACE, "wrong-number-of-arguments");
declare variable $test:TYPE_ERROR := QName($test:TEST_NAMESPACE, "type-error");
declare variable $test:UNKNOWN_ANNOTATION_VALUE_TYPE := QName($test:TEST_NAMESPACE, "unknown-annotation-value-type");

(:~
 : Main entry point into the module. Takes a sequence of function items.
 : Functions with known test annotations will be evaluated and their results
 : checked against the annotations.
 :
 : @param $functions a sequence of function items
 : @return an XML report (in xUnit format)
 :)
declare function test:suite($functions as function(*)+) {
    test:suite($functions, (), (), (), (), (), ())
};

(:~
 : Main entry point into the module. Takes a sequence of function items.
 : Functions with known test annotations will be evaluated and their results
 : checked against the annotations.
 :
 : @param $functions a sequence of function items
 : @param $test-ignored-function a function callback for reporting ignored tests
 : @param $test-started-function a function callback for reporting the start of a test
 : @param $test-failure-function a function callback for reporting the failure of a test
 : @param $test-assumption-failed-function a function callback for reporting the failure of a assumption within a test
 : @param $test-error-function a function callback for reporting an error when running a test
 : @param $test-finished-function a function callback for reporting the end of a test
 :
 : @return an XML report (in xUnit format)
 :)
declare function test:suite(
        $functions as function(*)+,
        $test-ignored-function as (function(xs:string) as empty-sequence())?,
        $test-started-function as (function(xs:string) as empty-sequence())?,
        $test-failure-function as (function(xs:string, map(xs:string, item()?), map(xs:string, item()?)) as empty-sequence())?,
        $test-assumption-failed-function as (function(xs:string, map(xs:string, item()?)?) as empty-sequence())?,
        $test-error-function as (function(xs:string, map(xs:string, item()?)?) as empty-sequence())?,
        $test-finished-function as (function(xs:string) as empty-sequence())?
) {
    let $modules := test:distinct-modules($functions)
    let $runner :=
        test:run-tests(
            ?, ?,
            $test-ignored-function, $test-started-function, $test-failure-function,
            $test-assumption-failed-function, $test-error-function, $test-finished-function
        )
    return
        <testsuites>
        {
            (: Run tests for each module in turn :)
            for $module in $modules
            let $modFunctions := filter($functions, function($func) {
                    namespace-uri-from-QName(function-name($func)) = $module
                })
            let $setup := test:call-func-with-annotation($modFunctions, "setUp", $test-error-function)
            let $result :=
                if (empty($setup) or $setup/self::ok) then
                    let $startTime := util:system-time()
                    let $results := test:function-by-annotation($modFunctions, "assert", $runner)
                    let $elapsed :=
                        util:system-time() - $startTime
                    return
                        <testsuite package="{$module}" timestamp="{util:system-dateTime()}"
                            tests="{count($results)}"
                            failures="{count($results/failure)}"
                            errors="{count($results/error)}"
                            pending="{count($results/pending)}"
                            time="{$elapsed}">
                            { $results }
                        </testsuite>
                else
                    <testsuite package="{$module}" timestamp="{util:system-dateTime()}"
                        errors="{count($functions)}">
                        {$setup/string()}
                    </testsuite>
            return (
                $result,
                test:call-func-with-annotation($modFunctions, "tearDown", $test-error-function)
            )[1]
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
declare %private function test:call-func-with-annotation(
        $functions as function(*)+,
        $annot as xs:string,
        $test-error-function as (function(xs:string, map(xs:string, item()?)?) as empty-sequence())?
) as element()? {
    test:function-by-annotation($functions, $annot, function($func, $meta) {
        try {
            (<ok/>, $func())[1]
        } catch * {
            if (not(empty($test-error-function))) then
                $test-error-function(
                    $annot,
                    map {
                        "code": $err:code,
                        "description": $err:description,
                        "value": $err:value,
                        "module": $err:module,
                        "line-number": $err:line-number,
                        "column-number": $err:column-number,
                        "additional": $err:additional,
                        "xquery-stack-trace": $exerr:xquery-stack-trace,
                        "java-stack-trace": $exerr:java-stack-trace
                    }
                )
            else ()
            ,
            <system-err>{$err:description}</system-err>
        }
    })
};

(:~
 : Calls the test function one or more times, depending on the number of
 : %args() annotations found. Each %arg annotation triggers one test run
 : using the supplied parameters.
 :)
declare %private function test:run-tests(
        $func as function(*),
        $meta as element(function),
        $test-ignored-function as (function(xs:string) as empty-sequence())?,
        $test-started-function as (function(xs:string) as empty-sequence())?,
        $test-failure-function as (function(xs:string, map(xs:string, item()?), map(xs:string, item()?)) as empty-sequence())?,
        $test-assumption-failed-function as (function(xs:string, map(xs:string, item()?)?) as empty-sequence())?,
        $test-error-function as (function(xs:string, map(xs:string, item()?)?) as empty-sequence())?,
        $test-finished-function as (function(xs:string) as empty-sequence())?
) {
    if ($meta/annotation[ends-with(@name,  ":pending")]) then
        (
            if (not(empty($test-ignored-function))) then
                $test-ignored-function(test:get-test-name($meta))
            else ()
            ,
            test:print-result(
                $meta,
                (),
                <report>{
                    element pending {
                        $meta/annotation[ends-with(@name,  ":pending")]/value ! text()
                    }
                }</report>
            )
        )
    else
        let $failed-assumptions := test:test-assumptions($meta, $test-assumption-failed-function)
        return
            if (not(empty($failed-assumptions))) then
                test:print-result(
                    $meta,
                    (),
                    <report>{
                        element assumptions {
                            for $failed-assumption in $failed-assumptions
                            return
                                element assumption {
                                    attribute name { replace($failed-assumption/@name, "[^:]+:(.+)", "$1") },
                                    $failed-assumption/value/text()
                                }
                        }
                    }</report>
                )
            else
                let $argsAnnot := $meta/annotation[matches(@name, ":args?")][not(preceding-sibling::annotation[1][matches(@name, ":args?")])]
                let $test := test:test($func, $meta, ?,
                        $test-started-function, $test-failure-function, $test-error-function, $test-finished-function)
                return
                    if ($argsAnnot) then
                        $argsAnnot ! $test(.)
                    else
                        $test(())
};

(:~
 : Tests the assumptions on the (test) function
 :
 : @param $meta the function description
 : @param $test-assumption-failed-function A callback for reporting the failure of assumptions
 :
 : @return Any assumption annotations where the asusmption did not hold true
 :)
declare
    %private
function test:test-assumptions(
        $meta as element(function),
        $test-assumption-failed-function as (function(xs:string, map(xs:string, item()?)?) as empty-sequence())?
) as element(annotation)* {
    let $assumption-annotations := $meta/annotation[matches(@name,  "[^:]+:assume.+")]
    return
        let $failed-assumption-annotations := $assumption-annotations ! test:test-assumption(., $test-assumption-failed-function)
        return
            (
                if (not(empty($test-assumption-failed-function))) then
                    $failed-assumption-annotations ! $test-assumption-failed-function(
                        test:get-test-name($meta),
                        map {
                            "name": ./string(@name),
                            "value": ./value/string()
                        }
                    )
                else ()
                ,
                $failed-assumption-annotations
            )
};

declare
    %private
function test:test-assumption(
        $assumption-annotation as element(annotation),
        $test-assumption-failed-function as (function(xs:string, map(xs:string, item()?)?) as empty-sequence())?
) as element(annotation)? {
    if (ends-with($assumption-annotation/@name, ":assumeInternetAccess")) then
        (: check for internet access :)
        try {
            let $uri := $assumption-annotation/value/text()
            return
                (: set a timeout of 3 seconds :)

                (: TODO(AR) temporarily disabled http-client due to circular dependency :)
                (:
                let $response := http:send-request(<http:request method="head" href="{$uri}" timeout="3"/>)[1]
                return
                :)
                () (: nothing failed :)
        } catch * {
            $assumption-annotation (: return the annotation as failed :)
        }
    else()
};

(:~
 : The main function for running a single test. Executes the test function
 : and compares the result against each assertXXX annotation.
 :)
declare %private function test:test(
        $func as function(*),
        $meta as element(function),
        $firstArg as element(annotation)?,
        $test-started-function as (function(xs:string) as empty-sequence())?,
        $test-failure-function as (function(xs:string, map(xs:string, item()?), map(xs:string, item()?)) as empty-sequence())?,
        $test-error-function as (function(xs:string, map(xs:string, item()?)?) as empty-sequence())?,
        $test-finished-function as (function(xs:string) as empty-sequence())?
) {
    let $args := test:get-run-args($firstArg)
    let $assertions := test:get-assertions($meta, $firstArg)
    let $assertError := $assertions[contains(@name, ":assertError")]
    return
        if (exists($assertions)) then
        (
            if (not(empty($test-started-function))) then
                $test-started-function(test:get-test-name($meta))
            else ()
            ,
            try {
                let $result := test:call-test($func, $meta, $args)
                let $assertResult := test:check-assertions($assertions, $result)
                return
                    if ($assertError) then
                    (
                        if (not(empty($test-failure-function))) then
                            $test-failure-function(
                                test:get-test-name($meta),
                                (: expected :)
                                map {
                                    "error": $assertError/value/string()
                                },
                                (: actual :)
                                map {
                                    "error": map {
                                        "value": $result
                                    }
                                }
                            )
                        else (),
                        test:print-result(
                            $meta,
                            $result,
                            <report>
                                <failure message="Expected error {$assertError/value/string()}."
                                    type="failure-error-code-1"/>
                                <output>{ $result }</output>
                            </report>
                        )
                    ) else (
                        if ($assertResult[failure] and not(empty($test-failure-function))) then
                            $test-failure-function(
                                test:get-test-name($meta),
                                (: expected :)
                                map {
                                    "value": test:expected-strings($assertResult)
                                },
                                (: actual :)
                                map {
                                    "result": test:actual-strings($assertResult)
                                }
                            )
                        else(),
                        test:print-result($meta, $result, $assertResult)
                    )
            } catch * {
                if ($assertError) then
                    if (
                        $assertError/value
                        and not(contains($err:code, $assertError/value/string())
                        or matches($err:description, $assertError/value/string()))
                    ) then
                    (
                        if (not(empty($test-failure-function))) then
                            $test-failure-function(
                                test:get-test-name($meta),
                                (: expected :)
                                map {
                                    "error": $assertError/value/string()
                                },
                                (: actual :)
                                map {
                                    "error": map {
                                        "code": $err:code,
                                        "description": $err:description,
                                        "value": $err:value,
                                        "module": $err:module,
                                        "line-number": $err:line-number,
                                        "column-number": $err:column-number,
                                        "additional": $err:additional,
                                        "xquery-stack-trace": $exerr:xquery-stack-trace,
                                        "java-stack-trace": $exerr:java-stack-trace
                                    }
                                }
                            )
                        else ()
                        ,
                        test:print-result($meta, (),
                            <report>
                                <failure message="Expected error {$assertError/value/string()}, got: {$err:code} {$err:description}"
                                    type="failure-error-code-1"/>
                            </report>
                        )
                    )
                    else
                        test:print-result($meta, (), ())
                else
                (
                    if (not(empty($test-error-function))) then
                        $test-error-function(
                            test:get-test-name($meta),
                            map {
                                "code": $err:code,
                                "description": $err:description,
                                "value": $err:value,
                                "module": $err:module,
                                "line-number": $err:line-number,
                                "column-number": $err:column-number,
                                "additional": $err:additional,
                                "xquery-stack-trace": $exerr:xquery-stack-trace,
                                "java-stack-trace": $exerr:java-stack-trace
                            }
                        )
                    else ()
                    ,
                    test:print-result(
                        $meta,
                        (),
                        <report>
                            <error type="{$err:code}" message="{$err:description}"/>
                        </report>
                    )
                )
            },
            if (not(empty($test-finished-function))) then
                $test-finished-function(test:get-test-name($meta))
            else ()
        )
        else
            ()
};

declare function test:expected-strings($report as element(report)+) {
    string-join(
        for $report-failure in $report/failure
        return
            string-join($report-failure/text(), ", ") || " (" || $report-failure/@message || ")"
        ,
        ", "
    )
};

declare function test:actual-strings($report as element(report)+) {
    fn:serialize($report[failure]/output/node(), map {
        "method": "adaptive",
        "item-separator": ","
    })
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
declare %private function test:get-run-args($firstArg as element(annotation)?) as element(annotation)* {
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
declare %private function test:call-test(
        $func as function(*),
        $meta as element(function),
        $args as element(annotation)*
) {
    let $funArgs :=
        if ($args[1]/@name = "test:args") then
            test:map-arguments($args/value, $meta/argument)
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

declare %private function test:map-named-argument($test-args as element(value)*, $func-arg as element(argument)) {
    let $data :=
        try {
            test:cast($test-args, $func-arg)
        } catch * {
            fn:error($test:TYPE_ERROR, "Failed to cast annotation arguments to required target type " || $func-arg/@type)
        }
    return
        (: If we need to return a sequence of values, enclose them into a closure function :)
        function() { $data }
};

(:~
 : For %args: transform each annotation parameter into the type required for the function parameter.
 :)
declare %private function test:map-arguments($test-args as element(value)*, $func-args as element(argument)*) {
    if (fn:not(fn:empty($test-args))) then
        if (fn:count($test-args) ne fn:count($func-args)) then
            fn:error(
                $test:WRONG_ARG_COUNT,
                "The number of arguments specified in test:args must match the arguments of the function to test"
            )
        else
            fn:for-each-pair($test-args, $func-args, function($targ as element(value), $farg as element(argument)) {
                function() {
                    test:cast($targ, $farg)
                }
            })
    else
        ()
};


(:~
 : Convert an annotation value into an XDM value.
 :
 : Annotation values can only be one of the following types: xs:string, xs:integer, xs:decimal or xs:double.
 :
 : @param $annotation-value the annotation value element as returned by util:inspect-function
 :
 : @return the atomic value of the annotation
 :)
declare %private function test:xdm-value-from-annotation-value($annotation-value as element(value)) {
    switch ($annotation-value/string(@type))
        case "xs:string"
        return
            xs:string($annotation-value/text())

        case "xs:integer"
        return
            xs:integer($annotation-value/text())

        case "xs:decimal"
        return
            xs:decimal($annotation-value/text())

        case "xs:double"
        return
            xs:double($annotation-value/text())

        default
        return
            fn:error(
                $test:UNKNOWN_ANNOTATION_VALUE_TYPE,
                "The annotation value has an unknown type"
            )
};

declare %private function test:cast(
    $annotation-arg-values as element(value)*,
    $function-argument as element(argument)
) {
    for $annotation-arg-value in $annotation-arg-values
    let $literal-value := test:xdm-value-from-annotation-value($annotation-arg-value)
    return
        switch (string($function-argument/@type))
            case "xs:string" return
                if ($literal-value instance of xs:string)
                then $literal-value
                else xs:string($literal-value)

            case "xs:integer" return
                if ($literal-value instance of xs:integer)
                then $literal-value
                else xs:integer($literal-value)

            case "xs:decimal" return
                if ($literal-value instance of xs:decimal)
                then $literal-value
                else xs:decimal($literal-value)

            case "xs:double" return
                if ($literal-value instance of xs:double)
                then $literal-value
                else xs:double($literal-value)

            case "xs:boolean"           return xs:boolean($literal-value)
            case "xs:anyURI"            return xs:anyURI($literal-value)

            case "xs:NCName"            return xs:NCName($literal-value)
            case "xs:QName"             return xs:QName($literal-value)

            case "xs:numeric"           return xs:numeric($literal-value)
            case "xs:int"               return xs:int($literal-value)
            case "xs:positiveInteger"   return xs:positiveInteger($literal-value)
            case "xs:negativeInteger"   return xs:negativeInteger($literal-value)
            case "xs:float"             return xs:float($literal-value)

            case "xs:time"              return xs:time($literal-value)
            case "xs:date"              return xs:date($literal-value)
            case "xs:dateTime"          return xs:dateTime($literal-value)
            case "xs:dateTimeStamp"     return xs:dateTimeStamp($literal-value)

            case "xs:duration"          return xs:duration($literal-value)
            case "xs:yearMonthDuration" return xs:yearMonthDuration($literal-value)
            case "xs:dayTimeDuration"   return xs:dayTimeDuration($literal-value)

            case "text()"               return text { $literal-value }
            case "element()"            return parse-xml($literal-value)/element()
            default                     return $literal-value
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

declare %private function test:get-test-name($meta as element(function)) as xs:string {
    let $nameAnnot := $meta/annotation[matches(@name, ":name")]
    return
        if ($nameAnnot) then
            $nameAnnot/value
        else
            replace($meta/@name, "^\w+:([^:]+)$", "$1")
};

(:~
 : Print out evaluation results for a given test.
 :)
declare %private function test:print-result($meta as element(function), $result as item()*,
        $assertResult as element(report)*) {
    <testcase name="{test:get-test-name($meta)}" class="{$meta/@name}">
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
declare %private function test:check-assertions(
        $assertions as element(annotation)*,
        $result as item()*
) as element(report)* {
    for $annotation in $assertions
    let $assert := replace($annotation/@name, "^\w+:(.*)$", "$1")
    return
        switch ($assert)
            case "assertEquals" return
                test:assertEquals($annotation/value, $result)
            case "assertEqualsPermutation" return
                test:assertEqualsPermutation($annotation/value, $result)
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
declare %private function test:assertEquals($values as element(value)*, $result as item()*) as element(report)? {
    if (fn:exists($values)) then
        if (fn:count($values) eq fn:count($result)) then
            let $tests := fn:for-each-pair($values, $result, test:equals#2)
            let $equal := every $test in $tests satisfies $test
            return
                if ($equal) then
                    ()
                else
                   <report>
                        <failure message="assertEquals failed."
                            type="failure-error-code-1">
                        { $values ! test:xdm-value-from-annotation-value(.) }
                        </failure>
                        <output>{ $result }</output>
                    </report>
        else
            <report>
                <failure message="assertEquals failed: wrong number of items returned by function. Expected: {fn:count($values)}. Got: {fn:count($result)}"
                    type="failure-error-code-1">
                { $values ! test:xdm-value-from-annotation-value(.) }
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
declare %private function test:assertEqualsPermutation($values as element(value)*, $result as item()*) as element(report)? {
    if (fn:exists($values)) then
        if (fn:count($values) eq fn:count($result)) then
            let $tests :=
                for $item in $result
                return
                    some $value in $values satisfies test:equals($value, $item)
            let $equal := every $test in $tests satisfies $test
            return
                if ($equal) then
                    ()
                else
                   <report>
                        <failure message="assertEqualsPermutation failed."
                            type="failure-error-code-1">
                        { $values ! test:xdm-value-from-annotation-value(.) }
                        </failure>
                        <output>{ $result }</output>
                    </report>
        else
            <report>
                <failure message="assertEqualsPermutation failed: wrong number of items returned by function. Expected: {fn:count($values)}. Got: {fn:count($result)}"
                    type="failure-error-code-1">
                { $values ! test:xdm-value-from-annotation-value(.) }
                </failure>
                <output>{ $result }</output>
            </report>
    else
        ()
};

declare %private function test:equals($annotation-value as element(value), $result as item()) as xs:boolean {
    let $normResult :=
        typeswitch ($result)
            case node() return
                test:normalize($result)
            default return
                $result
    let $value := test:xdm-value-from-annotation-value($annotation-value)
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
                type="failure-error-code-1"/>
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
                type="failure-error-code-1"/>
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
            <failure message="assertTrue failed."
                type="failure-error-code-1"/>
            <output>{ $result }</output>
        </report>
};

declare %private function test:safe-effective-boolean-value ($result as item()*) as xs:boolean {
    try {
        boolean($result)
    } catch err:FORG0006 {
        false()
    }
};

(:~
 : Check if the function caused an error.
 :)
declare %private function test:assertError($value as xs:string, $result as item()*) as element(report)? {
    if (test:safe-effective-boolean-value($result))
    then ()
    else
        <report>
            <failure message="assertError failed. Expected error {$value}"
                type="failure-error-code-1"/>
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
            <failure message="assertFalse failed."
                type="failure-error-code-1"/>
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
            let $namespaces := fold-left($result/descendant-or-self::*, map {},
                function ($namespaces as map(*), $xml as element()) {
                    map:merge(($namespaces,
                	    for $prefix in in-scope-prefixes($xml)
                	    where $prefix ne "xml"
                	    return
                	        map:entry($prefix, namespace-uri-for-prefix($prefix, $xml))
                    ))
                }
            )
            return
                string-join(
                    for $prefix in map:keys($namespaces)
                    return
                        if ($prefix eq "") then
                            "declare default element namespace '" || $namespaces($prefix) || "';"
                        else
                            "declare namespace " || $prefix || "='" || $namespaces($prefix) || "';",
                    " "
                )
        else
            ()
    let $eval-query :=
        if (matches($expr, "^\s*/")) then
            $prolog || "$result" || $expr
        else
            $prolog || $expr
    let $eval-result := util:eval($eval-query, false(), (), true())
    let $xr := test:checkXPathResult($eval-result)
    return
        if ($xr) then
            ()
        else
            <report>
                <failure message="assertXPath failed."
                    type="failure-error-code-1">{ $expr }</failure>
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
            parse-xml("<r>" || $value || "</r>")/r/node()

        case xs:integer           return xs:integer($value)
        case xs:positiveInteger   return xs:positiveInteger($value)
        case xs:negativeInteger   return xs:negativeInteger($value)
        case xs:int               return xs:int($value)
        case xs:long              return xs:long($value)
        case xs:decimal           return xs:decimal($value)
        case xs:double            return xs:double($value)
        case xs:float             return xs:float($value)
        case xs:numeric           return xs:numeric($value)

        case xs:date              return xs:date($value)
        case xs:dateTime          return xs:dateTime($value)
        case xs:time              return xs:time($value)

        case xs:duration          return xs:duration($value)
        case xs:yearMonthDuration return xs:yearMonthDuration($value)
        case xs:dayTimeDuration   return xs:dayTimeDuration($value)

        case xs:boolean           return xs:boolean($value)

        case xs:anyURI            return xs:anyURI($value)

        case xs:NCName            return xs:NCName($value)
        case xs:QName             return xs:QName($value)

        default return string($value)
};

(: Helper functions to be used by test modules :)

declare function test:mkcol($root as xs:string, $path as xs:string) as empty-sequence() {
    test:mkcol-recursive($root, tokenize($path, "/"))
};

declare %private function test:mkcol-recursive($collection, $components) as empty-sequence() {
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
