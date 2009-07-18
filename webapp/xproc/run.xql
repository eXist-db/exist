xquery version "1.0";

import module namespace request = "http://exist-db.org/xquery/request";
import module namespace const = "http://xproc.net/xproc/const";
import module namespace xproc = "http://xproc.net/xproc";
import module namespace u = "http://xproc.net/xproc/util";

declare option exist:serialize "method=xhtml media-type=html";

let $pipeline := request:get-parameter("xproc", ())
let $stdin := request:get-parameter("stdin", ())
let $indoc := if ($stdin) then doc($stdin) else ()
let $debug := if(request:get-parameter("debug", ())) then
					request:get-parameter("debug", ())
				 else
					'0'
return
     xproc:run(doc($pipeline), $indoc, $debug)
