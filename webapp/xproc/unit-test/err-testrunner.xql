xquery version "1.0" encoding "UTF-8";

(:NOTE - requires file module to be enabled :)
import module namespace file="http://exist-db.org/xquery/file";
import module namespace xproc = "http://xproc.net/xproc";
import module namespace u = "http://xproc.net/xproc/util" at "resource:net/xproc/xprocxq/src/xquery/util.xqm";

declare namespace p="http://www.w3.org/ns/xproc";
declare namespace t="http://xproc.org/ns/testsuite";
declare namespace c="http://www.w3.org/ns/xproc-step";

declare option exist:serialize "method=html media-type=text/html omit-xml-declaration=yes indent=yes";

declare function local:sequence-deep-equal
  ( $seq1 as item()* ,
    $seq2 as item()* )  as xs:boolean {

  every $i in 1 to max((count($seq1),count($seq2)))
  satisfies deep-equal($seq1[$i],$seq2[$i])
 } ;

let $logon :=  xmldb:login("/","admin", "", fn:false())
let $runtime-debug := request:get-parameter("dbg", ())
let $format := request:get-parameter("format", ())
let $not :=  request:get-parameter("not", ())
let $type :=  request:get-parameter("type", ())
let $dir := concat('/Users/jimfuller/Source/Thirdparty/eXist/extensions/xprocxq/main/test/tests.xproc.org/',$type)
let $file :=  request:get-parameter("file", ())
let $files := file:directory-list($dir,$file)

let $result := for $file in $files/*:file[not(contains(@name,$not))]
let $path     := concat('file://',$dir,$file/@name)
let $test     := doc($path)
let $error    := fn:string($test/t:test/@error)

let $stdin    := for $item in util:catch("*",$test//t:input[@port='source']/*,"no input")
                return
                 document{$item}
let $bindings := for $item in util:catch("*",if ($test//t:input[fn:not(@port='source')]) then <bindings>{$test//t:input[fn:not(@port='source')]}</bindings> else (),())
                return
                 document{$item}
let $pipeline := util:catch("*",$test//t:pipeline,"no pipeline")
let $expected := if($error) then fn:true() else util:catch("*",$test//t:output/*,"no expected")
let $result   :=  util:catch("*",xproc:run($pipeline,$stdin,$runtime-debug,$bindings,()),("ERROR: ",$util:exception-message))
let $pass     := if ($error) then
    fn:contains($result,$error)
  else
    if (contains($result,"ERROR:")) then
      fn:false()
    else if (count($expected/*) eq 1 and count($result/*) eq 1 ) then
        fn:deep-equal(u:treewalker($expected),u:treewalker($result))
    else
       fn:deep-equal($expected,$result)
return

<test pass="{$pass}" uri="{$file/@name}" title="{$test//t:title/node()}">
  <div style="background-color:{if($pass) then "green" else "red"}"><h4>{fn:deep-equal($expected,$result)} - {$test//t:title/node()} - {fn:string($test/t:test/@error)}</h4>
  <textarea rows="5" cols="25"><t/> {$stdin}</textarea>
  <textarea rows="5" cols="25"><t/> {$bindings}</textarea>
  <textarea rows="5" cols="30"><t/> {$pipeline/*}</textarea>
  <textarea rows="5" cols="30"><t/> {$result}</textarea>
  <textarea rows="5" cols="30"><t/> {$expected}</textarea>
  </div>
</test>

return

if ($format eq 'w3c') then
      transform:transform(document{<tests>{$result}</tests>}, xs:anyURI("w3c-test-result.xsl"), ())
else
  $result