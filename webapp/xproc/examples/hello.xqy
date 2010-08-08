xquery version "1.0" encoding "UTF-8";

(: for now you need to declare these namespaces :)
import module namespace xproc = "http://xproc.net/xproc";

(: define standard input source binding :)
let $stdin :=document{<test>Hello World</test>}

(: the xproc pipeline :)
let $pipeline :=document{
<p:declare-step xmlns:p="http://www.w3.org/ns/xproc" name="simple-pipeline">
    <p:xslt>
        <p:input port="stylesheet">
           <p:document href="xmldb:exist:///db/xproc/examples/stylesheet.xml"/>
        </p:input>
    </p:xslt>
</p:declare-step>

                }

return
(: the simplest xproc entry function :)
     (: xproc:run($pipeline,$stdin) :)
     xproc:run($pipeline,$stdin,"0","0",(),())
