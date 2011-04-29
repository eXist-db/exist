(:
 :  eXist Open Source Native XML Database
 :  Copyright (C) 2011 The eXist Project
 :  http://exist-db.org
 :
 :  This program is free software; you can redistribute it and/or
 :  modify it under the terms of the GNU Lesser General Public License
 :  as published by the Free Software Foundation; either version 2
 :  of the License, or (at your option) any later version.
 :
 :  This program is distributed in the hope that it will be useful,
 :  but WITHOUT ANY WARRANTY; without even the implied warranty of
 :  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 :  GNU Lesser General Public License for more details.
 :
 :  You should have received a copy of the GNU Lesser General Public
 :  License along with this library; if not, write to the Free Software
 :  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 :
 :  $Id$
 :)
xquery version "1.0";

(:~
	Post-processes query results for the sandbox application. The
	controller first sends the user-supplied query to XQueryServlet
	for evaluation. The result is then passed to this script, which
	stores the result set into the HTTP session and returns the number
	of hits and time elapsed.

	Subsequent requests from the sandbox application may retrieve single
	items from the result set stored in the session (see controller).
:)

declare namespace sandbox="http://exist-db.org/xquery/sandbox";

declare option exist:serialize "method=xml media-type=text/xml omit-xml-declaration=no indent=no";

(:~
	Pretty print an XML fragment. Returns HTML to highlight the XML syntax.
:)
declare function sandbox:pretty-print($node as item()) {
	typeswitch ($node)
		case $elem as element(exist:match) return
			<span class="xml-match">{$elem/node()}</span>
		case $elem as element() return
			<div class="xml-element">
				<span class="xml-element-tag">&lt;</span>
				<span class="xml-element-name">{node-name($elem)}</span>
				{
					for $attr in $elem/@*
					return (
						' ', <span class="xml-attr-name">{node-name($attr)}</span>,
						'="', <span class="xml-attr-value">{$attr/string()}</span>, '"'
					)
				}
				{
					let $children := $elem/node()
					return
						if (count($children) gt 0) then (
							<span class="xml-element-tag">&gt;</span>,
							for $child in $children
							return
								sandbox:pretty-print($child),
							<span class="xml-element-tag">&lt;/</span>,
							<span class="xml-element-name">{node-name($elem)}</span>,
							<span class="xml-element-tag">&gt;</span>
						) else
							<span class="xml-element-tag">/&gt;</span>
				}
			</div>
		case $text as text() return
			<span class="xml-text">{$text}</span>
		case $comment as comment() return
			<div class="xml-comment">&lt;-- {$comment/string()} --&gt;</div>
		case $pi as processing-instruction() return
			<div style="color: darkred">&lt;?{$pi/string()}?&gt;</div>
		default return
			$node
};

(:~ Retrieve a single query result. :)
declare function sandbox:retrieve($num as xs:integer) as element() {
    let $cached := session:get-attribute("cached")
    let $node := $cached[$num]
    let $item := 
    	if ($node instance of node()) then
    		util:expand($node, 'indent=yes')
    	else
    		$node
    let $documentURI :=if ($node instance of node()) then document-uri(root($node)) else ()
    return
        <div class="{if ($num mod 2 eq 0) then 'even' else 'uneven'}">
            <div class="pos">
            {
                if (string-length($documentURI) > 0) then
                    <a href="{$documentURI}#{util:node-id($node)}">{$num}</a>
                else
                    ()
            }
            </div>
            <div class="item">
            { sandbox:pretty-print($item) }
            </div>
        </div>
};

(:~ Take the query results and store them into the HTTP session. :)
declare function sandbox:store-in-session($results as item()*) as element(result) {
	let $null := session:set-attribute('cached', $results)
	let $elapsed := request:get-attribute("elapsed")
	return
		<result hits="{count($results)}" elapsed="{if ($elapsed) then $elapsed else 0}"/>
};

(: 	When a query has been executed, its results will be passed into
	this script in the request attribute 'results'. The result is then
	stored into the HTTP session. Subsequent requests from the sandbox
	can reference a result item in the session by passing parameter 'num'.
:)
session:create(),
let $input := request:get-data()
let $results := request:get-attribute("results")
let $pos := xs:integer(request:get-parameter("num", ()))
return
    if (string-length($input) gt 0) then
        $input
	else if ($pos) then
		sandbox:retrieve($pos)
	else
		sandbox:store-in-session($results)
