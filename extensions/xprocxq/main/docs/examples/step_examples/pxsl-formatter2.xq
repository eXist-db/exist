xquery version "1.0" encoding "UTF-8";

import module namespace const = "http://xproc.net/xproc/const";
import module namespace xproc = "http://xproc.net/xproc";
import module namespace naming = "http://xproc.net/xproc/naming";
import module namespace u = "http://xproc.net/xproc/util";

import module namespace xslfo = "http://exist-db.org/xquery/xslfo"; (: required for p:xsl-formatter :)

declare variable $local:XPROCXQ_EXAMPLES := "file:///tmp";   (: CHANGE ME :)

let $stdin :=doc('http://www.xproc.org')             (: get index page of a website :)
  
let $pipeline :=document{<p:pipeline name="pipeline"
            xmlns:p="http://www.w3.org/ns/xproc">

<p:xslt>                                         
   <p:input port="stylesheet">
        (: use antennahouse xhtml2fo xslt transformation :)
       <p:document href="http://www.antennahouse.com/XSLsample/sample-xsl-xhtml2fo/xhtml2fo.xsl"/>
   </p:input>
</p:xslt>

(: generate pdf:)
<p:xsl-formatter href='{$local:XPROCXQ_EXAMPLES}/test.pdf'/>

</p:pipeline>}

return
    xproc:run($pipeline,$stdin,"1")
