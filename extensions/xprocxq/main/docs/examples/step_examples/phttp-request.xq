xquery version "1.0" encoding "UTF-8";

(: example test for xprocxq:)

import module namespace const = "http://xproc.net/xproc/const";
import module namespace xproc = "http://xproc.net/xproc";
import module namespace naming = "http://xproc.net/xproc/naming";
import module namespace u = "http://xproc.net/xproc/util";

declare variable $local:XPROCXQ_EXAMPLES := "/db/examples";   (: CHANGE ME :)

let $stdin :=()

let $pipeline :=document{<p:pipeline xmlns:p="http://www.w3.org/ns/xproc"
                                     xmlns:c="http://www.w3.org/ns/xproc-step"
                           name="pipeline">

<p:http-request name="http-get">  (: http get test step :)
<p:input port="source">
  <p:inline>
    <c:request xmlns:c="http://www.w3.org/ns/xproc-step" 
               href="http://tests.xproc.org/service/fixed-xml" 
               method="get"/>
  </p:inline>
</p:input>
</p:http-request>

<p:filter select="/doc"/>

</p:pipeline>}

return
    xproc:run($pipeline,$stdin)
    