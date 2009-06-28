xquery version "1.0" encoding "UTF-8";

import module namespace const = "http://xproc.net/xproc/const";
import module namespace xproc = "http://xproc.net/xproc";
import module namespace u = "http://xproc.net/xproc/util";

let $stdin := document{<test><a><b/></a></test>}

let $pipeline :=document{
                    <p:pipeline name="pipeline"
                                xmlns:p="http://www.w3.org/ns/xproc"
                                xmlns:c="http://www.w3.org/ns/xproc-step">
						<p:delete match="//b"/>
                    </p:pipeline>
                }

return
     xproc:run($pipeline,$stdin) 
