xquery version "1.0";

(::pragma exist:output-size-limit -1::)

declare namespace f="http://exist-db.org/xquery/test";

import module namespace t="http://exist-db.org/xquery/text";
import module namespace util="http://exist-db.org/xquery/util";
import module namespace request="http://exist-db.org/xquery/request";

declare function f:term-callback($term as xs:string, $freq as xs:integer,
$docs as xs:integer) as element() {
	<li class="LSRow">
	    <a href="?query={$term}">
	        <span class="term">{$term}</span> ({$docs} hits)
	    </a>
	</li>
};

let $t := request:request-parameter("query", ()),
    $collection := request:request-parameter("collection", "/db")
return
    <ul class="LSRes">
	{
		if ($t) then
		    t:index-terms(collection($collection), $t, util:function("f:term-callback", 3))
		else
		    ()
	}
    </ul>
