xquery version "1.0";

import module namespace xdb="http://exist-db.org/xquery/xmldb";

import module namespace kwic="http://exist-db.org/xquery/kwic" at "kwic.xql";

import module namespace setup="http://exist-db.org/xquery/docs/setup" at "docsetup.xql";

declare namespace dq="http://exist-db.org/xquery/documentation";

declare option exist:serialize "method=xhtml media-type=text/html expand-xincludes=yes";

declare variable $dq:COLLECTION := "xqdocs";

declare variable $dq:FIELDS :=
	<fields>
		<field name="title">section/title</field>
		<field>para</field>
	</fields>;

declare variable $dq:CHARS_SUMMARY := 120;
declare variable $dq:CHARS_KWIC := 40;

(:~
	Display the hits: this function iterates through all hits and calls
	kwic:summarize to print out a summary of each match.
:)
declare function dq:print($hits as element()+, $docXPath as xs:string, $mode as xs:string)
as element()* {
	for $hit in $hits
	let $nodeId := util:node-id($hit)
	let $uri := concat(
		(: util:document-name(root($hit)), "?q=", :)
		"docs.xql?path=", document-uri(root($hit)), "&amp;q=",
		escape-uri($docXPath, true()), "&amp;id=", $nodeId, "#", $nodeId
	)
	let $config :=
		<config xmlns="" width="{if ($mode eq 'summary') then $dq:CHARS_SUMMARY else $dq:CHARS_KWIC}"
			table="{if ($mode eq 'summary') then 'no' else 'yes'}"
			link="{$uri}"/>
	return
		kwic:summarize($hit, $config) 
};

(:~
	Print the hierarchical context of a hit.
:)
declare function dq:print-headings($section as element(section)*, $docXPath as xs:string) {
	$section/ancestor::chapter/title//text(),
	for $s at $p in $section/ancestor-or-self::section
	let $nodeId := util:node-id($s)
	let $uri := concat(
		"docs.xql?path=", document-uri(root($s)), "&amp;q=",
		(: util:document-name(root($s)), "?q=", :)
		escape-uri($docXPath, true()), "&amp;id=", $nodeId, "#", $nodeId
	)
	return
		(" > ", <a href="{$uri}">{$s/title//text()}</a>)
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
                let $dummy := setup:setup($adminPass)
                return false()
            else
                true()
    else
        false()
return
	(: dq:get-page((), $askPass) :)
	transform:transform(dq:get-page((), $askPass), 
		"stylesheets/db2html.xsl", (), "expand-xincludes=yes")
