xquery version "1.0" encoding "UTF-8";

import module namespace const = "http://xproc.net/xproc/const";
import module namespace xproc = "http://xproc.net/xproc";
import module namespace naming = "http://xproc.net/xproc/naming";
import module namespace u = "http://xproc.net/xproc/util";

declare variable $local:XPROCXQ_EXAMPLES := "/db/examples";   (: CHANGE ME :)

let $stdin :=<test><a/></test>
  
let $pipeline :=document{<p:pipeline name="pipeline"
            xmlns:p="http://www.w3.org/ns/xproc">

<p:escape-markup/>

<!-- <p:unescape-markup/> //-->

</p:pipeline>}

return
    xproc:run($pipeline,$stdin)