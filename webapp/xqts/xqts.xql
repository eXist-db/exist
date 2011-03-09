xquery version "1.0";

declare namespace xqts="http://exist-db.org/xquery/xqts";
declare namespace catalog="http://www.w3.org/2005/02/query-test-XQTSCatalog";
declare namespace hack="http://exist-db.org/xquery/xqts/hacked-test-cases";

declare namespace props="java:java.io.File";

import module namespace file="http://exist-db.org/xquery/file";
import module namespace util="http://exist-db.org/xquery/util";
import module namespace xdb="http://exist-db.org/xquery/xmldb";
import module namespace xdiff="http://exist-db.org/xquery/xmldiff" at "java:org.exist.xquery.modules.xmldiff.XmlDiffModule";
import module namespace request="http://exist-db.org/xquery/request";
import module namespace system="http://exist-db.org/xquery/system";

declare option exist:output-size-limit "-1";

(: $Id$ :)

(:~  ----------------------------------------------------------------------------
     W3C XQuery Test Suite
     
     This is the main module for running the XQTS on eXist. You can either
     run the whole test suite, a specific test group or a single test case.
     
     Setup:

     * Run EXIST_HOME/webapp/xqts/build.xml and follow the instructions.
     -------------------------------------------------------------------------- :)

declare variable $xqts:CONFIG := xqts:initialize();
declare variable $xqts:XQTS_HOME := xqts:path-to-uri($xqts:CONFIG/basedir/text());
declare variable $xqts:IN_MEMORY := ($xqts:CONFIG/mode/text() = "memory");

declare function xqts:initialize() as element() {
    let $home := system:get-exist-home()
    let $path := concat($home, "/webapp/xqts")
    let $collection := xdb:create-collection("/db", "XQTS")
    let $config0 := if (doc-available("/db/XQTS/config.xml")) then doc("/db/XQTS/config.xml")/config else ()
    let $config :=
        if ($config0) then
            $config0
        else
            let $stored := xdb:store-files-from-pattern("/db/XQTS", $path, "*.xml", "text/xml")
            return
                if (doc-available("/db/XQTS/config.xml")) then doc("/db/XQTS/config.xml")/config else ()
    let $xqtsHome := $config/basedir/text()
    let $catalog0 := doc("/db/XQTS/XQTSCatalog.xml")
    let $catalog :=
        if ($catalog0) then $catalog0
        else
            xdb:store($collection, "XQTSCatalog.xml", 
                xs:anyURI(xqts:path-to-uri(concat($xqtsHome, "XQTSCatalog.xml"))))
    let $sources0 := collection("/db/XQTS/TestSources")
    let $sourceCol := xdb:create-collection("/db/XQTS", "TestSources")
    let $collection1 := xdb:create-collection("/db/XQTS/TestSources", "collection1")
    let $collection2 := xdb:create-collection("/db/XQTS/TestSources", "collection2")
    let $sources :=
        if ($sources0) then
            $sources0
        else
            xdb:store-files-from-pattern($sourceCol, concat($xqtsHome, "TestSources"), "*.xml", "text/xml")
    let $docs := 
        (
            (: since XQTS URIs are resolved against /db/XQTS/TestSources :)
            xdb:copy($sourceCol, "/db/XQTS/TestSources/collection1", "bib.xml"),
            xdb:copy($sourceCol, "/db/XQTS/TestSources/collection1", "reviews.xml"),
            xdb:copy($sourceCol, "/db/XQTS/TestSources/collection2", "bib.xml"),
            xdb:copy($sourceCol, "/db/XQTS/TestSources/collection2", "reviews.xml"),
            xdb:copy($sourceCol, "/db/XQTS/TestSources/collection2", "books.xml"),            
            xdb:store($collection, "hacked-tests.xml",
                xs:anyURI(xqts:path-to-uri(
                        concat("file:///", system:get-module-load-path(), "/hacked-tests.xml")
                    ))) 
        )   
    return
        $config
};

declare function xqts:create-progress-file($testCount as xs:int) as empty() {
    let $results := xdb:store("/db/XQTS", "progress.xml", 
        <progress total="{$testCount}" done="0" failed="0" passed="0" error="0"/>)
    return ()
};

declare function xqts:create-collections($group as element(catalog:test-group)) as node() {
    let $rootColl := xdb:create-collection("/db/XQTS", "test-results")
    let $temp := xdb:create-collection("/db/XQTS", "temp") 
    let $ancestors := reverse(($group/ancestor::catalog:test-group, $group))
    let $collection := xqts:create-collections($rootColl, $ancestors, "/db/XQTS/test-results")
    let $results := xdb:store($collection, "results.xml", <test-result failed="0" passed="0" error="0"/>)
    return
        doc($results)
};

declare function xqts:create-collections($parent as xs:string,
    $pathElements as element()+, $currentPath as xs:string) as xs:string {
    let $next := $pathElements[last()]
    let $remainder := subsequence($pathElements, 1, count($pathElements) - 1)
    let $newColl := xdb:create-collection($parent, $next/@name)
    return
        if ($remainder) then
            xqts:create-collections($newColl, $remainder, concat($currentPath, "/", $next/@name))
        else
            $newColl
};

declare function xqts:read($filePath as xs:string) {
	system:as-user("admin", (), file:read($filePath, "UTF-8"))
};

declare function xqts:get-query($case as element(catalog:test-case)) {
    let $query-name := $case//catalog:query/@name
    let $filePath := concat( $xqts:XQTS_HOME, "Queries/XQuery/", $case/@FilePath, $query-name, ".xq" )
    let $xq-string := xqts:read($filePath)
    return 
        $xq-string
};

declare function xqts:get-input-value($input as element(catalog:input-file)) as item()* {
   if ($input eq "emptydoc") then
       ()
   else 
       let $source := root($input)//catalog:source[@ID = $input/text()]
       return
           if (empty($source)) then
               concat("no input found: ", $input/text())
           else
               doc(concat("/db/XQTS/", $source/@FileName))   
};

declare function xqts:get-input-value-uri($input as element(catalog:input-file)) as item()* {
    if ($input eq "emptydoc") then
       ()
    else 
        let $source := root($input)//catalog:source[@ID = $input/text()]
        return concat( $xqts:XQTS_HOME, "/", $source/@FileName )
};

declare function xqts:get-variable($case as element(catalog:test-case), $varName as xs:string) as item()* {
    let $filePath := concat($xqts:XQTS_HOME, "Queries/XQuery/", $case/@FilePath, $varName, ".xq")
    let $xq-string := xqts:read($filePath)
    return
        if (empty($xq-string)) then
            ()
        else
            util:eval($xq-string)
};

declare function xqts:compute-specific-static-context($testCaseName as xs:string) as element()* {
    (
        <unbind-namespace uri="http://exist.sourceforge.net/NS/exist"/>       
    , 
        if ($testCaseName eq "ForExpr013") then
            <output-size-limit value="-1"/>
        else if ($testCaseName eq "fn-current-time-4") then
            (: arbitrary date :)                                
            <current-dateTime value="2005-12-05T13:38:03.455-05:00"/>  
        else if ($testCaseName eq "fn-current-time-6") then
            (: arbitrary date :)                                
            <current-dateTime value="2005-12-05T13:38:18.059-05:00"/>  
        else if ($testCaseName eq "fn-current-time-7") then
            (: arbitrary date :)                                
            <current-dateTime value="2005-12-05T13:38:18.059-05:00"/>              
        else if ($testCaseName eq "fn-current-time-10") then
            (: arbitrary date :)                                
            <current-dateTime value="2005-12-05T13:38:18.09-05:00"/>
        else if (starts-with($testCaseName, "fn-current-time-")) then
            (: arbitrary date :)                                
            <current-dateTime value="2005-12-05T10:15:03.408-05:00"/>
        else if ($testCaseName eq "fn-current-dateTime-6") then
            <current-dateTime value="2005-12-05T17:10:00.312-05:00"/>
        (: bloody lower-case ! :)
        else if ($testCaseName eq "fn-current-datetime-7") then
            <current-dateTime value="2005-12-05T17:10:00.312-05:00"/>            
        else if ($testCaseName eq "fn-current-dateTime-10") then
            <current-dateTime value="2005-12-05T17:10:00.344-05:00"/> 
        else if ($testCaseName eq "fn-current-dateTime-21") then
            <current-dateTime value="2005-12-05T17:10:00.453-05:00"/>   
        else if ($testCaseName eq "fn-current-dateTime-24") then
            <current-dateTime value="2005-12-05T17:10:00.469-05:00"/>                                   
        else                        
            <current-dateTime value="2005-12-05T17:10:00.203-05:00"/>
    ,
        if (starts-with($testCaseName, "modules-") or starts-with($testCaseName, "K-ModuleImport-")) then
            (
            <mapModule namespace="http://www.w3.org/TestModules/test1" 
                uri="{xs:anyURI('xmldb:exist:///db/XQTS/TestSources/test1-lib.xq')}"/>,            
            <mapModule namespace="http://www.w3.org/TestModules/test2" 
                uri="{xs:anyURI('xmldb:exist:///db/XQTS/TestSources/test2-lib.xq')}"/>,            
            <mapModule namespace="http://www.w3.org/TestModules/module1" 
                uri="{xs:anyURI('xmldb:exist:///db/XQTS/TestSources/module1-lib.xq')}"/>,
            <mapModule namespace="http://www.w3.org/TestModules/module2" 
                uri="{xs:anyURI('xmldb:exist:///db/XQTS/TestSources/module2-lib.xq')}"/>,
            <mapModule namespace="http://www.w3.org/TestModules/defs" 
                uri="{xs:anyURI('xmldb:exist:///db/XQTS/TestSources/moduleDefs-lib.xq')}"/>,
            <mapModule namespace="http://www.w3.org/TestModules/diffns" 
                uri="{xs:anyURI('xmldb:exist:///db/XQTS/TestSources/modulesdiffns-lib.xq')}"/>            
            )
        else
            ()
    ,
        if (matches($testCaseName,"^fn-implicit-timezone-.*$") or
            matches($testCaseName,"^K-ContextImplicitTimezoneFunc-.*$")) then      
            <implicit-timezone value="-PT5H"/>
        else 
            ()
    )
};

declare function xqts:get-context-item($input as element(catalog:contextItem)?) as item()* {
    if (empty($input)) then
       ()
    else (
        let $source := root($input)//catalog:source[@ID = $input/text()]
        return
            if (empty($source)) then
                concat("no input found: ", $input/text())
            else
                doc(concat("/db/XQTS/", $source/@FileName))
    )
};

declare function xqts:path-to-uri($path as xs:string) as xs:string {
    if (starts-with($path, "file:///")) then
        replace($path, "\\", "/")
    else if (starts-with($path, "/")) then
        concat("file://", replace($path, "\\", "/"))
    else
        concat("file:///", replace($path, "\\", "/"))
};

declare function xqts:normalize-text($result as item()*) as xs:string {
    let $str := string-join(for $i in $result return string($i), " ")
    return
        (: Remove leading and trailing whitespace :)
        replace(replace(replace($str, "^\s+", ""), "\s+$", ""), "&#x0D;&#x0A;", "&#x0A;")
};

declare function xqts:normalize-and-expand($text as item()*) as xs:string {
	replace(replace(replace(xqts:normalize-text($text), "&amp;amp;", "&amp;", "m"), "&amp;lt;", "&lt;"),
		"&amp;gt;", "&gt;")
};

declare function xqts:get-expected-results($testCase as element(catalog:test-case), $inMemory as xs:boolean) as element()* { 
    let $testName := $testCase/@name   
    let $hackedTest := /hack:test-cases/hack:test-case[@name = $testName]    
    return
    (
        for $output in $testCase/catalog:output-file
        return
            let $compare :=
                if (exists($hackedTest/@compare)) then 
                    $hackedTest/@compare
                else if ($output/@compare eq "Inspect") then
                    (: "Text" by default. OK in most if not all the cases :)
                    "Text"                   
                else                
                    $output/@compare        
            let $outputFilePath := concat($xqts:XQTS_HOME, "ExpectedTestResults/", $testCase/@FilePath,
                $output/text())
            return
                <expected-result compare="{$compare}">                
                {
                    if ($compare eq "Text") then
                        xqts:normalize-and-expand(xqts:read($outputFilePath))
                    else if ($compare eq "UnnormalizedText") then
                        xqts:normalize-and-expand(xqts:read($outputFilePath))
                    else if ($compare eq "TextAsXML") then
                        xqts:normalize-and-expand(xqts:read($outputFilePath))
                    else if ($compare eq "XML") then
                        if ($inMemory) then
                            util:parse(xqts:read($outputFilePath))
                        else
                            util:catch(
                                "java.lang.Exception",
                                doc(xdb:store("/db/XQTS/temp", substring-before($output/text(), "."), xs:anyURI($outputFilePath), "text/xml")),
                                (: Handle unexpected exceptions :)
                                util:log("ERROR", concat("Exception while loading expected result: ", $util:exception-message))
                            )
                    else if ($compare eq "Fragment") then
                        let $xmlFrag := concat("<f>", xqts:read($outputFilePath), "</f>")
                        return
                            if ($inMemory) then
                                util:parse($xmlFrag)
                            else
                                util:catch(
                                    "java.lang.Exception",
                                    doc(xdb:store("/db/XQTS/temp", substring-before($output/text(), "."), $xmlFrag, "text/xml")),                            
                                    (: Handle unexpected exceptions :)
                                    util:log("ERROR", concat("Exception while loading expected result fragment: ", $util:exception-message))
                                )
                    else
                        util:log("ERROR", concat("Unknown comparison method: ", $compare))
                }
                </expected-result>              
    ,
        (: due to a possible bug in the MemTreeBuilder, the parent element will be copied ! :) 
        if (exists($hackedTest/hack:expected-result)) then
            $hackedTest/hack:expected-result        
        else
            ()
    )
};

declare function xqts:execute-test-case($testCase as element(catalog:test-case), $inMemory as xs:boolean) as element()? {
    let $context :=
        <static-context>
            { 
                xqts:compute-specific-static-context(string($testCase/@name)) 
            }
            {                           
                for $input in $testCase/catalog:input-file
                return
                    if ($inMemory) then
                        <variable name="{$input/@variable}" source="{xqts:get-input-value-uri($input)}"/>
                    else
                        <variable name="{$input/@variable}">{xqts:get-input-value($input)}</variable>,
                for $var in $testCase/catalog:input-query
                return
                    let $variable := xqts:get-variable($testCase, $var/@name)
                    return
                        <variable name="{$var/@variable}" type="{util:get-sequence-type($variable)}">{$variable}</variable>,
                for $input in $testCase/catalog:input-URI
                return
                    if ($input eq "works") then
                        <variable name="{$input/@variable}" type="xs:anyURI">{xs:anyURI("/db/XQTS/TestSources/works.xml")}</variable>
                    else if ($input eq "works-mod") then
                        <variable name="{$input/@variable}" type="xs:anyURI">{xs:anyURI("/db/XQTS/TestSources/works-mod.xml")}</variable>
                    else
                        <variable name="{$input/@variable}" type="xs:anyURI">{xs:anyURI(concat("/db/XQTS/TestSources/", $input))}</variable>
            }
            {
                for $input in $testCase/catalog:defaultCollection
                return
                    <staticallyKnownDocuments>{concat("/db/XQTS/TestSources/", $input)}</staticallyKnownDocuments>
            }
        </static-context>                   
    let $query := xqts:get-query($testCase)
    let $expectedResults := xqts:get-expected-results($testCase, $inMemory)
    let $formatedResult := 
        util:catch(
            "java.lang.Exception",            
            let $raw_result := util:eval-with-context($query, $context, false(), xqts:get-context-item($testCase/catalog:contextItem))
            return
                xqts:compute-result($testCase, $query, $raw_result, $expectedResults),

            (: Exception processing :)
            (: Only expected exception and got exception. No check on if it is the right exception, though. :)
            if (fn:exists($testCase//catalog:expected-error) and empty($expectedResults)) then
               <test-case name="{$testCase/@name}" result="pass" dateRun="{util:system-time()}" print="expected-exception-got-exception">
                    (: TODO : check exception code :)
                    <exception>{$util:exception-message}</exception>
                    {
                        for $expected-error in $testCase//catalog:expected-error
                        return
                            <expected-error>{$expected-error/text()}</expected-error>
                    }
                    <query>{$query}</query>
               </test-case>
            (: Expected either output or exception. :)
            else if (fn:exists($testCase//catalog:expected-error) and fn:exists($expectedResults)) then
                <test-case name="{$testCase/@name}" result="pass" dateRun="{util:system-time()}" print="expected-exception-or-output">
                    (: TODO : check exception code :)
                    <exception>{$util:exception-message}</exception>
                    {
                        for $expectedResult in $expectedResults
                        return 
                            $expectedResult
                    }
                    {
                        for $expected-error in $testCase//catalog:expected-error
                        return
                            <expected-error>{$expected-error/text()}</expected-error>
                    }
                    <query>{$query}</query>
                </test-case>
            (: Expected output, but got exception. :)
            else if (fn:empty($testCase//catalog:expected-error) and fn:exists($testCase//catalog:output-file)) then
                <test-case name="{$testCase/@name}" result="fail" dateRun="{util:system-time()}" print="expected-output-got-exception">
                    <exception>{$util:exception-message}</exception>
                    {
                        for $expectedResult in $expectedResults
                        return 
                            $expectedResult
                    }
                    <query>{$query}</query>
                </test-case>
            (: Don't know what is expected but got here anyway. :)
            else 
                <test-case name="{$testCase/@name}" result="fail" dateRun="{util:system-time()}" print="unhandled-error">
                    <exception>{$util:exception-message}</exception>
                    {
                        for $expectedResult in $expectedResults
                        return 
                            $expectedResult
                    }
                    {
                        for $expected-error in $testCase//catalog:expected-error
                        return
                            <expected-error>{$expected-error/text()}</expected-error>
                    }
                    <query>{$query}</query>
                </test-case>
        )
        let $log := 
            if (fn:empty($formatedResult)) then 
                util:log("DEBUG", ("Unable to generate a formated result: ", xs:string($testCase/@name))) 
            else 
                ()
    return $formatedResult
};

declare function xqts:compute-result($testCase as element(catalog:test-case), $query as xs:string, $result as item()*, $expectedResults as element()* ) {
    let $passed := some $expectedResultTest in (
        for $expectedResult in $expectedResults
        return
            let $comparisonMethod := $expectedResult/@compare
            return
                if ($comparisonMethod eq "Text") then
                    (: don't use text() because () neq "" :)
                    $expectedResult/string() eq xqts:normalize-text($result)                
                else if ($comparisonMethod eq "UnnormalizedText") then
                    (: don't use text() because () neq "" :)
                    (: join the results in one single string :)
                    $expectedResult/string() eq string-join($result, "")
                else if ($comparisonMethod eq "TextAsXML") then
                    xdiff:compare($expectedResult/*, util:eval($result))                              
                else if ($comparisonMethod eq "XML") then
                    xdiff:compare($expectedResult/*, $result)
                else if ($comparisonMethod eq "Fragment") then
                    xdiff:compare($expectedResult/*, <f>{$result}</f>)
                (: unlikely :)    
                else
                    false()
    ) satisfies $expectedResultTest
    return
        xqts:format-result($testCase, $passed, $query, $result, $expectedResults)
};

declare function xqts:format-result($testCase as element(catalog:test-case), $passed as xs:boolean, $query as xs:string, 
    $result as item()*, $expectedResults as item()*) as element() {
    <test-case name="{$testCase/@name}" result="{if ($passed) then 'pass' else 'fail'}" dateRun="{util:system-time()}" print="result-without-exception">
        <result>{$result}</result>
        {
            for $expectedResult in $expectedResults
            return 
                $expectedResult
        }
        {
            for $expected-error in $testCase//catalog:expected-error
            return
                <expected-error>{$expected-error/text()}</expected-error>
        }
        <query>{$query}</query>            
    </test-case>
};

declare function xqts:run-single-test-case($case as element(catalog:test-case),
    $resultRoot as element()?, $inMemory as xs:boolean) as item()* {
    let $result := xqts:execute-test-case($case, $inMemory)
    return            
        $result
};

declare function xqts:run-test-group($group as element(catalog:test-group), $inMemory as xs:boolean) as empty() {
    (: Create the collection hierarchy for this group and get the results.xml doc to append to. :)
    let $resultsDoc := xqts:create-collections($group)
    let $tests := $group/catalog:test-case
    return 
        (
            (: Execute the test cases :)
            let $singleTestResults :=
                for $test in $tests
                let $log := util:log("DEBUG", ("Running test case: ", string($test/@name)))
                return
                    xqts:run-single-test-case($test, $resultsDoc/test-result, $inMemory)
            let $null := xqts:report-progress($singleTestResults)
            return
                util:catch(
                   "java.lang.Exception",            
                   update insert $singleTestResults into $resultsDoc/test-result,
                   update insert
                        <test-case result="fail" dateRun="{util:system-time()}" print="unhandled-error">
                                <exception>Caught late exception while inserting result: {$util:exception-message}</exception>
                        </test-case>
                    into $resultsDoc/test-result
                ),
            
            if (fn:exists($tests) and fn:exists($resultsDoc/test-result)) then (
                xqts:finish($resultsDoc/test-result)
            ) else
                xdb:remove(util:collection-name($resultsDoc), util:document-name($resultsDoc)),
            (: Execute tests in child groups :)
            for $childGroup in $group/catalog:test-group
                let $log := util:log("DEBUG", ("Entering group: ", string($childGroup/@name)))
                return
                    xqts:run-test-group($childGroup, $inMemory)
        )
};

declare function xqts:test-single($name as xs:string, $inMemory as xs:boolean) as element() {
    let $test := //catalog:test-case[@name = $name]
    return
        if ($test) then
            let $resultsDoc := xqts:create-collections($test/parent::catalog:test-group)
            let $dummy := xqts:run-single-test-case($test, $resultsDoc/test-result, $inMemory)
            return
                $resultsDoc/test-result
        else
            <error>Test case not found: {$name}.</error>
};

declare function xqts:test-group($groupName as xs:string, $inMemory as xs:boolean) as empty() {
    let $group := //catalog:test-group[@name = $groupName]
    return (
        xqts:create-progress-file(count($group//catalog:test-case)),
        xqts:run-test-group($group, $inMemory)
    )
};

declare function xqts:test-all($inMemory as xs:boolean) as empty() {
    for $test in /catalog:test-suite/catalog:test-group
    return
        xqts:test-group($test/@name, $inMemory)
};

declare function xqts:report-progress($testResults as element()*) as empty() {
    let $fails := count($testResults[@result = 'fail'])
    let $errors := count($testResults[@result = 'error'])
    let $passed := count($testResults[@result = 'pass'])
    let $progress := if (doc-available("/db/XQTS/progress.xml")) then doc("/db/XQTS/progress.xml")/progress else ()
    let $counter := $progress/@done
    return (
        update value $counter with xs:int($counter + count($testResults)),
        update value $progress/@failed with xs:int($progress/@failed + $fails),
        update value $progress/@error with xs:int($progress/@error + $errors),
        update value $progress/@passed with xs:int($progress/@passed + $passed)
    )
};

declare function xqts:finish($result as element()) as empty() {
    let $passed := count($result//test-case[@result = 'pass'])
    let $failed := count($result//test-case[@result = 'fail'])
    let $error := count($result//test-case[@result = 'error'])
    return (
        update value $result/@passed with xs:int($passed),
        update value $result/@failed with xs:int($failed),
        update value $result/@error with xs:int($error)
    )
};

declare function xqts:overall-result() {
    let $passed := count(//test-case[@result = "pass"])
    let $failed := count(//test-case[@result = "fail"])
    let $error := count(//test-case[@result = "error"])
    return
        <test-result failed="{$failed}" passed="{$passed}"
	       error="{$error}"
            percentage="{$passed div ($passed + $failed)}"/>
};

let $group := request:get-parameter('group', ()),
    $mode := request:get-parameter('mode', xs:string($xqts:IN_MEMORY))
return
    if ($group) then
        xqts:test-group($group, xs:boolean($mode))
    else
        ()
