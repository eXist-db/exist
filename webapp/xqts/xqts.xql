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

declare function xqts:path-to-uri($path as xs:string) as xs:string {
        if (starts-with($path, "/")) then
            concat("file://", $path)
        else
            concat("file:///", $path)
};

declare function xqts:create-progress-file($testCount as xs:int) as empty() {
    let $results := xdb:store("/db/XQTS", "progress.xml", 
        <progress total="{$testCount}" done="0" failed="0" passed="0" error="0"/>)
    return ()
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

declare function xqts:create-collections($group as element(catalog:test-group)) as node() {
    let $rootColl := xdb:create-collection("/db/XQTS", "test-results")
    let $ancestors := reverse(($group/ancestor::catalog:test-group, $group))
    let $collection := xqts:create-collections($rootColl, $ancestors, "/db/XQTS/test-results")
    let $results := xdb:store($collection, "results.xml", <test-result failed="0" passed="0" error="0"/>)
    return
        doc($results)
};

declare function xqts:create-collections($parent as object,
    $pathElements as element()+, $currentPath as xs:string) 
as object {
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
   let $path := concat( $xqts:XQTS_HOME, "Queries/XQuery/", $case/@FilePath, $query-name, ".xq" )
   let $xq-string := util:file-read($path, "UTF-8")
   return $xq-string
};

declare function xqts:print-result($test-name as xs:string, $passed as xs:boolean, $query as xs:string, 
    $result as item()*, $expected as item()*, $case as element(catalog:test-case)) as element() {
    <test-case name="{$test-name}" result="{if ($passed) then 'pass' else 'fail'}" dateRun="{util:system-time()}" print="print-result">
    {
        if (not($passed)) then (
            <result>{$result}</result>,
            if ($expected instance of element() and count($expected/*) > 10) then
            	<expected truncated="">{$expected/*[position() < 10]}</expected>
            else
                <expected>{$expected}</expected>,
            (: element {$case/catalog:input-file/@variable} { xqts:getInputValue($case) }, :)
            <query>{$query}</query>
        ) else ()
    }
    </test-case>
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

declare function xqts:check-output($query as xs:string, $result as item()*, $case as element(catalog:test-case)) {
  let $all-results :=
    for $output in $case/catalog:output-file
    return
        (: Expected only an error, but still got a result :)
        if (fn:exists($case/catalog:expected-error) and fn:empty($case/catalog:output-file)) then
            <test-case name="{$case/@name}" result="fail" dateRun="{util:system-time()}" print="expected-error-and-no-output">
                   <expected-error>{string-join($case/catalog:expected-error/text(), ";")}</expected-error>
                   <result>{$result}</result>
                   <query>{$query}</query>
            </test-case>
        (: Comparison method: "Text" :)
        else if ($output/@compare eq "Text") then
            let $text := util:file-read(concat($xqts:XQTS_HOME, "ExpectedTestResults/", $case/@FilePath,
                "/", $output/text()), "UTF-8")
            let $test := xqts:normalize-and-expand($text) eq xqts:normalize-text($result)
            return
                xqts:print-result($case/@name, $test, $query, $result, $text, $case)
        (: Comparison method: "XML" :)
        else if ($output/@compare eq "XML") then
            util:catch("java.lang.Exception",
                let $filePath := concat($xqts:XQTS_HOME, "ExpectedTestResults/", $case/@FilePath,
                    $output/text())
                let $expected := doc(xdb:store("/db", "temp.xml", xs:anyURI($filePath), "text/xml"))
                let $test := xdiff:compare($expected, $result)
                return
                    xqts:print-result($case/@name, $test, $query, $result, $expected, $case),
                (: Handle unexpected exceptions :)
                <test-case name="{$case/@name}" result="fail" dateRun="{util:system-time()}" print="xml-compare-unexpected-exception">
                   <exception>Exception while loading expected result: {$util:exception-message}</exception>
                   <query>{$query}</query>
                </test-case>
            )
        (: Comparison method: "Fragment" :)
        else if ($output/@compare eq "Fragment") then
            util:catch("java.lang.Exception",
                let $filePath := concat($xqts:XQTS_HOME, "ExpectedTestResults/", $case/@FilePath, $output/text())
                let $expectedFrag := util:file-read($filePath, "UTF-8")
                let $xmlFrag := concat("<f>", $expectedFrag, "</f>")
                let $expected := doc(xdb:store("/db", "temp.xml", $xmlFrag, "text/xml"))
                (:
                let $log := util:log("DEBUG", ("Frag stored: ", $xmlFrag))
                let $doh := doc(xdb:store("/db", $output, $xmlFrag, "text/xml"))
		:)
                let $test := xdiff:compare($expected, <f>{$result}</f>)
                return
                    xqts:print-result($case/@name, $test, $query, <f>{$result}</f>, $expected, $case),
                (: Handle unexpected exceptions :)
                <test-case name="{$case/@name}" result="fail" dateRun="{util:system-time()}" print="fragment-compare-unexpected-exception">
                   <exception>Exception while loading expected result fragment: {$util:exception-message}</exception>
                   <query>{$query}</query>
                </test-case>
            )
        (: Comparison method: "Inspect" :)
        (: A text compare is sufficient in many test cases :)
        else if ($output/@compare eq "Inspect") then
            let $text := util:file-read(concat($xqts:XQTS_HOME, "ExpectedTestResults/", $case/@FilePath,
                "/", $output/text()), "UTF-8")
            let $test := xqts:normalize-text($text) eq xqts:normalize-text($result)
            return
                xqts:print-result($case/@name, $test, $query, $result, $text, $case)
            
        (: Don't know how to compare :)
      else
        <test-case name="{$case/@name}" result="error" dateRun="{util:system-time()}" print="unknown-comparison-method">
          <error test="{$case/@name}">Unknown comparison method: {$output/@compare}.</error>
          <query>{$query}</query>
	</test-case>
    (: Use this let clause to get an update insert failure from the 5314th 
       test and onward in xqts:report-progress(). in-memory-temp-frag corrpution
       reproducible in xqts, who could imagine... /ljo :)
    (: let $passed := $all-results//test-case[@result eq 'pass'] :)
    let $passed :=
      if (fn:exists($all-results[2])) then
	let $result-frag := concat("<f>", fn:string-join($all-results, ""), "</f>")
	let $results-doc := doc(xdb:store("/db", "temp-res.xml", $result-frag, "text/xml"))
        return $results-doc//test-case[@result eq 'pass']
      else ()
    return
      if (fn:exists($passed)) then
	$passed[1]
      else if (fn:exists($all-results[1])) then
	$all-results[1]
      else
        (: Expected runtime-exception, but got a result :)
      if (fn:exists($case//catalog:expected-error) and fn:empty($case//catalog:output-file) and $case/@scenario eq "runtime-error") then
        <test-case name="{$case/@name}" result="fail" dateRun="{util:system-time()}" print="expected-error-and-no-output">
          <expected-error>{string-join($case/catalog:expected-error/text(), ";")}</expected-error>
          <result>{$result}</result>
          <query>{$query}</query>
        </test-case>
      else
	let $log-all := util:log("DEBUG", ("Unhandled error, no result for: ", xs:string($case/@name)))
	return 
	  <test-case name="{$case/@name}" result="error" dateRun="{util:system-time()}" print="unhandled-error">
    	    <error test="{$case/@name}">Cannot handle: {$case/@name}.</error>
            <query>{$query}</query>
          </test-case>

};

declare function xqts:get-variable($case as element(catalog:test-case), 
	$name as xs:string) as item()* {
	let $path := concat( $xqts:XQTS_HOME, "Queries/XQuery/", $case/@FilePath, $name, ".xq" )
	let $xq-string := util:file-read($path, "UTF-8")
	return
		if (empty($xq-string)) then
			()
		else
			util:eval($xq-string)
};

declare function xqts:run-test-case( $testCase as element(catalog:test-case)) as item()* {
   let $query := xqts:get-query($testCase)
   let $result := 
       util:catch("java.lang.Exception",
			let $context :=
				<static-context>
				    { xqts:compute-specific-static-context(string($testCase/@name)) }
           			{               			
               			for $input in $testCase/catalog:input-file
               			return
                   			<variable name="{$input/@variable}">{xqts:get-input-value($input)}</variable>,
                		for $var in $testCase/catalog:input-query
                		return
                    		<variable name="{$var/@variable}">
                        		{xqts:get-variable($testCase, $var/@name)}
                    		</variable>
           			}
       			</static-context>
           let $result :=
               util:eval-with-context($query, $context, false(), xqts:get-context-item($testCase/catalog:contextItem))
           return
               xqts:check-output($query, $result, $testCase),
	     (: Only expected exception and got exception.
	        No check on if it is the right exception, though. :)
           if (fn:exists($testCase//catalog:expected-error) and fn:empty($testCase//catalog:output-file)) then
               <test-case name="{$testCase/@name}" result="pass" dateRun="{util:system-time()}" print="expected-exception-got-exception">
                   <exception>{$util:exception-message}</exception>
                   <expected-error>{string-join($testCase//catalog:expected-error/text(),";")}</expected-error>
                   <query>{$query}</query>
               </test-case>
           else
	     (: Expected either output or exception. :)
	     if (fn:exists($testCase//catalog:expected-error) and fn:exists($testCase//catalog:output-file)) then
               <test-case name="{$testCase/@name}" result="pass" dateRun="{util:system-time()}" print="exception-or-output">
                   <exception>{$util:exception-message}</exception>
                   <expected-error>{string-join($testCase//catalog:expected-error/text(),";")}</expected-error>
		   {
		     for $output in $testCase//catalog:output-file
		     return <expected>{
                     let $filePath := concat($xqts:XQTS_HOME, "ExpectedTestResults/", $testCase/@FilePath, $output/text())
                     return util:file-read($filePath, "UTF-8")
                     }</expected>
		   }
                   <query>{$query}</query>
               </test-case>
           else if (fn:empty($testCase//catalog:expected-error) and fn:exists($testCase//catalog:output-file)) then
 	     (: Expected output, but got exception. :)
               <test-case name="{$testCase/@name}" result="fail" dateRun="{util:system-time()}" print="expected-output-got-exception">
                   <exception>{$util:exception-message}</exception>
		   {
		     for $output in $testCase//catalog:output-file
		     return <expected>{
                     let $filePath := concat($xqts:XQTS_HOME, "ExpectedTestResults/", $testCase/@FilePath, $output/text())
                     return util:file-read($filePath, "UTF-8")
                     }</expected>
		   }
                   <query>{$query}</query>
               </test-case>
	     else 
 	     (: Don't know what is expected but got here anyway. :)
               <test-case name="{$testCase/@name}" result="fail" dateRun="{util:system-time()}" print="unhandled-result">
                   <exception>{$util:exception-message}</exception>
                   <expected-error>{string-join($testCase//catalog:expected-error/text(),";")}</expected-error>
		   {
		     if (fn:exists($testCase//catalog:output-file)) then
		     for $output in $testCase//catalog:output-file
		     return <expected>{
                     let $filePath := concat($xqts:XQTS_HOME, "ExpectedTestResults/", $testCase/@FilePath, $output/text())
                     return util:file-read($filePath, "UTF-8")
                     }</expected>
		     else ()
		   }
                   <query>{$query}</query>
               </test-case>
       )
       let $log := if (fn:empty($result)) then util:log("DEBUG", ("FAILURE in-memory-frag in test: ", xs:string($testCase/@name))) else ()
   return $result
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

declare function xqts:run-single-test-case($case as element(catalog:test-case),
    $resultRoot as element()?) as empty() {
    let $result := xqts:run-test-case($case)
    return (
        update insert $result into $resultRoot,
        let $added := $resultRoot/test-case[@name = $case/@name]
       let $log := if (fn:empty($added)) then util:log("DEBUG", ("FAILURE in-memory-frag in test: ", xs:string($case/@name))) else ()
        return
            if ($added) then xqts:report-progress($added)
            else ()
    )
};

declare function xqts:run-test-group($group as element(catalog:test-group)) as empty() {
    (: Create the collection hierarchy for this group and get the results.xml doc to 
        append to. :)
    let $resultsDoc := xqts:create-collections($group)
    let $log-resdoc := util:log("DEBUG", ("Creating resdoc collection: ", $resultsDoc))
    let $tests := $group/catalog:test-case
(:
[
    	not(starts-with(@name, 'Constr-inscope-10')) and 
    	not(starts-with(@name, 'Constr-inscope-12')) and 
    	not(starts-with(@name, 'Constr-namespace-22'))
] 
:)
    return (
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

declare function xqts:get-input-value($input as element(catalog:input-file)) as item()* {
   if ($input eq "emptydoc") then
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

declare function xqts:overall-result() {
    let $passed := count(//test-case[@result = "pass"])
    let $failed := count(//test-case[@result = "fail"])
    let $error := count(//test-case[@result = "error"])
    return
        <test-result failed="{$failed}" passed="{$passed}"
	    error="{$error}"
            percentage="{$passed div ($passed + $failed)}"/>
};

declare function xqts:compute-specific-static-context($testCaseName as xs:string) as element()* {
    (        
        if ($testCaseName eq "fn-current-time-4") then
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

let $group := request:get-parameter('group', ())
return
    if ($group) then
        xqts:test-group($group)
    else
        ()
