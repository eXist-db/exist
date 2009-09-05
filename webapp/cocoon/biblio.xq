xquery version "1.0";
(: $Id: biblio.xq 7220 2008-01-14 10:50:38Z wolfgang_m $ :)

declare namespace bib="http://exist-db.org/bibliography";

(: Load library modules :)
declare namespace request="http://exist-db.org/xquery/request";
declare namespace session="http://exist-db.org/xquery/session";
declare namespace xsl="http://exist-db.org/xquery/transform";
declare namespace util="http://exist-db.org/xquery/util";
declare namespace xdb="http://exist-db.org/xquery/xmldb";

import module namespace conf="http://exist-db.org/modules/mods-config" at "config.xqm";

import module namespace sort="http://exist-db.org/biblio/sort" at "sort.xq";

import module namespace display="http://exist-db.org/biblio/display" at "display.xq";

(: declare option exist:output-size-limit "-1"; :)

declare variable $bib:sort-import {
    'import module namespace sort="http://exist-db.org/biblio/sort"
    at "sort.xq";'
};

(: Removes a document :)
declare function bib:remove() as element()?
{
    let $resources := request:get-parameter("r", ())
    return
        if(empty($resources)) then
            <script>showError(&quot;Please select a resource to remove!&quot;)</script>
        else
            for $r in $resources
            let $log := util:log("DEBUG", ("$r: ", util:get-sequence-type($r), ": ", $r))
            let $cached := session:get-attribute("cache"),
                $rec := $cached[xs:int($r)]
            return (
                xdb:remove(util:collection-name($rec), util:document-name($rec)),
                session:set-attribute("cache", remove($cached, xs:int($r)))
            )
};

declare function bib:remove-folder() as element()? {
    let $folder := request:get-parameter("folder", ())
    return
        if(not($folder)) then
            <script>showError(&quot;Please select a folder to remove!&quot;)</script>
        else
            xdb:remove($folder)
};

declare function bib:create-folder($homeColl as xs:string) as element()? {
    let $name := request:get-parameter("name", ()),
         $parentParam := request:get-parameter("folder", ()),
         $parent :=
             if ($parentParam) then
                 $parentParam
             else
                 $homeColl
    return
        let $newCol := xdb:create-collection($parent, $name)
        return ()
};

declare function bib:folders($root as xs:string, $current as xs:string) as element() {
    <ul>
        {bib:scan-collection($root, $root, $current)}
    </ul>
};

declare function bib:scan-collection($home as xs:string, $collection as
xs:string, $current as xs:string) as element()+ {
    let $displayName := 
        if ($home eq $collection) then
            "All folders"
        else
            substring-after(substring-after($collection, $home), '/')
    return (
        <li>
            <input type="radio" name="folder" value="{$collection}">
            {
                if ($collection eq $current) then
                    attribute checked { "checked" }
                else
                    ()
            }
            </input>
            <a href="?collection={$collection}">{$displayName}</a>
        </li>,
        for $child in xdb:get-child-collections($collection)
        order by $child return
            bib:scan-collection($home, concat($collection, '/', $child),
            $current)
    )
};

declare function bib:operand($field, $terms) as xs:string
{
    let $mode := request:get-parameter("mode", "all")
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
    let $field1 := request:get-parameter("field1", "any"),
        $queryPart :=
            if($term1) then
                concat(conf:get-query-root($collection), "[", bib:operand(conf:queryField($field1), $term1))
            else
                conf:get-query-root($collection),
        $l := util:log("debug", ("Part: ", $term1)),
        $term2 := request:get-parameter("term2", ())
    return
        if($term2) then
            let $field2 := request:get-parameter("field2", "any"),
                $op := request:get-parameter("op", "and")
            return
                concat($queryPart, " ", $op, " ", bib:operand(conf:queryField($field2), $term2), "]")
        else if($term1) then
            concat($queryPart, "]")
        else
            $queryPart
};

declare function bib:displayOverview($recs as item()*, $collection) as element()+
{
    let $count := count($recs),
        $max := request:get-parameter("howmany", "10") cast as xs:int,
        $start := request:get-parameter("start", "1") cast as xs:int,
        $end := if ($start + $max - 1 < $count) then $start + $max - 1 else $count,
        $expandAll := exists(request:get-parameter("expand", ())),
        $preload := if ($expandAll) then true() else $conf:preload
    return
        <form name="mainForm" action="biblio.xq" method="POST">
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
        $max := xs:int(request:get-parameter("howmany", "10")),
        $start := xs:int(request:get-parameter("start", "1")),
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
    let $display := request:get-parameter("view", "overview")
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
        concat(conf:get-namespace-decls(), $bib:sort-import,
            "for $m in ", $xpath, " order by ", $orderExpr, " return $m")
};

declare function bib:reorder($order as xs:string, $collection as xs:string) as element()+
{
    let $xpath := session:get-attribute("query"),
        $l := util:log("debug", ("Cached: ", $xpath)),
        $recs := util:eval(bib:buildQuery($xpath, $order)),
        $x := session:set-attribute("cache", $recs)
    return
        bib:display($recs, $collection)
};

declare function bib:query($collection as xs:string) as element()+
{
    let $simpleQuery := request:get-parameter("query", ()),
        $start := request:get-parameter("start", ()),
        $cached := session:get-attribute("cache"),
        $orderby := request:get-parameter("order", ""),
        $term1 :=
            if($simpleQuery) then
                $simpleQuery
            else
                request:get-parameter("term1", ())
    return
        (: if parameter "start" is not set, execute a new query :)
        if($term1 or $simpleQuery eq "" or empty($cached)) then
            let $xpath := bib:createXPath($collection, $term1),
                $l := util:log("debug", ("Query: ", $xpath)),
                $recs := util:eval(bib:buildQuery($xpath, $orderby)),
                $x := session:set-attribute("query", $xpath),
                $r := session:set-attribute("cache", $recs)
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

declare function bib:process-action($collection as xs:string,
$homeCol as xs:string) as element()* {
    let $action := request:get-parameter("action", "")
    return (
        util:log("debug", "checking action"),
        if($action eq "Remove") then
            bib:remove()
        else if ($action eq "Create") then
            bib:create-folder($homeCol)
        else if ($action eq "Remove Folder") then
            bib:remove-folder()
        else
            util:log("debug", "no action")
    )
};

declare function bib:main($collection as xs:string)
as element()+
{
    bib:query($collection)
};

declare function bib:get-collection($user as xs:string?) as xs:string {
    let $colParam := request:get-parameter("collection", ())
    return
        if($colParam) then
            let $collection :=
                    if($colParam eq "_home_") then
                        xdb:get-user-home($user)
                    else
                        $colParam,
                $s := session:set-attribute("modscol", $collection)
            return
                $collection
        else
            if(session:get-attribute("modscol")) then
                session:get-attribute("modscol")
            else
                "/db/mods" (: fallback :)
};

let $user := session:get-attribute("user"),
    $pass := session:get-attribute("password"),
    $url := session:encode-url(request:get-uri()),
    $collection := bib:get-collection($user),
    $homeSess := session:get-attribute("root-collection"),
    $homeCol :=
        if ($homeSess) then $homeSess
        else (
            session:set-attribute("root-collection", $collection),
            $collection
        )
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
                    <li><a href="{$url}?action=logout">Logout</a></li>
                </ul>
            </div>
        </div>
        {bib:process-action($collection, $homeCol)}
        <!-- include the sidebar -->
        <div id="sidebar">
            <div class="block">
                <h3>Search</h3>
                <form name="searchform" action="{$url}" method="GET">
                    <div id="livesearch">
                        <input class="textfield" name="query" type="text" autocomplete="off" 
                            onkeypress="liveSearchStart()" />
                    </div>
                    <div id="LSResult" style="display: none;"><div id="LSShadow"></div></div>
                    <input type="submit" class="button"/>
                </form>
                <ul>
                    <li><a href="{$url}?show-form=true">Advanced Query</a></li>
                </ul>
            </div>
        
            <div class="block">
                <h3>Folders</h3>
                <form action="{$url}" method="GET" id="folders">
                    {bib:folders($homeCol, $collection)}
                    <fieldset>
                        <label for="name">Create a new folder</label>
                        <input class="textfield" type="text" name="name"/>
                        <input class="button" type="submit" name="action" value="Create"/>
                     </fieldset>
                     <fieldset>
                         <input class="button" type="submit" name="action"
                         value="Remove Folder"/>
                     </fieldset>
                </form>
            </div>
            
            <div class="userinfo">
                Logged in as: {$user}<br/>
                Collection: {$collection}
            </div>
        </div>
        
        <div id="content2col">
            <!-- call the main function to process the query -->
            { 
                let $displayForm := request:get-parameter("show-form", ())
                return
                    if($displayForm) then
                        conf:query-form($url, $collection)
                    else
                        bib:main($collection) 
            }
        </div>
    </body>
</html>
