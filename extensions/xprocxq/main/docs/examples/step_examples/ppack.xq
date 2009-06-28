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

                    <p:pack  wrapper="root">
                       <p:input port="alternate">
                         <p:inline>
                           <message>This demonstrates how 2 xml docs are concatanated using p:pack.</message>
                         </p:inline>
                       </p:input>
                    </p:pack>

        </p:pipeline>
                }

return
     xproc:run($pipeline,$stdin,'0') 
