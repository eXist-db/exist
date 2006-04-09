xquery version "1.0";

declare namespace util="http://exist-db.org/xquery/util";
declare namespace m="http://www.loc.gov/mods/v3";
declare namespace request="http://exist-db.org/xquery/request";
declare namespace session="http://exist-db.org/xquery/session";

(: Map query field parameter to xpath selection :)
declare function local:query-field($field as xs:string) as xs:string
{
    if ($field = "au") then
        "m:name"
    else if ($field = "ti") then
        "m:titleInfo"
    else if ($field = "ab") then
        "m:abstract"
    else if ($field = "su") then
        "m:subject"
    else if ($field = "ye") then
        "m:originInfo/m:dateIssued"
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
		"$r/m:name[1]"
	else if ($field = "title") then
		"$r/m:titleInfo[1]/m:title"
	else
		"$r/m:originInfo/m:dateIssued[1] descending"
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
        concat("for $r in document()//m:mods[",
            $expr, "] order by ",
            local:order-expr($orderby), " return $r")
};

(: Display a single record from the query results :)
declare function local:display-details($hits as node()+)
as element()
{
    let $count := count($hits),
        $max := xs:int(request:get-parameter("max", "10")),
        $start := xs:int(request:get-parameter("start", "1")),
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
        $max := xs:int(request:get-parameter("max", "10")),
        $start := xs:int(request:get-parameter("start", "1")),
        $end := if ($start + $max - 1 < $count) then $start + $max - 1 else $count
    return
        <query-results hits="{$count}" start="{$start}" 
            next="{$end + 1}" max="{$max}">
            {
                for $p in $start to $end
                let $current := item-at($hits, $p)
                return
                    <item>
                        {
                            $current/m:originInfo/m:dateIssued,
                            $current/m:originInfo/m:copyrightDate,
                            $current/m:titleInfo[empty(@type)],
                            $current/m:name[1]
                        }
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
	let $expr := concat("for $r in document()//m:mods[",
					$query, "] order by ", local:order-expr($orderby),
					" return $r"),
		$hits := util:eval($expr),
		$s := session:set-attribute("results", $hits)
	return
		if (empty($hits)) then
			<p>Nothing found!</p>
		else
			local:display($hits)
};

declare function local:do-query() as element()+
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
            let $query := local:build-query($term1, $orderby),
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
	
	<header>
        <logo src="logo.jpg"/>
        <title>Open Source XML Database</title>
	    <author email="wolfgang@exist-db.org">Wolfgang M. Meier</author>
        <style href="mods.css"/>
    </header>

	<xi:include href="sidebar.xml"/>

	<body>
        <section title="MODS Search">
            { let $start := current-time() return
				(
					local:do-query(),
            		<p><small>Request served in 
					{seconds-from-duration(current-time()-$start)}
					seconds. <a href="source/biblio.xq">View Source</a>.
					</small></p>
				)
			}
        </section>
    </body>
</document>
