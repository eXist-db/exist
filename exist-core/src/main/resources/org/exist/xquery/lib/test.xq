xquery version "3.1";

module namespace t = "http://exist-db.org/xquery/testing";

import module namespace system = "http://exist-db.org/xquery/system";
import module namespace util = "http://exist-db.org/xquery/util";
import module namespace xmldb = "http://exist-db.org/xquery/xmldb";
import module namespace xdiff = "http://exist-db.org/xquery/xmldiff"
        at "java:org.exist.xquery.modules.xmldiff.XmlDiffModule";

declare namespace err = "http://www.w3.org/2005/xqt-errors";
declare namespace exerr = "http://www.exist-db.org/xqt-errors/";

declare function t:setup-action($action) {
    typeswitch ($action)
        case element(create-collection) return
            xmldb:create-collection($action/@parent, $action/@name)
        case element(store) return
			t:store($action)
		case element(store-files) return
            t:store-files($action)
        case element(remove-collection) return
            if ($action/@ignore-missing eq "true" and not(xmldb:collection-available($action/@collection)))
            then ()
            else
                xmldb:remove($action/@collection)
        case element(remove-document) return
            xmldb:remove($action/@collection, $action/@name)
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
        xmldb:store($action/@collection, $action/@name, $data, $type)
};

declare function t:store-files($action as element(store-files)) {
    let $type := if ($action/@type) then $action/@type/string() else "application/xml"
    return
        if ($action/@classpath)
        then
            let $classpath-entries := util:system-property("java.class.path")
            let $path-separator := util:system-property("path.separator")
            let $cp :=
                if (util:system-property("file.separator") eq '/')
                then
                    $action/@classpath
                else
                    replace($action/@classpath, '/', '\\')
            let $matches := fn:tokenize($classpath-entries, $path-separator)[fn:ends-with(., $cp)]
            return
                if (not(empty($matches)))
                then
                    xmldb:store-files-from-pattern($action/@collection, $matches[1], $action/@pattern, $type)
                else
                    util:log("ERROR", ("Could not match classpath with '" || $action/@classpath || "' in test.xq"))
        else
            xmldb:store-files-from-pattern($action/@collection, $action/@dir, $action/@pattern, $type)
};


declare function t:setup($setup as element(setup)?) {
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
            fn:serialize($children)
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

declare function t:run-test($test as element(test), $count as xs:integer) {
    t:run-test($test, $count, (), (), (), (), (), ())
};

declare function t:run-test($test as element(test), $count as xs:integer,
        $test-ignored-function as (function(xs:string) as empty-sequence())?,
        $test-started-function as (function(xs:string) as empty-sequence())?,
        $test-failure-function as (function(xs:string, map(xs:string, item()?), map(xs:string, item()?)) as empty-sequence())?,
        $test-assumption-failed-function as (function(xs:string, map(xs:string, item()?)?) as empty-sequence())?,
        $test-error-function as (function(xs:string, map(xs:string, item()?)?) as empty-sequence())?,
        $test-finished-function as (function(xs:string) as empty-sequence())?) {
	let $context := t:init-prolog($test)
	let $null := 
	   if ($test/@trace eq 'yes') then 
	       (system:clear-trace(), system:enable-tracing(true(), false()))
       else ()
    let $highlight-option := concat("highlight-matches=",
          if ($test/expected//@*[matches(., '^(\|{3}).*\1$')] and $test/expected//exist:match) then "both"
          else if ($test/expected//@*[matches(., '^(\|{3}).*\1$')]) then "attributes"
          else if ($test/expected//exist:match) then "elements"
          else "none"        
          )
    let $serialize-options := 
      let $decls := ($test/../*[name() ne 'test']|$test/code)[matches(., 'declare[\- ]option(\((&#34;|&#39;)|\s+)exist:serialize(\2,)?\s+(&#34;|&#39;).*?\4[;)]')]
      let $ops1 := $decls/replace(., "declare[\- ]option(\((&#34;|&#39;)|\s+)exist:serialize(\2,)?\s+(&#34;|&#39;)(.*?)\4[;)]", "_|$5_")
      let $ops2 :=
        for $a in $ops1
        for $b in tokenize($a, '_')[starts-with(., '|')]
        return tokenize(substring-after($b, '|'), '\s+')
      return if (count($ops2[matches(., 'highlight-matches')]))
        then string-join($ops2, ' ')
        else string-join(($ops2, $highlight-option), ' ')
    let $_notify-start := if(not(empty($test-started-function))) then $test-started-function($test/task) else ()
    let $queryOutput :=
        try {
            map {
                "result": util:eval(concat($context, $test/code/string()), false())
            }
        } catch * {
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
        }
	let $output := if ($test/@trace eq 'yes') then map { "result": system:trace() } else $queryOutput
    let $expanded :=
        if (not(empty($output("error")))) then
            $output
        else if ($test/@serialize) then
            try {

                let $ser-params := map:merge(
                    let $options := fn:tokenize($test/@serialize/string(), " ")
                    for $option in $options
                    let $kv := fn:tokenize($option, "=")
                    return
                        map:entry($kv[1], $kv[2])
                )
                return
                    map {
                        "result": normalize-space(string-join($output("result") ! fn:serialize(., $ser-params)))
                    }
            } catch * {
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
            }

		else if ($output("result") instance of node()) then
        	map {
        	    "result": util:expand($output("result"), $serialize-options)
        	}
		else
			$output
    let $expected :=
        if ($test/@output eq 'text') then
            data($test/expected)
        else $test/expected/node()
    let $OK :=
        if ($test/error) then
            not(empty($expanded("error"))) and (contains($expanded("error")("description"), $test/error) or local-name-from-QName($expanded("error")("code")) eq string($test/error))
        else if ($test/xpath) then
            t:test(t:xpath($output("result"), $test/xpath))
        else if ($test/@output eq 'text') then
            let $asString :=
                if ($test/@serialize) then
                    $expanded("result")
                else
                    normalize-space(string-join(for $x in $output("result") return string($x),' '))
            return
                $asString eq normalize-space($expected)
        else
            let $xn := t:normalize($expanded("result"))
            let $en := t:normalize($expected)
            return
        	   deep-equal($xn, $en)
    return
        (
            <test n="{$count}" pass="{$OK}">
            { $test/@id }
            {
                if (not($OK)) then
                    let $_notify :=
                        if(not(empty($expanded("error")))) then
                            if(not(empty($test-error-function))) then $test-error-function($test/task, $expanded("error")) else ()
                        else
                            if(not(empty($test-failure-function))) then $test-failure-function($test/task, map { "value": $test/expected/node(), "xpath": $test/xpath/node(), "error": $test/error/node() }, $expanded) else ()
                    return
                        ($test/task, $test/expected, $test/xpath, <result>{$expanded("result"), if(not(empty($expanded("error")))) then $expanded("error")("description") else ()}</result>)
                else
                    ()
            }
            </test>
            ,
            let $_notify-finished := if(not(empty($test-finished-function))) then $test-finished-function($test/task) else ()
            return ()
        )
};

declare function t:normalize($nodes as node()*) {
	for $node in $nodes return t:normalize-node($node)
};

declare function t:normalize-node($node as node()) {
	typeswitch ($node)
		case element() return
			element { node-name($node) } {
				$node/@*, for $child in $node/node() return t:normalize-node($child)
			}
		case text() return
			let $norm := normalize-space($node)
			return
				if (string-length($norm) eq 0) then () else $node
		default return
			$node
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

declare function t:run-testSet($set as element(TestSet), $id as xs:string?) {
    t:run-testSet($set, $id, (), (), (), (), (), ())
};

declare function t:run-testSet($set as element(TestSet), $id as xs:string?,
        $test-ignored-function as (function(xs:string) as empty-sequence())?,
        $test-started-function as (function(xs:string) as empty-sequence())?,
        $test-failure-function as (function(xs:string, map(xs:string, item()?), map(xs:string, item()?)) as empty-sequence())?,
        $test-assumption-failed-function as (function(xs:string, map(xs:string, item()?)?) as empty-sequence())?,
        $test-error-function as (function(xs:string, map(xs:string, item()?)?) as empty-sequence())?,
        $test-finished-function as (function(xs:string) as empty-sequence())?) {
    let $copy := util:expand($set)
    let $null := t:setup($copy/setup)
    let $tests :=
        if ($id) then
            $copy/test[@id = $id]
        else
            for $test in $copy/test
            return
                if($test[empty(@ignore) or @ignore = "no"])then
                    $test
                else
                    let $_notify-ignored := if(not(empty($test-ignored-function))) then $test-ignored-function($test/task) else ()
                    return ()
    let $result := util:expand(
           <TestSet>
           {$copy/testName}
           {$copy/description}
           {
               for $test at $p in $tests
               return
                   t:run-test($test, $p,
                        $test-ignored-function, $test-started-function, $test-failure-function,
                        $test-assumption-failed-function, $test-error-function, $test-finished-function)
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
