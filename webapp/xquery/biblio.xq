xquery version "1.0";

declare namespace util="http://exist-db.org/xquery/util";
declare namespace dc="http://purl.org/dc/elements/1.1/";
declare namespace fn="http://exist-db.org/local-functions";

(: Map query field parameter to xpath selection :)
declare function fn:query-field($field as xs:string) as xs:string
{
    if ($field = "au") then
        "dc:creator|(dc:editor|dc:contributor)"
    else if ($field = "ti") then
        "dc:title"
    else if ($field = "ab") then
        "dc:description"
    else if ($field = "su") then
        "dc:subject"
    else if ($field = "ye") then
        "dc:date"
    else
        "."
};

(: Create filter expression from query term, field and mode :)
declare function fn:filter-expr($term as xs:string, $field as xs:string,
$mode as xs:string) as xs:string
{
    let $f := fn:query-field($field),
        $t := concat("'", $term, "'")
    return
        if ($mode = "near") then
            concat("near(", $f, ", ", $t, ")")
        else if ($mode = "exact") then
            concat($f, " = ", $t)
        else
            concat($f, " &amp;= ", $t)
};

(: Map order parameter to xpath for order by clause :)
declare function fn:order-expr($field as xs:string) as xs:string
{
	if ($field = "creator") then
		"(dc:creator|dc:editor)[1], $r/dc:title"
	else if ($field = "title") then
		"dc:title, (dc:creator|dc:editor)[1]"
	else
		"dc:date descending, dc:title"
};

(: Assemble the query string :)
declare function fn:build-query($term1 as xs:string, $orderby as xs:string) 
as xs:string
{
    let $field1 := request:request-parameter("field1", "any"),
        $mode1 := request:request-parameter("mode1", "all"),
		$expr1 := fn:filter-expr($term1, $field1, $mode1),
		$term2 := request:request-parameter("term2", ""),
		$expr :=
			if ($term2 = "") then $expr1
			else
				let $field2 := request:request-parameter("field2", "any"),
					$mode2 := request:request-parameter("mode2", "all")
				return
					concat($expr1, " and ", 
						fn:filter-expr($term2, $field2, $mode2)),
		$t := request:set-session-attribute("query", $expr)
    return
        concat("for $r in document()//rdf:Description[",
            $expr, "] order by $r/",
            fn:order-expr($orderby), " return $r")
};

(:  display creators: if there's more than one creator, output the
    first one and add "et al."
:)
declare function fn:get-creators($i as element()) as xs:string
{
    let $creators := for $c in $i/(dc:creator|dc:editor) return $c,
        $count := count($creators)
    return
        if($count = 0) then
            ""
        else if($count = 1) then
            string(item-at($creators, 1))
        else
            concat(item-at($creators, 1), " et al.")
};

(: Display a single record from the query results :)
declare function fn:display-details($hits as node()+)
as element()
{
    let $count := count($hits),
        $start := xs:int(request:request-parameter("start", "1")),
        $hit := item-at($hits, $start)
    return
        <query-results hits="{$count}" start="{$start}" 
            next="{$start + 1 cast as xs:int}">
            {$hit}
        </query-results>
};

(: Present an overview of query results :)
declare function fn:display-summary($hits as node()+)
as element()
{
    let $count := count($hits),
        $start := xs:int(request:request-parameter("start", "1")),
        $end := if ($start + 9 < $count) then $start + 9 else $count
    return
        <query-results hits="{$count}" start="{$start}" 
            next="{$end + 1 cast as xs:int}">
            {
                for $p in $start to $end
                let $current := item-at($hits, $p)
                let $creators := fn:get-creators($current)
                return
                    <item>
                        <year>{$current/dc:date}</year>
                        {$current/dc:title}
                        <creator>{ $creators }</creator>
                    </item>
            }
        </query-results>
};

(: 	Call display-summary or display-details :)
declare function fn:display($hits as node()+)
as element()
{
    let $mode := request:request-parameter("display", "summary")
    return
        if ($mode = "summary") then
            fn:display-summary($hits)
        else
            fn:display-details($hits)
};

(: Re-order the search results :)
declare function fn:reorder($query as xs:string, $orderby as xs:string)
as element()
{
	let $expr := concat("for $r in document()//rdf:Description[",
					$query, "] order by $r/", fn:order-expr($orderby),
					" return $r"),
		$hits := util:eval($expr),
		$s := request:set-session-attribute("results", $hits)
	return
		if (empty($hits)) then
			<p>Nothing found!</p>
		else
			fn:display($hits)
};

declare function fn:do-query() as element()+
{
    let $term1 := request:request-parameter("term1", ""),
        $previous := request:get-session-attribute("results"),
		$queryOld := request:get-session-attribute("query"),
		$orderby := request:request-parameter("order", "")
    return
        if(string-length($term1) = 0) then
			if ($orderby != "") then
				fn:reorder($queryOld, $orderby)
            else if (exists($previous)) then
                fn:display($previous)
            else
                <p>Please enter one or more search terms.
                <a href="biblio.xml">Back to query form!</a></p>
        else
            let $query := fn:build-query($term1, "creator"),
                $hits := util:eval( $query ),
                $s := request:set-session-attribute("results", $hits)
            return
                if (empty($hits)) then
                    <p>Nothing found for your query!
                    <a href="biblio.xml">Back to query form!</a>
                    </p>
                else
                    fn:display($hits)
};

<document xmlns:xi="http://www.w3.org/2001/XInclude">
	
	<xi:include href="context://header.xml"/>
	<xi:include href="sidebar.xml"/>

	<body>
        <section title="Library Search">
            { let $start := current-time() return
				(
					fn:do-query(),
            		<p><small>Request served in 
					{get-seconds-from-dayTimeDuration(current-time()-$start)}
					seconds. <a href="source/biblio.xq">View Source</a>.
					</small></p>
				)
			}
        </section>
    </body>
</document>
