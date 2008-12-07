xquery version "1.0";
(: $Id: livesearch.xq 7972 2008-07-05 20:53:33Z brihaye $ :)

declare namespace f="http://exist-db.org/xquery/test";
declare namespace mods="http://www.loc.gov/mods/v3";

import module namespace t="http://exist-db.org/xquery/text";
import module namespace util="http://exist-db.org/xquery/util";
import module namespace request="http://exist-db.org/xquery/request";

(: declare option exist:output-size-limit "-1"; :)

declare function f:term-callback($term as xs:string, $data as xs:int+) 
as element()+ {
	<li class="LSRow">
	    <a href="javascript:termSelected('{$term}')">
	        <span class="term">{$term}</span> ({$data[2]} hits)
	    </a>
	</li>,
	if ($data[3] eq 15) then
	    <li class="LSRow">
	        <span style="margin-left: 1em;">...</span>
	    </li>
	else
	    ()
};

let $t := request:get-parameter("query", ()),
    $collection := request:get-parameter("collection", "/db")
return
    <ul class="LSRes">
	{
	    t:index-terms(collection($collection), $t, util:function(xs:QName("f:term-callback"),
	    2), 15)
	}
    </ul>