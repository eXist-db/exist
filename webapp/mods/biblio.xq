xquery version "1.0";

(::pragma exist:output-size-limit -1::)

declare namespace m="http://www.loc.gov/mods/v3";
declare namespace bib="http://exist-db.org/bibliography";

(: Load library modules :)
declare namespace request="http://exist-db.org/xquery/request";
declare namespace xsl="http://exist-db.org/xquery/transform";
declare namespace util="http://exist-db.org/xquery/util";
declare namespace xdb="http://exist-db.org/xquery/xmldb";

import module namespace conf="http://exist-db.org/modules/mods-config" at "config.xqm";

import module namespace sort="http://exist-db.org/biblio/sort" at "sort.xq";

import module namespace display="http://exist-db.org/biblio/display" at "display.xq";

declare variable $bib:sort-import {
    'import module namespace sort="http://exist-db.org/biblio/sort"
    at "sort.xq";'
};

(: Removes a document :)
declare function bib:remove($user, $pass) as element()
{
	let $doc := request:request-parameter("doc", ()),
		$path := request:request-parameter("collection", ())
	return
		if(empty($doc) or empty($path)) then
			<p>Required parameter "doc" or "collection" is missing!</p>
		else (
			<p>Removing document {$doc} from collection {$path}.</p>,
			let $collection :=
					xdb:collection(concat("xmldb:exist://", $path), $user,
                    $pass)
			return
				xdb:remove($collection, $doc)
		)
};

(: Get the XPath expression for the specified field :)
declare function bib:queryField($field as xs:string) as xs:string
{
	if($field eq "au") then
		"m:name"
	else if($field eq "ti") then
		"m:titleInfo"
	else if($field eq "ab") then
		"m:abstract"
	else if($field eq "su") then
		"m:subject"
	else if($field eq "ye") then
		"m:originInfo/m:dateIssued"
	else
		"."
};

declare function bib:operand($field, $terms) as xs:string
{
    let $mode := request:request-parameter("mode", "all")
    return
        if($mode eq "any") then
            concat($field, " |= '", $terms, "'")
        else if($mode eq "near") then
            concat("near(", $field, ", '", $terms, "')")
        else
            concat($field, " &amp;= '", $terms, "'")
};

(: Create an XPath expression for the current field and search terms :)
declare function bib:createXPath($collection as xs:string, $term1 as xs:string?) 
as xs:string
{
    let $field1 := request:request-parameter("field1", "any"),
        $queryPart :=
            if($term1) then
                concat("collection('", $collection, "')//m:mods[", bib:operand(bib:queryField($field1), $term1))
            else
                concat("collection('", $collection, "')//m:mods"),
        $l := util:log("debug", ("Part: ", $term1)),
        $term2 := request:request-parameter("term2", ())
    return
        if($term2) then
            let $field2 := request:request-parameter("field2", "any"),
                $op := request:request-parameter("op", "and")
            return
                concat($queryPart, " ", $op, " ", bib:operand(bib:queryField($field2), $term2), "]")
        else if($term1) then
            concat($queryPart, "]")
        else
            $queryPart
};

declare function bib:displayOverview($recs as item()*, $collection) as element()+
{
    let $count := count($recs),
        $max := request:request-parameter("howmany", "10") cast as xs:int,
        $start := request:request-parameter("start", "1") cast as xs:int,
        $end := if ($start + $max - 1 < $count) then $start + $max - 1 else $count,
        $expandAll := exists(request:request-parameter("expand", ())),
        $preload := if ($expandAll) then true() else $conf:preload
    return
        <form name="mainForm" action="biblio.xq" method="GET">
            {display:navigation($count, $start, $end + 1, $max, $preload)}
            <table id="results">
                <tr id="headings">
                    <th/>
                    <th><a href="?order=creator">Author</a></th>
                    <th><a href="?order=Date">Year</a></th>
                    <th><a href="?order=title">Title</a></th>
                </tr>
                {
                    for $p in $start to $end
                    let $m := $recs[$p]
                    return (
                        display:record-short($p, $m, $expandAll),
                        if ($preload) then
                            display:record-full-preload($p, $m, $expandAll)
                        else
                            ()
                    )
                }
            </table>
        </form>
};

declare function bib:displayDetails($recs as item()*, $collection) as element()
{
    let $count := count($recs),
        $max := xs:int(request:request-parameter("howmany", "10")),
        $start := xs:int(request:request-parameter("start", "1")),
        $hit := item-at($recs, $start)
    return
        <items hits="{$count}" start="{$start}" view="details"
            next="{$start + 1}" max="{$max}"
            collection="{$collection}">
            <item pos="{$start}" doc="{util:document-name($hit)}">
                {$hit/*}
            </item>
        </items>
};

declare function bib:display($recs as item()*, $collection) as element()+
{
    let $display := request:request-parameter("view", "overview")
    return
        if($display eq "details") then
            bib:displayDetails($recs, $collection)
        else
            bib:displayOverview($recs, $collection)
};

declare function bib:buildQuery($xpath as xs:string, $order as xs:string) as xs:string
{
    let $orderExpr := sort:orderExpr($order)
    return
        concat($bib:sort-import,
            "for $m in ", $xpath, " order by ", $orderExpr, " return $m")
};

declare function bib:reorder($order as xs:string, $collection as xs:string) as element()+
{
    let $xpath := request:get-session-attribute("query"),
        $recs := util:eval(bib:buildQuery($xpath, $order)),
        $x := request:set-session-attribute("cache", $recs)
    return
        bib:display($recs, $collection)
};

declare function bib:query($collection as xs:string) as element()+
{
    let $simpleQuery := boolean(request:request-parameter("query", ())),
        $start := request:request-parameter("start", ()),
        $cached := request:get-session-attribute("cache"),
        $orderby := request:request-parameter("order", ""),
        $term1 :=
            if($simpleQuery) then
                request:request-parameter("query", ())
            else
                request:request-parameter("term1", ())
    return
        (: if parameter "start" is not set, execute a new query :)
        if(empty($start) or empty($cached)) then
	        let $xpath := bib:createXPath($collection, $term1),
                $l := util:log("debug", ("Query: ", $xpath)),
		        $recs := util:eval(bib:buildQuery($xpath, $orderby)),
                $x := request:set-session-attribute("query", $xpath),
                $r := request:set-session-attribute("cache", $recs)
	        return
		        bib:display($recs, $collection)
        (: redisplay previous query results :)
        else if($orderby != "") then
            bib:reorder($orderby, $collection)
        else (
            util:log("debug", ("Reading from cache: start = ", $start)),
            bib:display($cached, $collection)
        )
};

declare function bib:main($user as xs:string, $collection as xs:string)
as element()+
{
    let $action := request:request-parameter("action", "")
    return (
        util:log("debug", "checking action"),
        if($action eq "remove") then
            bib:remove($user, $pass)
        else
            util:log("debug", "no action"),
        bib:query($collection)
    )
};

declare function bib:get-collection($user as xs:string?) as xs:string {
    let $colParam := request:request-parameter("collection", ())
    return
        if($colParam) then
            let $collection :=
                    if($colParam eq "_home_") then
                        xdb:get-user-home($user)
                    else
                        $colParam,
                $s := request:set-session-attribute("modscol", $collection)
            return
                $collection
        else
            if(request:get-session-attribute("modscol")) then
                request:get-session-attribute("modscol")
            else
                "/db/mods" (: fallback :)
};

let $user := request:get-session-attribute("user"),
    $pass := request:get-session-attribute("password"),
    $url := request:encode-url(request:request-uri()),
    $collection := bib:get-collection($user)
return
<html>
    <head>
        <title>MODS Example</title>
        <link href="{$conf:css}" type="text/css" rel="stylesheet"/>
        <script language="Javascript" type="text/javascript" src="load.js"/>
        <script type="text/javascript" src="livesearch.js"/>
        <script type="text/javascript">
            var collection = &quot;{$collection}&quot;;
        </script>
    </head>
    <body onload="liveSearchInit();">
        <div id="page-head">
            <img src="logo.jpg" title="eXist"/>
            <div id="navbar">
                <h1>MODS Example</h1>
                <ul id="menu">
                    <li><a href="../index.xml">Home</a></li>
                    <li><a href="../index.xml#download">Download</a></li>
                    <li><a href="http://wiki.exist-db.org">Wiki</a></li>
                    <li><a href="../examples.xml">Demo</a></li>
                </ul>
            </div>
        </div>

        <!-- include the sidebar -->
        { conf:sidebar($url, $user, $collection) }
        
        <div id="content2col">
            <!-- call the main function to process the query -->
            { 
                let $displayForm := request:request-parameter("show-form", ())
                return
                    if($displayForm) then
                        conf:query-form($url, $collection)
                    else
                        bib:main($user, $collection) 
            }
        </div>
    </body>
</html>
