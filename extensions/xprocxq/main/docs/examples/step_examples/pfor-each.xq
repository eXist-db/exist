xquery version "1.0" encoding "UTF-8";

import module namespace const = "http://xproc.net/xproc/const";
import module namespace xproc = "http://xproc.net/xproc";
import module namespace naming = "http://xproc.net/xproc/naming";
import module namespace u = "http://xproc.net/xproc/util";

declare variable $local:XPROCXQ_EXAMPLES:= "/db/examples";   (: CHANGE ME :)

let $stdin :=(<test/>,<a/>,<b/>)
let $external-bindings := ()

let $pipeline :=document{<p:pipeline xmlns:p="http://www.w3.org/ns/xproc"
               name="pipeline">

<p:for-each name="test">
	<p:xslt>
	   <p:input port="stylesheet">
	       <p:document href="{$local:XPROCXQ_EXAMPLES}/xslt/stylesheet.xsl"/>
	   </p:input>
	</p:xslt>
</p:for-each>

</p:pipeline>}

return
    xproc:run($pipeline,$stdin,'0')