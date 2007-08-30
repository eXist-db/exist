xquery version "1.0";

declare namespace xqts="http://exist-db.org/xquery/xqts";
declare namespace catalog="http://www.w3.org/2005/02/query-test-XQTSCatalog";

declare namespace props="java:java.io.File";

import module namespace util="http://exist-db.org/xquery/util";
import module namespace xdb="http://exist-db.org/xquery/xmldb";
import module namespace xdiff="http://exist-db.org/xquery/xmldiff"
at "java:org.exist.xquery.modules.xmldiff.XmlDiffModule";
import module namespace request="http://exist-db.org/xquery/request";
declare namespace system="http://exist-db.org/xquery/system";

declare option exist:output-size-limit "-1";

(: $Id$ :)

(:~  ----------------------------------------------------------------------------
     W3C XQuery Test Suite
     
     This is the main module for running the XQTS on eXist. You can either
     run the whole test suite, a specific test group or a single test case.
     
     Setup:
     
     * Make sure the XmlDiff module is registered in conf.xml
     
     * Edit config.xml and change basedir to point to the directory into
     which you unzipped the XQTS sources. Note: it needs to be an URI. 
     
     * Create a collection /db/XQTS in the database.
     
     * From the XQTS directory, upload XQTSCatalog.xml into the created
     collection.
     
     * Upload the "TestSources" directory so the source docs can be found
     in /db/XQTS/TestSources.
     
     * Run this script with the client.
     -------------------------------------------------------------------------- :)
declare variable $xqts:CONFIG := xqts:initialize();
declare variable $xqts:XQTS_HOME := xqts:path-to-uri($xqts:CONFIG/basedir/text());

declare function xqts:initialize() as element() {
    let $home := system:get-exist-home()
    let $path := concat($home, "/webapp/xqts")
    let $collection := xdb:create-collection("/db", "XQTS")
    let $config0 := doc("/db/XQTS")/config
    let $config :=
        if ($config0) then
            $config0
        else
            let $stored := xdb:store-files-from-pattern("/db/XQTS", $path, "*.xml", "text/xml")
            return
                doc("/db/XQTS/config.xml")/config
    let $xqtsHome := $config/basedir/text()
    let $catalog0 := doc("/db/XQTS/XQTSCatalog.xml")
    let $catalog :=
        if ($catalog0) then $catalog0
        else
            xdb:store($collection, "XQTSCatalog.xml", 
                xs:anyURI(xqts:path-to-uri(concat($xqtsHome, "XQTSCatalog.xml"))))
    let $sources0 := collection("/db/XQTS/TestSources")
    let $sources :=
        if ($sources0) then
            $sources0
        else
            let $sourceCol := xdb:create-collection("/db/XQTS", "TestSources")
            return
                xdb:store-files-from-pattern($sourceCol, concat($xqtsHome, "TestSources"), "*.xml", "text/xml")
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

declare function xqts:create-collections($parent as object,
    $pathElements as element()+, $currentPath as xs:string) as object {
    let $next := $pathElements[last()]
    let $remainder := subsequence($pathElements, 1, count($pathElements) - 1)
    let $newColl := xdb:create-collection($parent, $next/@name)
    return
        if ($remainder) then
            xqts:create-collections($newColl, $remainder, concat($currentPath, "/", $next/@name))
        else
            $newColl
};

declare function xqts:get-query($case as element(catalog:test-case)) {
    let $query-name := $case//catalog:query/@name
    let $filePath := concat( $xqts:XQTS_HOME, "Queries/XQuery/", $case/@FilePath, $query-name, ".xq" )
    let $xq-string := util:file-read($filePath, "UTF-8")
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

declare function xqts:get-variable($case as element(catalog:test-case), $varName as xs:string) as item()* {
    let $filePath := concat($xqts:XQTS_HOME, "Queries/XQuery/", $case/@FilePath, $varName, ".xq")
    let $xq-string := util:file-read($filePath, "UTF-8")
    return
        if (empty($xq-string)) then
            ()
        else
            util:eval($xq-string)
};

declare function xqts:compute-specific-static-context($testCaseName as xs:string) as element()* {
    (        
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
    if (starts-with($path, "/")) then
        concat("file://", $path)
    else
        concat("file:///", $path)
};

declare function xqts:normalize-text($result as item()*) as xs:string {
    let $str := string-join(for $i in $result return string($i), " ")
    return
        (: Remove leading and trailing whitespace :)
        replace(replace($str, "^\s+", ""), "\s+$", "")
};

declare function xqts:normalize-and-expand($text as item()*) as xs:string {
	replace(replace(replace(xqts:normalize-text($text), "&amp;amp;", "&amp;", "m"), "&amp;lt;", "&lt;"),
		"&amp;gt;", "&gt;")
};

declare function xqts:get-expected-results($testCase as element(catalog:test-case)) as element()* {
    (
        for $output at $pos in $testCase/catalog:output-file
        return
            let $comparison :=
                if ($output/@compare eq "Inspect") then
                    (: introduce a per-test/output position return value:)
                    "Text"
                (: obvious wrong comparison methods :)
                else if ($testCase/@name eq "copynamespace-2") then
                    "XML"
                (: ForExprType057 fails : why ? :)
                else if ($testCase/@name eq "ForExprType059") then
                    "XML"
                else if ($testCase/@name eq "ForExprType060") then
                    "XML"
                else if ($testCase/@name eq "Constr-inscope-1") then
                    "XML"
                else if ($testCase/@name eq "Constr-inscope-2") then
                    "XML"
                else if ($testCase/@name eq "Constr-inscope-3") then
                    "XML"
                else if ($testCase/@name eq "Constr-inscope-4") then
                    "XML"
                else                
                    $output/@compare
            let $outputFilePath := concat($xqts:XQTS_HOME, "ExpectedTestResults/", $testCase/@FilePath,
                $output/text())
            return
                <expected-result compare="{$comparison}">
                {
                    if ($comparison eq "Text") then
                        xqts:normalize-and-expand(util:file-read($outputFilePath, "UTF-8"))
                    else if ($comparison eq "XML") then                    
                        util:catch(
                            "java.lang.Exception",
                            doc(xdb:store("/db/XQTS/temp", substring-before($output/text(), "."), xs:anyURI($outputFilePath), "text/xml")),
                            (: Handle unexpected exceptions :)
                            util:log("ERROR", concat("Exception while loading expected result: ", $util:exception-message))
                        )
                    else if ($comparison eq "Fragment") then
                        let $xmlFrag := concat("<f>", util:file-read($outputFilePath, "UTF-8"), "</f>")
                        return 
                            util:catch(
                                "java.lang.Exception",
                                doc(xdb:store("/db/XQTS/temp", substring-before($output/text(), "."), $xmlFrag, "text/xml")),                            
                                (: Handle unexpected exceptions :)
                                util:log("ERROR", concat("Exception while loading expected result fragment: ", $util:exception-message))
                            )
                    else
                        util:log("ERROR", concat("Unknown comparison method: ", $comparison))
                }
                </expected-result>                
    ,
        (: Inject Saxon's results here :)
        if ($testCase/@name eq "CastAs672") then
            <expected-result from="Saxon8.9J" compare="Text">true</expected-result> 
        else
            ()
    )
};

declare function xqts:execute-test-case($testCase as element(catalog:test-case)) as element()? {
    let $expectedResults := xqts:get-expected-results($testCase)
    let $query := xqts:get-query($testCase)
    let $formatedResult := 
        util:catch(
            "java.lang.Exception",
            let $context :=
                <static-context>
                    { 
                        xqts:compute-specific-static-context(string($testCase/@name)) 
                    }
                    {                           
                        for $input in $testCase/catalog:input-file
                        return
                            <variable name="{$input/@variable}">{xqts:get-input-value($input)}</variable>,
                            for $var in $testCase/catalog:input-query
                            return
                                <variable name="{$var/@variable}">
                                {
                                    xqts:get-variable($testCase, $var/@name)
                                }
                                </variable>
                    }
                </static-context>
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
    let $testsPassed :=
        for $expectedResult in $expectedResults
        return
            if ($expectedResult/@compare eq "Text") then
                $expectedResult/string() eq xqts:normalize-text($result)
            else if ($expectedResult/@compare eq "XML") then
                xdiff:compare($expectedResult/*, $result)
            else if ($expectedResult/@compare eq "Fragment") then
                xdiff:compare($expectedResult/*, <f>{$result}</f>)
            (: unlikely :)    
            else
                false()
    let $passed := boolean(count(index-of($testsPassed, true())) gt 0)
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
    $resultRoot as element()?) as empty() {
    let $result := xqts:execute-test-case($case)
    return 
        update insert $result into $resultRoot,
        let $added := $resultRoot/test-case[@name = $case/@name]
        let $log := 
            if (fn:empty($added)) then 
                util:log("DEBUG", ("FAILURE in-memory-frag in test: ", xs:string($case/@name))) 
            else 
                ()
        return
            if ($added) then 
                xqts:report-progress($added)
            else 
                ()
};

declare function xqts:run-test-group($group as element(catalog:test-group)) as empty() {
    (: Create the collection hierarchy for this group and get the results.xml doc to append to. :)
    let $resultsDoc := xqts:create-collections($group)
    let $log-resdoc := util:log("DEBUG", ("Creating resdoc collection: ", $resultsDoc))
    let $tests := $group/catalog:test-case
    return 
        (
            (: Execute the test cases :)
            for $test in $tests
                let $log := util:log("DEBUG", ("Running test case: ", string($test/@name)))
                return
                    xqts:run-single-test-case($test, $resultsDoc/test-result),
                    if (fn:exists($tests) and fn:exists($resultsDoc/test-result)) then 
                        xqts:finish($resultsDoc/test-result)
                    else
                        xdb:remove(util:collection-name($resultsDoc), util:document-name($resultsDoc)),
                        (: Execute tests in child groups :)
                        for $childGroup in $group/catalog:test-group
                            let $log := util:log("DEBUG", ("Entering group: ", string($childGroup/@name)))
                            return
                                xqts:run-test-group($childGroup)
        )
};

declare function xqts:test-single($name as xs:string) as element() {
    let $test := //catalog:test-case[@name = $name]
    return
        if ($test) then
            let $resultsDoc := xqts:create-collections($test/parent::catalog:test-group)
            let $dummy := xqts:run-single-test-case($test, $resultsDoc/test-result)
            return
                $resultsDoc/test-result
        else
            <error>Test case not found: {$name}.</error>
};

declare function xqts:test-group($groupName as xs:string) as empty() {
    let $group := //catalog:test-group[@name = $groupName]
    return (
        xqts:create-progress-file(count($group//catalog:test-case)),
        xqts:run-test-group($group)
    )
};

declare function xqts:test-all() as empty() {
    for $test in /catalog:test-suite/catalog:test-group
    return
        xqts:test-group($test/@name)
};

declare function xqts:report-progress($test-case as element()) as empty() {
    let $result := string($test-case/@result)
    let $progress := doc("/db/XQTS/progress.xml")/progress
    let $counter := $progress/@done
    return (
        update value $counter with xs:int($counter + 1),
        if ($result eq 'fail') then
            update value $progress/@failed with xs:int($progress/@failed + 1)
        else if ($result eq 'error') then
            update value $progress/@error with xs:int($progress/@error + 1)
        else
            update value $progress/@passed with xs:int($progress/@passed + 1)
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

let $group := request:get-parameter('group', ())
return
    if ($group) then
        xqts:test-group($group)
    else
        ()
