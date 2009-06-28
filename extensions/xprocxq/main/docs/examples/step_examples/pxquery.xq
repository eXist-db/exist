xquery version "1.0" encoding "UTF-8";

import module namespace const = "http://xproc.net/xproc/const";
import module namespace xproc = "http://xproc.net/xproc";
import module namespace u = "http://xproc.net/xproc/util";

declare variable $local:XPROCXQ_EXAMPLES := "/db/examples";   (: CHANGE ME :)

let $stdin :=document{<test/>}

let $pipeline :=document{
                    <p:pipeline name="pipeline"
                                xmlns:p="http://www.w3.org/ns/xproc"
                                xmlns:c="http://www.w3.org/ns/xproc-step">
					           <p:xquery>
					               <p:input port="query">
					                   <p:inline>
					                       <c:query xmlns:c="http://www.w3.org/ns/xproc-step" xproc:escape="true">
					                           let $r := 'this pipeline successfully processed' return $r (: for now default context goes to xml database :)
					                       </c:query>
					                   </p:inline>
					               </p:input>
					           </p:xquery>

                    </p:pipeline>
                }

return
     xproc:run($pipeline,$stdin) 
