xquery version "1.0" encoding "UTF-8";

declare namespace t="http://xproc.org/ns/testsuite";
declare namespace c="http://www.w3.org/ns/xproc-step";

import module namespace const = "http://xproc.net/xproc/const";
import module namespace xproc = "http://xproc.net/xproc";
import module namespace u = "http://xproc.net/xproc/util";
import module namespace naming = "http://xproc.net/xproc/naming";

(:NOTE - requires file module to be enabled :)

declare option exist:serialize "method=html media-type=text/html omit-xml-declaration=yes indent=yes";

let $not :=  request:get-parameter("not", ())
let $type :=  request:get-parameter("type", ())
let $dir := concat('/Users/jimfuller/Source/Thirdparty/eXist/extensions/xprocxq/main/test/tests.xproc.org/',$type)
let $file :=  request:get-parameter("file", ())
let $files := file:directory-list($dir,$file)
let $result := for $file in $files/*:file[not(contains(@name,$not))]

let $path := concat('file://',$dir,$file/@name)

let $stdin1 :=doc($path)/t:test

let $stdin :=  if ($stdin1//t:pipeline/@href) then

<t:test xmlns:t="http://xproc.org/ns/testsuite"
        xmlns:p="http://www.w3.org/ns/xproc"
        xmlns:c="http://www.w3.org/ns/xproc-step"
        xmlns:err="http://www.w3.org/ns/xproc-error">

{$stdin1//t:input[not(@href)]}
{let $a := $stdin1//t:input[@href]
return
for $input in $a
return
		<t:input port="{$input/@port}">
			{let $doc := doc(concat('file://',$dir,$input/@href))
			return
			    $doc}
		</t:input>
}
<t:pipeline>
{let $doc := doc(concat('file://',$dir,$stdin1//t:pipeline/@href))
return
    $doc
}
</t:pipeline>

{$stdin1//t:output[not(@href)]}
{let $a := $stdin1//t:output[@href]
return
for $input in $a
return
		<t:output port="{$input/@port}">
			{let $doc := doc(concat('file://',$dir,$input/@href))
			return
			    $doc}
		</t:output>
}
</t:test>
               else

<t:test xmlns:t="http://xproc.org/ns/testsuite"
        xmlns:p="http://www.w3.org/ns/xproc"
        xmlns:c="http://www.w3.org/ns/xproc-step"
        xmlns:err="http://www.w3.org/ns/xproc-error">

{$stdin1//t:input[not(@href)]}
{let $a := $stdin1//t:input[@href]
return
for $input in $a
return
		<t:input port="{$input/@port}">
			{let $doc := doc(concat('file://',$dir,$input/@href))
			return
			    $doc}
		</t:input>
}

{$stdin1//t:pipeline}

{$stdin1//t:output[not(@href)]}
{let $a := $stdin1//t:output[@href]
return
for $input in $a
return
		<t:output port="{$input/@port}">
			{let $doc := doc(concat('file://',$dir,$input/@href))
			return
			    $doc}
		</t:output>
}
</t:test>

let $pipeline := if (count($stdin//t:input) = 0) then
        doc('/db/xproc/unit-test/test-runner2.xml')
    else if ( count($stdin//t:input) = 1) then
        doc('/db/xproc/unit-test/test-runner.xml')
    else
        doc('/db/xproc/unit-test/test-runner1.xml')

let $runtime-debug := request:get-parameter("dbg", ())
    return
    <test file="{$file/@name}">
        {
        if (contains($path,request:get-parameter("debug", ()))) then
           xproc:run($pipeline,$stdin,$runtime-debug)
        else
            util:catch('*', xproc:run($pipeline,$stdin,$runtime-debug), 'test crashed')
         }
     </test>
return
let $final-result := <result pass="{count($result//c:result[.= 'true'])}" nopass="{count($result//c:result[.= 'false'])}" total="{count($result//test)}">
    {$result}
</result>
let	$params :=
		<parameters>
			<param name="now" value="{current-time()}"/>
		</parameters>
return
    if (request:get-parameter("format", ()) eq 'html') then
      transform:transform(document{$final-result}, xs:anyURI("xmldb:exist:///db/xproc/unit-test/test-result.xsl"), ())
    else
        $final-result