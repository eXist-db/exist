xquery version "1.0";

declare namespace m="http://www.loc.gov/mods/v3";
declare namespace b="http://exist-db.org/bibliography";

(: Load library modules :)
declare namespace request="http://exist-db.org/xquery/request";
declare namespace xsl="http://exist-db.org/xquery/transform";
declare namespace util="http://exist-db.org/xquery/util";
declare namespace xdb="http://exist-db.org/xquery/xmldb";

import module namespace c="http://exist-db.org/modules/mods-config"
at "config.xqm";

(: Removes a document :)
declare function b:remove($user, $pass) as element()
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
declare function b:queryField($field as xs:string) as xs:string
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

declare function b:operand($field, $terms) as xs:string
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
declare function b:createXPath($collection as xs:string, $term1 as xs:string?) 
as xs:string
{
    let $field1 := request:request-parameter("field1", "any"),
        $queryPart :=
            if($term1) then
                concat("collection('", $collection, "')//m:mods[", b:operand(b:queryField($field1), $term1))
            else
                concat("collection('", $collection, "')//m:mods"),
        $l := util:log("debug", ("Part: ", $term1)),
        $term2 := request:request-parameter("term2", ())
    return
        if($term2) then
            let $field2 := request:request-parameter("field2", "any"),
                $op := request:request-parameter("op", "and")
            return
                concat($queryPart, " ", $op, " ", b:operand(b:queryField($field2), $term2), "]")
        else if($term1) then
            concat($queryPart, "]")
        else
            $queryPart
};

declare function b:displayOverview($recs as item()*, $collection) as element()
{
    let $count := count($recs),
        $max := request:request-parameter("howmany", "5") cast as xs:int,
        $start := request:request-parameter("start", "1") cast as xs:int,
        $end := if ($start + $max - 1 < $count) then $start + $max - 1 else $count
    return
        <items start="{$start}" hits="{$count}" next="{$end + 1}" max="{$max}"
            collection="{$collection}" view="overview">
            {
                for $p in $start to $end
                let $m := item-at($recs, $p)
                return
                <item pos="{$p}" doc="{util:document-name($m)}">
                    { c:displayItem($m) }
                </item>
            }
        </items>
};

declare function b:displayDetails($recs as item()*, $collection) as element()
{
    let $count := count($recs),
        $max := xs:int(request:request-parameter("howmany", "5")),
        $start := xs:int(request:request-parameter("start", "1")),
        $hit := item-at($recs, $start)
    return
        <items hits="{$count}" start="{$start}" view="details"
            next="{$start + 1}" max="{$max}"
            collection="{collection}">
            <item pos="{$start}" doc="{util:document-name($hit)}">
                {$hit/*}
            </item>
        </items>
};

declare function b:display($recs as item()*, $collection) as element()
{
    let $display := request:request-parameter("view", "overview")
    return
        if($display eq "details") then
            b:displayDetails($recs, $collection)
        else
            b:displayOverview($recs, $collection)
};

declare function b:buildQuery($xpath as xs:string, $order as xs:string) as xs:string
{
    let $orderExpr := c:orderExpr($order)
    return
        concat(
            "import module namespace c='http://exist-db.org/modules/mods-config'
            at 'config.xqm';
            for $m in ", $xpath, " order by ", $orderExpr, " return $m")
};

declare function b:reorder($order as xs:string, $collection as xs:string) as element()+
{
    let $xpath := request:get-session-attribute("query"),
        $recs := util:eval(b:buildQuery($xpath, $order)),
        $x := request:set-session-attribute("cache", $recs)
    return
        b:display($recs, $collection)
};

declare function b:query($collection as xs:string) as element()+
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
	        let $xpath := b:createXPath($collection, $term1),
                $l := util:log("debug", ("Query: ", $xpath)),
		        $recs := util:eval(b:buildQuery($xpath, $orderby)),
                $x := request:set-session-attribute("query", $xpath),
                $r := request:set-session-attribute("cache", $recs)
	        return
		        b:display($recs, $collection)
        (: redisplay previous query results :)
        else if($orderby != "") then
            b:reorder($orderby, $collection)
        else (
            util:log("debug", ("Reading from cache: start = ", $start)),
            b:display($cached, $collection)
        )
};

declare function b:main($user as xs:string, $collection as xs:string)
as element()+
{
    let $action := request:request-parameter("action", "")
    return (
        util:log("debug", "checking action"),
        if($action eq "remove") then
            b:remove($user, $pass)
        else
            util:log("debug", "no action"),
        b:query($collection)
    )
};

declare function b:get-collection($user) as xs:string {
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
            request:get-session-attribute("modscol")
};

let $user := request:get-session-attribute("user"),
    $pass := request:get-session-attribute("password"),
    $url := request:encode-url(request:request-uri()),
    $collection := b:get-collection($user)
return
<html>
    <head>
        <title>MODS Example</title>
        <link href="{$c:css}" type="text/css" rel="stylesheet"/>
    </head>
    <body>
        <div id="top">
            <img src="logo.jpg" title="eXist"/>
            <table id="menubar">
                <tr>
                    <td id="header">MODS Example</td>
                    <td>
                        <a href="index.xml">Home</a>
                        <a href="index.xml#download">Download</a>
                        <a href="http://wiki.exist-db.org">Wiki</a>
                        <a href="examples.xml">Demo</a>
                    </td>
                </tr>
            </table>
        </div>

        <!-- include the sidebar -->
        { c:sidebar($url, $user, $collection) }
        
        <div id="content">
            <!-- call the main function to process the query -->
            { 
                let $displayForm := request:request-parameter("show-form", ())
                return
                    if($displayForm) then
                        c:query-form($url, $collection)
                    else
                        b:main($user, $collection) 
            }
        </div>
    </body>
</html>
