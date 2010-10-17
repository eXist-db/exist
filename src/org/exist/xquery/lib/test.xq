xquery version "1.0";

module namespace t="http://exist-db.org/xquery/testing";

import module namespace xdb="http://exist-db.org/xquery/xmldb";
import module namespace xdiff="http://exist-db.org/xquery/xmldiff"
at "java:org.exist.xquery.modules.xmldiff.XmlDiffModule";

declare function t:setup-action($action) {
    typeswitch ($action)
        case element(create-collection) return
            xdb:create-collection($action/@parent, $action/@name)
        case element(store) return
			t:store($action)
		case element(store-files) return
            t:store-files($action)
        case element(remove-collection) return
            xdb:remove($action/@collection)
        case element(remove-document) return
            xdb:remove($action/@collection, $action/@name)
        default return
            ()
};

declare function t:store($action as element(store)) {
    let $type := if ($action/@type) then $action/@type/string() else "application/xml"
    let $data :=
		if ($action/*) then
			$action/*[1]
		else
			$action/string()
	return
        xdb:store($action/@collection, $action/@name, $data, $type)
};

declare function t:store-files($action as element(store-files)) {
    let $type := if ($action/@type) then $action/@type/string() else "application/xml"
    return
        xdb:store-files-from-pattern($action/@collection, $action/@dir, $action/@pattern, $type)
};

declare function t:setup($setup as element(setup)) {
    for $action in $setup/*
    return
        t:setup-action($action)
};

declare function t:tearDown($tearDown as element(tearDown)?) {
    for $action in $tearDown/*
    return
        t:setup-action($action)
};

declare function t:declare-variable($var as element(variable)) as item()? {
    let $children := $var/*
    return
        if (empty($children)) then
            string-join($var/node(), '')
        else
            util:serialize($children, ())
};

declare function t:init-prolog($test as element(test)) {
	let $imports := $test/../imports
	let $vars :=
	    string-join(
        	for $var in $test/../variable
        	return
        	    concat("declare variable $", $var/@name, " := ", t:declare-variable($var), ";"),
            ""
        )
	return
		string-join(($imports, $vars, $test/../functions), '')
};

declare function t:test($result as item()*) {
    if ($result instance of xs:boolean) then
        $result
    else
        exists($result)
};

declare function t:compare-xml($output as node()*, $expected as node()*) {
    if (empty($output)) then
        true()
    else
        let $firstEqual :=
            if ($output[1] instance of text()) then
                deep-equal($output[1], $expected[1])
            else
                t:compare-xml(subsequence($output, 2), subsequence($expected, 2))
        return
            if (not($firstEqual)) then
                false()
            else if (count($output) gt 1) then
                t:compare-xml(subsequence($output, 2), subsequence($expected, 2))
            else
                true()
};

declare function t:run-test($test as element(test), $count as xs:integer) {
	let $context := t:init-prolog($test)
	let $null := 
	   if ($test/@trace eq 'yes') then 
	       (system:clear-trace(), system:enable-tracing(true(), false()))
       else ()
    let $queryOutput :=
		util:catch("*",
			util:eval(concat($context, $test/code/string())),
			<error>Compilation error: {$util:exception-message}</error>
		)
	let $output := if ($test/@trace eq 'yes') then system:trace() else $queryOutput
    let $expected :=
        if ($test/@output eq 'text') then
            data($test/expected)
        else $test/expected/node()
    let $OK :=
        if ($test/xpath) then
            t:test(t:xpath($output, $test/xpath))
        else if ($test/@output eq 'text') then
            normalize-space(string-join(for $x in $output return string($x),' ')) eq normalize-space($expected)
        else
            t:compare-xml($output, $expected)
    let $expanded :=
		if ($output instance of node()) then
        	util:expand($output)
		else
			$output
    return
        <test n="{$count}" pass="{$OK}">
        {
            if (not($OK)) then
                ($test/task, $test/expected, <result>{$expanded}</result>)
            else ()
        }
        </test>
};

declare function t:xpath($output as item()*, $xpath as element()) {
    for $prefix in in-scope-prefixes($xpath)
    let $nsuri := namespace-uri-for-prefix($prefix, $xpath)
    return
        if ($prefix != 'xml') then
            util:declare-namespace($prefix, $nsuri)
        else (),
    let $expr := $xpath/string()
    return
        if (matches($expr, "^\s*/")) then
            util:eval(concat("$output", $expr))
        else
            util:eval($expr)
};

declare function t:run-testSet($set as element(TestSet)) {
    let $copy := util:expand($set)
    let $null := t:setup($copy/setup)
    let $result := util:expand(
           <TestSet>
           {$copy/testName}
           {$copy/description}
           {
               for $test at $p in $copy/test
               return
                   t:run-test($test, $p)
           }
           </TestSet>
        )
    let $null := t:tearDown($copy/tearDown)
    return $result
};

declare function t:format-testResult($result) {
    let $formatted := <html>
        <body>
          <h1>{$result/testName}</h1>
          <p>{$result/description/p}</p>
          <table>
          {for $test in $result//test
           return
                <tr>
                    <td>{$test/@n/.}</td>
                    <td>{$test/@pass/.}</td>
                </tr>


          }
          </table>
        </body>
     </html>
     return
        $formatted
};
