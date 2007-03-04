declare namespace xqts="http://exist-db.org/xquery/xqts";
declare namespace catalog="http://www.w3.org/2005/02/query-test-XQTSCatalog";

declare namespace props="java:java.io.File";

import module namespace util="http://exist-db.org/xquery/util";
import module namespace xdb="http://exist-db.org/xquery/xmldb";
import module namespace xdiff="http://exist-db.org/xquery/xmldiff"
at "java:org.exist.xquery.modules.xmldiff.XmlDiffModule";
import module namespace request="http://exist-db.org/xquery/request";
declare namespace system="http://exist-db.org/xquery/system";

(:~ ----------------------------------------------------------------------------------------
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
     ------------------------------------------------------------------------------------------- :)
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
        <progress total="{$testCount}" done="0" failed="0" passed="0"/>)
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
        else
            update value $progress/@passed with xs:int($progress/@passed + 1)
    )
};

declare function xqts:create-collections($group as element(catalog:test-group)) as node() {
    let $rootColl := xdb:create-collection("/db/XQTS", "test-results")
    let $ancestors := reverse(($group/ancestor::catalog:test-group, $group))
    let $collection := xqts:create-collections($rootColl, $ancestors, "/db/XQTS/test-results")
    let $results := xdb:store($collection, "results.xml", <test-result failed="0" passed="0"/>)
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
    <test-case name="{$test-name}" result="{if ($passed) then 'pass' else 'fail'}" dateRun="{util:system-time()}">
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
    let $output := $case/catalog:output-file[last()]
    return
        (: Expected an error, but got a result :)
        if (exists($case/catalog:expected-error)) then
            <test-case name="{$case/@name}" result="fail" dateRun="{util:system-time()}">
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
                <test-case name="{$case/@name}" result="fail" dateRun="{util:system-time()}">
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
                let $log := util:log("DEBUG", ("Frag stored: ", $xmlFrag))
                let $expected := doc(xdb:store("/db", "temp.xml", $xmlFrag, "text/xml")) 
                let $doh := doc(xdb:store("/db", $case/@name, $xmlFrag, "text/xml")) 
                let $test := xdiff:compare($expected, <f>{$result}</f>)
                return
                    xqts:print-result($case/@name, $test, $query, <f>{$result}</f>, $expected, $case),
                (: Handle unexpected exceptions :)
                <test-case name="{$case/@name}" result="fail" dateRun="{util:system-time()}">
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
            <error test="{$case/@name}">Unknown comparison method: {$output/@compare}.</error>
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
           if ($testCase//catalog:expected-error) then
               <test-case name="{$testCase/@name}" result="pass" dateRun="{util:system-time()}">
                   <exception>{$util:exception-message}</exception>
                   <expected-error>{string-join($testCase//catalog:expected-error/text(),";")}</expected-error>
                   <query>{$query}</query>
               </test-case>
           else
               <test-case name="{$testCase/@name}" result="fail" dateRun="{util:system-time()}">
                   <exception>{$util:exception-message}</exception>                  
					<!-- TODO : insert expected result here -->    
                   <query>{$query}</query>
               </test-case>
       )
   return $result
};

declare function xqts:finish($result as element()) as empty() {
    let $passed := count($result//test-case[@result = 'pass'])
    let $failed := count($result//test-case[@result = 'fail'])
    return (
        update value $result/@passed with xs:int($passed),
        update value $result/@failed with xs:int($failed)
    )
};

declare function xqts:run-single-test-case($case as element(catalog:test-case),
    $resultRoot as element()?) as empty() {
    let $result := xqts:run-test-case($case)
    return (
        update insert $result into $resultRoot,
        let $added := $resultRoot/test-case[@name = $case/@name]
        return
            if ($added) then xqts:report-progress($added)
            else ()
    )
};

declare function xqts:run-test-group($group as element(catalog:test-group)) as empty() {
    (: Create the collection hierarchy for this group and get the results.xml doc to 
        append to. :)
    let $resultsDoc := xqts:create-collections($group)
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
        if ($tests) then 
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
    return
        <test-result failed="{$failed}" passed="{$passed}"
            percentage="{$passed div ($passed + $failed)}"/>
};

let $group := request:get-parameter('group', ())
return
    if ($group) then
        xqts:test-group($group)
    else
        ()
