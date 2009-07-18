xquery version "1.0" encoding "UTF-8";

(: for now you need to declare these namespaces :)
import module namespace const = "http://xproc.net/xproc/const";
import module namespace xproc = "http://xproc.net/xproc";
import module namespace u = "http://xproc.net/xproc/util";

(: define standard input source binding :)
let $stdin :=document{<test>Hello World</test>}

(: the xproc pipeline :)
let $pipeline :=document{
                    <p:pipeline name="pipeline"
                                xmlns:p="http://www.w3.org/ns/xproc">
                        <p:identity/>
                    </p:pipeline>
                }

return
(: the xproc entry function :)
     xproc:run($pipeline,$stdin)