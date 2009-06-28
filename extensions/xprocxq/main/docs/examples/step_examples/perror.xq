xquery version "1.0" encoding "UTF-8";

import module namespace const = "http://xproc.net/xproc/const";
import module namespace xproc = "http://xproc.net/xproc";
import module namespace u = "http://xproc.net/xproc/util";

let $stdin :=document{<doc>
                    	<title>Title</title>
                    	<p>Some bad document .</p>
                      </doc>
                      }

let $pipeline :=document{
                    <p:pipeline name="pipeline"
                                xmlns:p="http://www.w3.org/ns/xproc"
                                xmlns:c="http://www.w3.org/ns/xproc-step">

<p:error xmlns:my="http://www.example.org/error"
         name="bad-document" code="my-error-code-1">
   <p:input port="source">
     <p:inline>
       <message>The document is bad for unexplained reasons.</message>
     </p:inline>
   </p:input>
</p:error>
</p:pipeline>
                }

return
     xproc:run($pipeline,$stdin,'0') 
