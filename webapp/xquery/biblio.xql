xquery version "1.0";
(: $Id: biblio.xq 6538 2007-09-12 09:09:35Z brihaye $ :)

declare namespace util="http://exist-db.org/xquery/util";
declare namespace dc="http://purl.org/dc/elements/1.1/";
declare namespace rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#";
declare namespace request="http://exist-db.org/xquery/request";
declare namespace session="http://exist-db.org/xquery/session";
declare namespace x="http://exist.sourceforge.net/dc-ext";

(: Map query field parameter to xpath selection :)
declare function local:query-field($field as xs:string) as xs:string
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
declare function local:filter-expr($term as xs:string, $field as xs:string,
$mode as xs:string) as xs:string
{
    let $f := local:query-field($field),
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
declare function local:order-expr($field as xs:string) as xs:string
{
	if ($field = "creator") then
		"(dc:creator|dc:editor)[1], $r/dc:title"
	else if ($field = "title") then
		"dc:title, (dc:creator|dc:editor)[1]"
	else
		"dc:date descending, dc:title"
};

(: Assemble the query string :)
declare function local:build-query($term1 as xs:string, $orderby as xs:string) 
as xs:string
{
    let $field1 := request:get-parameter("field1", "any"),
        $mode1 := request:get-parameter("mode1", "all"),
		$expr1 := local:filter-expr($term1, $field1, $mode1),
		$term2 := request:get-parameter("term2", ""),
		$expr :=
			if ($term2 = "") then $expr1
			else
				let $field2 := request:get-parameter("field2", "any"),
					$mode2 := request:get-parameter("mode2", "all")
				return
					concat($expr1, " and ", 
						local:filter-expr($term2, $field2, $mode2)),
		$t := session:set-attribute("query", $expr)
    return
        concat("for $r in xmldb:document()//rdf:Description[",
            $expr, "] order by $r/",
            local:order-expr($orderby), " return $r")
};

(:  display creators: if there's more than one creator, output the
    first one and add "et al."
:)
declare function local:get-creators($i as element()) as xs:string
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
declare function local:display-details($hits as node()+)
as element()
{
    let $count := count($hits),
        $start := xs:int(request:get-parameter("start", "1")),
        $max := request:get-parameter("howmany", "10") cast as xs:int,
        $hit := item-at($hits, $start)
    return
        <query-results hits="{$count}" start="{$start}" 
            next="{$start + 1}" max="{$max}">
            {$hit}
        </query-results>
};

(: Present an overview of query results :)
declare function local:display-summary($hits as node()+)
as element()
{
    let $count := count($hits),
        $max := request:get-parameter("howmany", "10") cast as xs:int,
        $start := request:get-parameter("start", "1") cast as xs:int,
        $end := if ($start + $max - 1 < $count) then $start + $max - 1 else $count
    return
        <query-results hits="{$count}" start="{$start}" 
            next="{$end + 1}" max="{$max}">
            {
                for $p in $start to $end
                let $current := item-at($hits, $p)
                let $creators := local:get-creators($current)
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
declare function local:display($hits as node()+)
as element()
{
    let $mode := request:get-parameter("display", "summary")
    return
        if ($mode = "summary") then
            local:display-summary($hits)
        else
            local:display-details($hits)
};

(: Re-order the search results :)
declare function local:reorder($query as xs:string, $orderby as xs:string)
as element()
{
	let $expr := concat("for $r in xmldb:document()//rdf:Description[",
					$query, "] order by $r/", local:order-expr($orderby),
					" return $r"),
		$hits := util:eval($expr),
		$s := session:set-attribute(
			"results", subsequence($hits, 1, 100)
		)
	return
		if (empty($hits)) then
			<p>Nothing found!</p>
		else
			local:display($hits)
};

declare function local:main() as element()+
{
    let $term1 := request:get-parameter("term1", ""),
        $previous := session:get-attribute("results"),
		$queryOld := session:get-attribute("query"),
		$orderby := request:get-parameter("order", "")
    return
        if(string-length($term1) = 0) then
			if ($orderby != "") then
				local:reorder($queryOld, $orderby)
            else if (exists($previous)) then
                local:display($previous)
            else
                <p>Please enter one or more search terms.
                <a href="biblio.xml">Back to query form!</a></p>
        else
            let $query := local:build-query($term1, "creator"),
                $hits := util:eval( $query ),
                $s := session:set-attribute("results", $hits)
            return
                if (empty($hits)) then
                    <p>Nothing found for your query!
                    <a href="biblio.xml">Back to query form!</a>
                    </p>
                else
                    local:display($hits)
};

<document xmlns:xi="http://www.w3.org/2001/XInclude">
	
	<xi:include href="header.xml"/>
	<xi:include href="sidebar.xml"/>

	<body>
        <section title="Library Search">
            { let $start := current-time() return
				(
					local:main(),
            		<p><small>Request served in 
					{seconds-from-duration(current-time()-$start)}
					seconds. <a href="source/biblio.xql">View Source</a>.
					</small></p>
				)
			}
        </section>
    </body>
</document>
