xquery version "1.0" encoding "UTF-8";

declare namespace c="http://www.w3.org/ns/xproc-step";

let $path := 'http://127.0.0.1:8080/exist/rest/db/examples/testrunner.xq?test='

let $identity := doc(concat($path,'identity-*.xml'))
let $count := doc(concat($path,'count-*.xml'))
let $evalorder := doc(concat($path,'evaluation-order-*.xml'))
let $choose := doc(concat($path,'choose-001.xml'))
let $foreach := doc(concat($path,'for-each-001.xml'))
let $xslt := doc(concat($path,'xslt.xml'))


(:
let $xslt := doc(concat($path,'xslt-*.xml'))
:)
let $result := <test>
{$identity}
{$count}
{$evalorder}
{$choose}
{$foreach}
{$xslt}
</test>

return
<test pass="{count($result//c:result[.= 'true'])}" nopass="{count($result//c:result[.= 'false'])}" fail="{count($result//c:result[.= 'fail'])}">
{$result}
</test>