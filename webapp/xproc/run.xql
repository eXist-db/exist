xquery version "1.0";

import module namespace request = "http://exist-db.org/xquery/request";
import module namespace const = "http://xproc.net/xproc/const";
import module namespace xproc = "http://xproc.net/xproc";
import module namespace u = "http://xproc.net/xproc/util";

declare option exist:serialize "method=xhtml media-type=html";

let $pipeline := request:get-parameter("xproc", ())
let $stdin := request:get-parameter("stdin", ())
let $indoc := if ($stdin) then doc($stdin) else ()
return
     xproc:run(doc($pipeline), $indoc, "0")