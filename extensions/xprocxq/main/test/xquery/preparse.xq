xquery version "1.0" encoding "UTF-8";

(: XProc Namespace Declaration :)
declare namespace p="http://www.w3.org/ns/xproc";
declare namespace c="http://www.w3.org/ns/xproc-step";
declare namespace err="http://www.w3.org/ns/xproc-error";

(: Module Imports :)

import module namespace test = "http://xproc.net/test"
                        at "../../test/xquery/test.xqm";
import module namespace xproc = "http://xproc.net/xproc"
                        at "../../src/xquery/xproc.xqm";
import module namespace comp = "http://xproc.net/xproc/comp"
                        at "../../src/xquery/comp.xqm";
import module namespace u: "http://xproc.net/xproc/util"
                        at "../../src/xquery/util.xqm";
import module namespace std = "http://xproc.net/xproc/std"
                        at "../../src/xquery/std.xqm";
import module namespace ext = "http://xproc.net/xproc/ext"
                        at "../../src/xquery/ext.xqm";
import module namespace opt = "http://xproc.net/xproc/opt"
                        at "../../src/xquery/opt.xqm";

(: :)
declare variable $source := document{*};


(: -------------------------------------------------------------------------- :)

<testsuite title="preparse XQuery Unit Tests" desc="Test the parsing and ordering of pipeline steps with XProc.xq">

<!--
<test>
    <name>preparse simple</name>
    <result>
{

let $pipeline :=
   <p:pipeline name="pipeline"
            xmlns:p="http://www.w3.org/ns/xproc">
                
  <p:input port="source" primary="true">
    <p:empty/>
  </p:input>

  <p:output port="result" primary="true"/>

   <p:identity name="step1">
        <p:input port="source" primary="true">
              <p:document href="file:test/data/test1.xml"/>
        </p:input>
        <p:output port="result"/>
   </p:identity>

</p:pipeline>
 return xproc:preparse($pipeline)
}
</result>
<expected></expected>
</test>

<test>
    <name>preparse with different inputs</name>
    <result>
{

let $pipeline :=
   <p:pipeline name="pipeline"
            xmlns:p="http://www.w3.org/ns/xproc">
                
   <p:identity name="step1">
        <p:input port="source" primary="true">
              <p:inline>
                    <test>inline test</test>
               </p:inline>
        </p:input>
        <p:output port="result"/>
   </p:identity>

</p:pipeline>

    let $preparse := xproc:preparse($pipeline)
    let $eval_result := xproc:parse($preparse,$source)
    let $serialized_result := xproc:output($eval_result,3)

return
    document
       {
        <xproc:result>
            {
                $serialized_result
            }
        </xproc:result>
        }
}
</result>
<expected></expected>
</test>



<test>
    <name>preparse simple</name>
    <result>
{

let $pipeline :=
   <p:pipeline name="pipeline"
            xmlns:p="http://www.w3.org/ns/xproc">
                
     <p:input port="source" primary="true" select="">
        <p:document href="file:test/data/test.xml"/>
        <p:inline>
              <anothertest/>
          </p:inline>
     </p:input>
    <p:output port="result" primary="true"/>

   <p:identity name="step1">
        <p:input port="source" primary="true">
            <p:pipe step="pipeline" port="source"/>
        </p:input>
        <p:output port="result"/>
   </p:identity>

</p:pipeline>

    let $preparse := xproc:preparse($pipeline)
    let $eval_result := xproc:parse($preparse,$source)
    let $serialized_result := xproc:output($eval_result,0)

return
    document
       {
        <xproc:result>
            {
               $serialized_result
            }
        </xproc:result>
        }
}
</result>
<expected></expected>
</test>


//-->

</testsuite>

(: -------------------------------------------------------------------------- :)
