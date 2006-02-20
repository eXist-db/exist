declare namespace xqts="http://exist-db.org/xquery/xqts";
declare namespace catalog="http://www.w3.org/2005/02/query-test-XQTSCatalog";

import module namespace util="http://exist-db.org/xquery/util";
import module namespace xdb="http://exist-db.org/xquery/xmldb";
import module namespace xdiff="http://exist-db.org/xquery/xmldiff";

(:~ ----------------------------------------------------------------------------------------
     W3C XQuery Test Suite
     
     This is the main module for running the XQTS on eXist. You can either
     run the whole test suite, a specific test group or a single test case.
     
     Setup:
     
     * Make sure the XmlDiff module is registered in conf.xml
     
     * Change the $xqts:XQTS_HOME variable below to point to the directory into
     which you unzipped the XQTS sources. 
     
     * Create a collection /db/XQTS in the database.
     
     * From the XQTS directory, upload XQTSCatalog.xml into the created
     collection.
     
     * Upload the "TestSources" directory so the source docs can be found
     in /db/XQTS/TestSources.
     
     * Run this script with the client.
     ------------------------------------------------------------------------------------------- :)
declare variable $xqts:XQTS_HOME { "file:///d:/Data/XQTS/" };

declare function xqts:initialize() as empty() {
    let $output-coll := xdb:create-collection("/db/XQTS", "test-results")
    let $dummy := xdb:store("/db/XQTS/test-results", "results.xml", <test-result failed="0" passed="0"/>)
    return ()
};

declare function xqts:get-query($case as element(catalog:test-case)) {
   let $query-name := $case//catalog:query/@name
   let $path := concat( $xqts:XQTS_HOME, "Queries/XQuery/", $case/@FilePath, $query-name, ".xq" )
   let $xq-string := util:file-read($path)
   return $xq-string
};

declare function xqts:print-result($test-name as xs:string, $passed as xs:boolean, $query as xs:string, 
    $result as item()*, $expected as item()*, $case as element(catalog:test-case)) as element() {
    <test-case name="{$test-name}" result="{if ($passed) then 'pass' else 'fail'}">
    {
        if (not($passed)) then (
            <result>{$result}</result>,
            <expected>{$expected}</expected>,
            (: element {$case/catalog:input-file/@variable} { xqts:getInputValue($case) }, :)
            <query>{$query}</query>
        ) else ()
    }
    </test-case>
};

declare function xqts:check-output($query as xs:string, $result as item()*, $case as element(catalog:test-case)) {
    let $output := $case/catalog:output-file[last()]
    return
        if ($output/@compare eq "Text") then
            let $text := util:file-read(concat($xqts:XQTS_HOME, "ExpectedTestResults/", $case/@FilePath,
                "/", $output/text()))
            let $test := $text eq string($result[1])
            return
                xqts:print-result($case/@name, $test, $query, $result, $text, $case)
        else if ($output/@compare eq "XML") then
            let $filePath := concat($xqts:XQTS_HOME, "ExpectedTestResults/", $case/@FilePath,
                $output/text())
            let $expected := doc(xdb:store("/db", "temp.xml", xs:anyURI($filePath), "text/xml"))
            let $test := xdiff:compare($expected, $result)
            return
                xqts:print-result($case/@name, $test, $query, $result, $expected, $case)
        else if ($output/@compare eq "Fragment") then
            let $filePath := concat($xqts:XQTS_HOME, "ExpectedTestResults/", $case/@FilePath, $output/text())
            let $xmlFrag := concat("<f>", util:file-read($filePath), "</f>")
            let $log := util:log("DEBUG", ("Frag stored: ", $xmlFrag))
            let $expected := doc(xdb:store("/db", "temp.xml", $xmlFrag, "text/xml")) 
            let $test := xdiff:compare($expected, <f>{$result}</f>)
            return
                xqts:print-result($case/@name, $test, $query, $result, $expected, $case)
        else
            <error test="{$case/@name}">Unknown comparison method: {$output/@compare}.</error>
};

declare function xqts:run-test-case( $case as element(catalog:test-case)) as item()* {
   let $query := xqts:get-query($case)   
   let $input-value := xqts:getInputValue($case)
   let $context :=
       <static-context>
           <variable name="{$case/catalog:input-file/@variable}">{$input-value}</variable>
       </static-context>
   let $result := 
       util:catch("org.exist.xquery.XPathException",
           let $result :=
               util:eval-with-context($query, $context, false())
           return
               xqts:check-output($query, $result, $case),
           if (exists($case/catalog:expected-error)) then
               <test-case name="{$case/@name}" result="pass"/>
           else
               <test-case name="{$case/@name}" result="fail">
                   <exception>{$util:exception-message}</exception>
                   <query>{$query}</query>
               </test-case>
       )
   return $result
};

declare function xqts:finish() as empty() {
    let $result := doc("/db/XQTS/test-results/results.xml")/test-result
    let $passed := count($result//test-case[@result = 'pass'])
    let $failed := count($result//test-case[@result = 'fail'])
    return (
        update value $result/@passed with xs:int($passed),
        update value $result/@failed with xs:int($failed)
    )
};

declare function xqts:run-single-test-case($case as element(catalog:test-case),
    $group as element()?) as empty() {
    let $result := xqts:run-test-case($case)
    return
        if ($group) then
            update insert $result into $group
        else
            update insert $result into doc("/db/XQTS/test-results/results.xml")/test-result
};

declare function xqts:run-test-group($group as element(catalog:test-group),
    $parent as element()?) as empty() {
    update insert <test-group name="{$group/@name}">{$group/catalog:GroupInfo}</test-group>
        into $parent,
    let $newGroup := $parent/test-group[last()]
    return (
        for $test in $group/catalog:test-case
        let $log := util:log("DEBUG", ("Running test case: ", string($test/@name)))
        return
            xqts:run-single-test-case($test, $newGroup),
        for $childGroup in $group/catalog:test-group
        let $log := util:log("DEBUG", ("Entering group: ", string($childGroup/@name)))
        return
            xqts:run-test-group($childGroup, $newGroup)
    )
};

declare function xqts:test-single($name as xs:string) as empty() {
    let $test := //catalog:test-case[@name = $name]
    return
        xqts:run-single-test-case($test, collection("/db/XQTS/test-results")/test-result)
};

declare function xqts:test-group($groupName as xs:string) as empty() {
    let $group := //catalog:test-group[@name = $groupName]
    return
        xqts:run-test-group($group, collection("/db/XQTS/test-results")/test-result)
};

declare function xqts:test-all() as empty() {
    for $test in /catalog:test-suite/catalog:test-group
    return
        xqts:test-group($test/@name)
};

declare function xqts:getInputValue($case as element(catalog:test-case)) as item()* {
	let $input := $case/catalog:input-file
  	return
       if ($input eq "emptydoc") then
           ()
       else (
           let $source := root($case)//catalog:source[@ID = $input/text()]
           return
               if (empty($source)) then
                   concat("no input found: ", $input/text()) 
               else
                   doc(concat("/db/XQTS/", $source/@FileName))
       )
};

xqts:initialize(),
(: xqts:test-single("Axes001"), :)
xqts:test-group("PathExpr"),
xqts:finish(),
doc("/db/XQTS/test-results/results.xml")