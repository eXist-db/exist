xquery version "1.0";

declare namespace m="http://www.loc.gov/mods/v3";
declare namespace b="http://exist-db.org/bibliography";

(: Load library modules :)
declare namespace request="http://exist-db.org/xquery/request";
declare namespace xsl="http://exist-db.org/xquery/transform";
declare namespace util="http://exist-db.org/xquery/util";
declare namespace xmldb="http://exist-db.org/xquery/xmldb";

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
					xmldb:collection(concat("xmldb:exist://", $path), $user,
                    $pass)
			return
				xmldb:remove-resource($collection, $doc)
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

(: Map order parameter to xpath for order by clause :)
declare function b:orderExpr($field as xs:string) as xs:string
{
	if ($field = "creator") then
		"$m/m:name[1]/m:namePart[@type='family'],$m/m:name[1]/m:namePart[@type='given']"
	else if ($field = "title") then
		"$m/m:titleInfo[1]/m:title"
	else
		"$m/m:originInfo/m:dateIssued[1] descending"
};

(: Create an XPath expression for the current field and search terms :)
declare function b:createXPath($user as xs:string, $terms as xs:string) 
as xs:string
{
    let $home := b:getUserHome($user),
        $field := request:request-parameter("field", "any")
    return
        if(empty($terms) or $terms eq "") then
            concat("collection('", $home, "')//m:mods")
        else
            concat("collection('", $home, "')//m:mods[", b:queryField($field),
                " &amp;= '", $terms, "']")
};

declare function b:displayOverview($recs as item()*, $user) as element()
{
    let $count := count($recs),
        $max := request:request-parameter("howmany", "5") cast as xs:int,
        $start := request:request-parameter("start", "1") cast as xs:int,
        $end := if ($start + $max - 1 < $count) then $start + $max - 1 else $count
    return
        <items start="{$start}" hits="{$count}" next="{$end + 1}" max="{$max}"
            chiba="{request:encode-url($c:chiba)}"
            collection="{b:getUserHome($user)}">
            {
                for $p in $start to $end
                let $m := item-at($recs, $p)
                return
                    <item doc="{util:document-name($m)}"
						chiba="{request:encode-url($c:chiba)}">
                        { c:displayItem($m) }
                    </item>
            }
        </items>
};

declare function b:displayDetails($recs as item()*, $user) as element()
{
    let $count := count($recs),
        $max := xs:int(request:request-parameter("howmany", "5")),
        $start := xs:int(request:request-parameter("start", "1")),
        $hit := item-at($recs, $start)
    return
        <items hits="{$count}" start="{$start}" 
            next="{$start + 1}" max="{$max}"
            collection="{b:getUserHome($user)}">
            {$hit}
        </items>
};

declare function b:display($recs as item()*, $user) as element()
{
    let $display := request:request-parameter("display", "overview")
    return
        if($display eq "details") then
            b:displayDetails($recs, $user)
        else
            b:displayOverview($recs, $user)
};

declare function b:buildQuery($xpath as xs:string, $order as xs:string) as xs:string
{
    let $orderExpr := b:orderExpr($order)
    return
        concat("for $m in ", $xpath, " order by ", $orderExpr, " return $m")
};

declare function b:reorder($order as xs:string, $user as xs:string) as element()+
{
    let $xpath := request:get-session-attribute("query"),
        $recs := util:eval(b:buildQuery($xpath, $order)),
        $x := request:set-session-attribute("cache", $recs)
    return
        b:display($recs, $user)
};

declare function b:query($user as xs:string) as element()+
{
    let $start := request:request-parameter("start", ()),
        $cached := request:get-session-attribute("cache"),
        $orderby := request:request-parameter("order", ""),
        $terms := request:request-parameter("query", ())
    return
        (: if parameter "start" is not set, execute a new query :)
        if(empty($start) or empty($cached)) then
	        let $xpath := b:createXPath($user, $terms), 
		        $recs := util:eval(b:buildQuery($xpath, $orderby)),
                $x := request:set-session-attribute("query", $xpath),
                $r := request:set-session-attribute("cache", $recs)
	        return
		        b:display($recs, $user)
        (: redisplay previous query results :)
        else if($orderby != "") then
            b:reorder($orderby, $user)
        else (
            util:log("debug", ("Reading from cache: start = ", $start)),
            b:display($cached, $user)
        )
};

declare function b:getUserHome($user as xs:string?) as xs:string?
{
    let $collection := request:get-session-attribute("collection")
    return
        if($collection) then
            $collection
        else
            let $home := string(doc("/db/system/users.xml")//user[@name =
                         	$user]/@home)
            return (
                request:set-session-attribute("collection", $home),
                $home
            )
};

declare function b:main()
as element()+
{
    let $action := request:request-parameter("action", ""),
        $user := request:get-session-attribute("user"),
        $pass := request:get-session-attribute("password")
    return
        (
        util:log("debug", "checking action"),
        if($action eq "remove") then
            b:remove($user, $pass)
        else
            util:log("debug", "no action"),
        b:query($user))
};

<html>
    <head>
        <title>Bibliography Overview</title>
        <link href="{$c:css}" type="text/css" rel="stylesheet"/>
    </head>
    <body>
        <div id="top">
            <img src="logo.jpg" title="eXist"/>
            <table id="menubar">
                <tr>
                    <td id="header">Open Source XML Database</td>
                    <td>
                        <a href="http://wiki.exist-db.org">News/Wiki</a>
                        <a href="index.xml#download">Download</a>
                    </td>
                </tr>
            </table>
        </div>

        { c:sidebar(request:encode-url(request:request-uri())) }        

		<!-- Process action if specified -->
        { b:main() }
    </body>
</html>
