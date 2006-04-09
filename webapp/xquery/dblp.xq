xquery version "1.0";

declare namespace request="http://exist-db.org/xquery/request";
declare namespace session="http://exist-db.org/xquery/session";
declare namespace util="http://exist-db.org/xquery/util";
declare namespace f="http://exist-db.org/dblp-functions";

declare function f:get-query-field($field as xs:string)
as xs:string
{
    if ($field = "any") then
        "."
    else
        $field
};

declare function f:get-path($type as xs:string)
as xs:string
{
	if ($type = "any") then
		"/dblp/*"
	else
		concat("/dblp/", $type)
};

declare function f:get-filter-expr($field as xs:string, $terms as xs:string)
as xs:string
{
	if($terms = "") then
		""
	else
		concat("[", f:get-query-field($field), " &amp;= """, $terms, """]")
};

declare function f:build-query($field as xs:string, $terms as xs:string,
	$type as xs:string)
as xs:string
{
    concat(f:get-path($type), f:get-filter-expr($field, $terms))
};

declare function f:display($hits as node()+, $count as xs:integer) as element()+
{
    let $start := number(request:get-parameter("start", "1")),
		$end := if ($start + 9 < $count) then $start + 9 
			else $count
    return
		if ($start > $count) then
			<p>Reached end of results!</p>
		else
			<dblp hits="{count($hits)}" next="{$end + 1}">
    			{for $p in $start to $end return item-at($hits, $p)}
			</dblp>
};

declare function f:query() as element()
{
    let $field := request:get-parameter("field", "any"),
        $keywords := request:get-parameter("keywords", ""),
		$type := request:get-parameter("type", "any"),
		$previous := session:get-attribute("results")
    return
        if ($keywords = "" and $type = "any") then
			if (exists($previous)) then
				f:display($previous, count($previous))
			else
            	<p>Please specify a search term.</p>
        else
            let $hits := util:eval(f:build-query($field, $keywords, $type)),
				$count := count($hits),
				$s := session:set-attribute("results", $hits)
			return
				if ($count = 0) then
					<p>Nothing found for your query!</p>
				else
					f:display($hits, $count)
};

<document xmlns:xi="http://www.w3.org/2001/XInclude">
	
	<xi:include href="context://header.xml"/>
	<xi:include href="sidebar.xml"/>

	<body>
		<section title="DBLP Bibliography Example">
        
        <form method="get" action="dblp.xq">
			<table bgcolor="#F3F3F3" width="100%" cellpadding="5"
				cellspacing="0" border="0">
				<tr bgcolor="#D9D9D9">
					<th width="20%" align="left">Search for</th>
					<th width="80%" align="left" colspan="2">List of keywords</th>
				</tr>
				<tr>
					<td width="40%">
						<select name="field" size="1">
							<option value="title">Title</option>
							<option value="author|editor">Author/Editor</option>
							<option value="year">Definition</option>
							<option value="any" selected="true">Any Field</option>
						</select>
					</td>
					<td width="60%">
						<input type="text" name="keywords" size="30"/>
					</td>
				</tr>
				<tr>
					<td width="40%"> 
						<select name="type" size="1">
							<option value="any">Any Type</option>
							<option value="article">Article</option>
							<option value="inproceedings">In Proceedings</option>
							<option value="proceedings">Proceedings</option>
							<option value="book">Book</option>
							<option value="incollection">In Collection</option>
							<option value="phdthesis">Phd Thesis</option>
							<option value="mastersthesis">Masters Thesis</option>
							<option value="www">WWW</option>
						</select>
					</td>
					<td width="60%" align="right">
						<input type="submit"/>
					</td>
				</tr>
			</table>
        </form>
        
        { f:query() }
        </section>
    </body>
</document>
