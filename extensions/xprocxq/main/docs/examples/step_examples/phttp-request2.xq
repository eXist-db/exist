xquery version "1.0" encoding "UTF-8";

(: Run this example first to check that everything is working properly:)

import module namespace const = "http://xproc.net/xproc/const";
import module namespace xproc = "http://xproc.net/xproc";
import module namespace u = "http://xproc.net/xproc/util";

declare namespace atom="http://www.w3.org/2005/Atom";

declare variable $local:XPROCXQ_EXAMPLES := "/db/examples";   (: CHANGE ME :)

let $stdin :=(<test>Hello <a>World</a></test>)

let $pipeline :=document{<p:pipeline name="pipeline"
    xmlns:p="http://www.w3.org/ns/xproc"
    xmlns:c="http://www.w3.org/ns/xproc-step"
    xmlns:atom="http://www.w3.org/2005/Atom">
    <p:http-request name="http-get">
        <p:input port="source">
            <p:inline>
                <c:request href="http://twitter.com/statuses/user_timeline/existdb.atom" 
                    method="get"/>
            </p:inline>
        </p:input>
    </p:http-request>

<p:filter select="//atom:feed"/>

</p:pipeline>
                }

return
     xproc:run($pipeline,$stdin,'0') 
