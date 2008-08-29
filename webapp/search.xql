xquery version "1.0";

import module namespace xdb="http://exist-db.org/xquery/xmldb";


declare namespace dq="http://exist-db.org/xquery/documentation";

declare option exist:serialize "method=xhtml media-type=text/html expand-xincludes=yes";

declare variable $dq:COLLECTION := "xqdocs";

declare variable $dq:FIELDS :=
	<fields>
		<field name="title">section/title</field>
		<field>para</field>
	</fields>;

(:~
    Collection configuration for the function docs. We use an ngram
    index for fast substring searches.
:)
declare variable $dq:config :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index>
            <fulltext default="none" attributes="no">
				<create qname="title"/>
				<create qname="para"/>
            </fulltext>
        </index>
    </collection>;

declare variable $dq:CHARS_SUMMARY := 120;
declare variable $dq:CHARS_KWIC := 40;

(:~
	Setup function: store the documentation into the db.
:)
declare function dq:setup($adminPass as xs:string) {
    if (empty(doc(concat($dq:COLLECTION, "/download.xml")))) then
        let $setuser := xdb:login("/db", "admin", $adminPass)
        let $confCol := (
            xdb:create-collection("/db/system/config", "db"),
            xdb:create-collection("/db/system/config/db", $dq:COLLECTION)
        )
        let $confStored := 
			xdb:store(concat("/db/system/config/db/", $dq:COLLECTION), 
				"collection.xconf", $dq:config)
        let $output := (
            xdb:create-collection("/db", $dq:COLLECTION),
            xdb:chmod-collection(concat("/db/", $dq:COLLECTION), 508)
        )
		let $home := system:get-exist-home()
    	let $dir := if (doc-available(concat("file:///", $home, "/webapp/download.xml"))) then
            concat($home, "/webapp")
        else if(ends-with($home, "WEB-INF")) then
            substring-before($home, "WEB-INF")
        else
            concat($home)
        return 
			xdb:store-files-from-pattern(concat("/db/", $dq:COLLECTION), $dir, "*.xml", "text/xml")
    else
        ()
};

(:~
	Retrieve the following and preceding text chunks for a given match.

	@param $match the text node containing the match
	@param $mode the selection mode: either "previous" or "following"
:)
declare function dq:get-context($match as element(exist:match), $mode as xs:string) as node()* {
	let $sibs := 
		if ($mode eq 'previous') then 
			$match/preceding::text()
		else
			$match/text()/following::text()
	for $sib in $sibs
	return
		if ($sib/parent::exist:match) then
			<span class="hi">{$sib}</span>
		else
			$sib
};

(:~
	Like fn:substring, but takes a node argument. If the node is an element,
	a new element is created with the same node-name as the old one and the
	shortened text content.
:)
declare function dq:substring($node as node(), $start as xs:int, $count as xs:int) as item()? {
	let $str := substring($node, $start, $count)
	return
		if ($node instance of element()) then
			element { node-name($node) } { $str }
		else
			$str
};

(:~
	Generate the left-hand context of the match. Returns a sequence of nodes
	and strings, whose total string length is less than or equal to $max characters.

	Note: this function calls itself recursively until $nodes is empty or
	the returned sequence has the desired total string length.
:)
declare function dq:truncate-previous($nodes as node()*, $truncated as item()*, 
	$max as xs:int, $chars as xs:int) {
	if ($nodes) then
		let $next := $nodes[last()]
		return
			if ($chars + string-length($next) gt $max) then
				let $remaining := $max - $chars
				return
					("...", dq:substring($next, string-length($next) - $remaining, $remaining), $truncated)
			else
				dq:truncate-previous(subsequence($nodes, 1, count($nodes) - 1), ($next, $truncated),
					$max, $chars + string-length($next))
	else
		$truncated
};

(:~
	Generate the right-hand context of the match. Returns a sequence of nodes
	and strings, whose total string length is less than or equal to $max characters.
	
	Note: this function calls itself recursively until $nodes is empty or
	the returned sequence has the desired total string length.
:)
declare function dq:truncate-following($nodes as node()*, $truncated as item()*, 
	$max as xs:int, $chars as xs:int) {
	if ($nodes) then
		let $next := $nodes[1]
		return
			if ($chars + string-length($next) gt $max) then
				let $remaining := $max - $chars
				return
					($truncated, dq:substring($next, 1, $remaining), "...")
			else
				dq:truncate-following(subsequence($nodes, 2), ($truncated, $next),
					$max, $chars + string-length($next))
	else
		$truncated
};

(:~
	Computes the total string length of the nodes in the argument sequence
:)
declare function dq:string-length($nodes as item()*) as xs:int {
	if (exists($nodes)) then
		sum(for $n in $nodes return string-length($n))
	else
		0
};

(:~
	Print a summary of the match in $node. Output a predefined amount of text to
	the left and the right of the match.

	@param $root the root element containing the match. This is the original element
		   stored in the database.
	@param $node the exist:match element to process.
	@param $mode display type: either "summary" or "kwic"
	@param $docXPath the xpath expression that generated the match.
:)
declare function dq:print-summary($root as node(), $node as element(exist:match), 
	$mode as xs:string, $docXPath as xs:string) as element() {
	let $chars := if ($mode eq "summary") then $dq:CHARS_SUMMARY else $dq:CHARS_KWIC
	let $prev := dq:get-context($node, 'previous')
	let $prevTrunc := dq:truncate-previous($prev, (), $chars, 0)
	let $remain := 
		if ($mode eq "summary") then 
			$chars * 2 - dq:string-length($prevTrunc)
		else
			$chars
	let $following := dq:get-context($node, 'following')
	let $followingTrunc := dq:truncate-following($following, (), $remain, 0)
	let $nodeId := util:node-id($root)
	let $uri := concat(
		"docs.xql?path=", document-uri(root($root)), "&amp;q=",
		escape-uri($docXPath, true()), "&amp;id=", $nodeId, "#", $nodeId
	)
	return
		if ($mode eq "summary") then
			<p class="summary">
				<a href="{$uri}">
					<span class="previous">{$prevTrunc}</span>
					<span class="hi" style="font-weight: bold">
					{ $node/text() }
					</span>
					<span class="following">{$followingTrunc}</span>
				</a>
			</p>
		else
			<tr>
				<td class="previous">{$prevTrunc}</td>
				<td class="hi">{$node/text()}</td>
				<td class="following">{$followingTrunc}</td>
			</tr>
};

(:~
	Display the hits: this function first calls util:expand() to get an in-memory
	copy of each hit with full-text matches tagged with &lt;exist:match&gt;. It
	then calls dq:print-summary for each exist:match element.
:)
declare function dq:print($hits as element()+, $docXPath as xs:string, $mode as xs:string)
as element()* {
	for $hit in $hits
	let $expanded := util:expand($hit)
	for $match in $expanded//exist:match[1]
	return
		dq:print-summary($hit, $match, $mode, $docXPath)
};

(:~
	Print the hierarchical context of a hit.
:)
declare function dq:print-headings($section as element(section)*, $docXPath as xs:string) {
	$section/ancestor::chapter/title/text(),
	for $s at $p in $section/ancestor-or-self::section
	let $nodeId := util:node-id($s)
	let $uri := concat(
		"docs.xql?path=", document-uri(root($s)), "&amp;q=",
		escape-uri($docXPath, true()), "&amp;id=", $nodeId, "#", $nodeId
	)
	return
		(" > ", <a href="{$uri}">{$s/title/text()}</a>)
};

(:~
	Display the query results.
:)
declare function dq:print-results($hits as element()*, $docXPath as xs:string) {
	let $mode := request:get-parameter("view", "summary")
	let $sections := $hits/ancestor::section[1]
	return
		<div id="f-results">
			<p id="f-results-heading">Found: {count($hits)} in {count($sections)} sections.</p>
			{
				if ($mode eq 'summary') then
					for $section in $sections
					let $hitsInSect := $section//$hits
					return
						<div class="section">
							<div class="headings">{ dq:print-headings($section, $docXPath) }</div>
						{ dq:print($hitsInSect, $docXPath, $mode) }
						</div>
				else
					<table class="kwic">
					{
						for $section in $sections
						let $hitsInSect := $section//$hits
						return (
							<tr>
								<td class="headings" colspan="3">
								{dq:print-headings($section, $docXPath)}
								</td>
							</tr>,
							dq:print($hitsInSect, $docXPath, $mode)
						)
					}
					</table>
			}
		</div>
};

(:~
	Create and return XPath expressions corresponding to the fields to
	be queried.
:)
declare function dq:create-query($fields as element(field)*, $query as xs:string) {
	for $field in $fields
	return
		concat($field/string(), "[. &amp;= '",	$query, "']")
};

(:~
	Process the query.
:)
declare function dq:query() {
	let $query := request:get-parameter("q", ())
	let $field := request:get-parameter("field", "all")
	return
		if ($query) then
			let $fields := 
				if ($field ne "all") then $dq:FIELDS/field[@name = $field] else $dq:FIELDS/field
			let $queryParts := dq:create-query($fields, $query)
			let $xpath := string-join(
				for $p in $queryParts return
					concat("collection('/db/", $dq:COLLECTION, "')/book//", $p),
				" | "
			)
			let $docXPath := string-join(for $p in $queryParts return concat(".//", $p), " or ")
			let $hits := util:eval($xpath)
			return
				dq:print-results($hits, $docXPath)
		else
			()
};

(:~
    Return the main XML page, which will be transformed into HTML by Cocoon.
    If Javascript is enabled on the client, this function will only be called
    once. All subsequent calls to this script will be made via AJAX and we don't
    need to return the entire page.
:)
declare function dq:get-page($action as xs:string?, $askPass as xs:boolean) 
as element() {
    <book>
        <bookinfo>
            <graphic fileref="logo.jpg"/>
    
            <productname>Open Source Native XML Database</productname>
            <title>Documentation Search</title>
            <link rel="stylesheet" type="text/css" href="styles/docsearch.css"/>
            <script type="text/javascript" src="scripts/yui/utilities.js"/>
        </bookinfo>
        
    	<xi:include xmlns:xi="http://www.w3.org/2001/XInclude" href="sidebar.xml"/>
        <chapter>
            <title>Search the Documentation</title>
            {
                if ($askPass) then
                    <form id="f-pass" name="f-pass" action="search.xql" method="POST">
                        <para>The documentation needs to be loaded into the first,
                        which requires administrator rights. Please enter the
                        password for the admin user below:</para>
                        <input type="password" name="pass" value=""/>
                        <input type="hidden" name="generate" value="true"/>
                        <button type="submit">Generate</button>
                    </form>
                else (
                    <div id="f-search">
                        <form name="f-query" action="search.xql" method="GET">
                            <table>
                                <tr>
                                    <td colspan="2">
                                        <label for="q">Search:</label>
                                        <input name="q" type="text" 
											value="{request:get-parameter('q', '')}"/>
                                        <label for="field">in</label>
                                        <select name="field">
                                            <option value="all">All</option>
                                            <option value="title">Headings Only</option>
                                        </select>
                                    </td>
									<td>
										<label for="view">View:</label>
										<select name="view">
											<option value="summary">Summary</option>
											<option value="kwic">One Line</option>
										</select>
									</td>
									<td class="f-btn">
                                        <input id="f-btn-search" type="submit" 
                                            name="action" value="Search"/>
                                    </td>
                                </tr>
                            </table>
                            <input type="hidden" name="prev" value="{$action}"/>
                        </form>
                        <p class="f-info">(<b>eXist version: {util:system-property("product-version")}, 
                        build: {util:system-property("product-build")}</b>).
                        </p>
                        <div id="f-result">
						{ dq:query() }
                        </div>
                    </div>
                )
            }
        </chapter>
    </book>
};

let $askPass :=
    if (empty(collection($dq:COLLECTION))) then
        let $adminPass := request:get-parameter("pass", ())
        let $generate := request:get-parameter("generate", ())
        return
            if ($generate) then
                let $dummy := dq:setup($adminPass)
                return false()
            else
                true()
    else
        false()
return
	transform:transform(dq:get-page((), $askPass), 
		"stylesheets/db2html.xsl", (), "expand-xincludes=yes")
