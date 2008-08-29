xquery version "1.0";

import module namespace request="http://exist-db.org/xquery/request";

declare namespace dq="http://exist-db.org/xquery/documentation";

declare option exist:serialize "method=xhtml media-type=text/html add-exist-id=all highlight-matches=all expand-xincludes=yes";

let $path := request:get-parameter("path", "")
let $id := request:get-parameter("id", "1")
let $query := request:get-parameter("q", ())

let $doc := util:eval(concat("doc($path)/*[", $query, "]"))
return
	transform:stream-transform($doc, "stylesheets/db2html.xsl", (), 
		"expand-xincludes=yes add-exist-id=all highlight-matches=all")
