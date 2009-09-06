xquery version "1.0";

import module namespace request="http://exist-db.org/xquery/request";

declare namespace dq="http://exist-db.org/xquery/documentation";

declare option exist:serialize "add-exist-id=all highlight-matches=all";
						    
let $path := request:get-parameter("path", ())
let $query := request:get-parameter("q", ())
return util:eval(concat("doc($path)/*[", $query, "]"))