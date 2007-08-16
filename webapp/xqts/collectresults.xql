xquery version "1.0";
(: $Id$ :)
declare namespace response="http://exist-db.org/xquery/response";
declare namespace empty="";
declare option exist:serialize "method=xml indent=yes";
declare option exist:output-size-limit "-1";

let $product-version := util:system-property("product-version")
let $product-build := util:system-property("product-build")
let $revision := system:get-revision()
let $test-suite := doc('/db/XQTS/XQTSCatalog.xml')/*:test-suite/@version
let $dummy := response:set-header("Content-Disposition", concat( 
	"attachment; filename=&quot;results_XQTS-" , $test-suite , "_eXist-" , 
	$product-version , '_' , $product-build , ".xml&quot;" ) )

let $cases := fn:collection(  '/db/XQTS' )//test-case
return
<test-suite-result xmlns="http://www.w3.org/2005/02/query-test-XQTSResult">
    <implementation name="eXist-db" version="{$product-version} (revision {$revision} {$product-build})" anonymous-result-column="false">

        <organization name="eXist Open Source Native XML database"  website="http://www.exist-db.org" anonymous="false"/>

        <submittor name="eXist XQTS testsuite runner" email="eXistAdmin@gmail.com"/>

        <description>
                <p>eXist is an Open Source native XML database featuring efficient, 
                index-based XQuery processing, automatic indexing, extensions for full-text 
                search,  XUpdate  support and tight integration with existing XML development tools.</p>
        </description>

        <implementation-defined-items>
                <!--implementation-defined-item name="implicit timezone" value="Z"/>
                <implementation-defined-item  name="DTD validation or schema validation for fn:doc"
                        value="schema validation"/-->
        </implementation-defined-items>

        <features>
        <!-- { (: for $feature in doc('/db/XQTS/XQTSCatalog.xml')/*:test-suite/*:features/*:feature  return
  	    <feature name="{$feature/@name}" supported="true"/> :) } -->
        <feature name="Minimal Conformance" supported="true"/>
        <feature name="Schema Import" supported="false"/>
        <feature name="Schema Validation" supported="false"/>
        <feature name="Static Typing" supported="false"/>
        <feature name="Static Typing Extensions" supported="false"/>
        <feature name="Full Axis" supported="true"/>
        <feature name="Module" supported="true"/>
        <feature name="Serialization" supported="true"/>
        <feature name="Trivial XML Embedding" supported="false"/>
				  </features>


        <context-properties>
                <!--context-property name="Default collation" context-type="static"  value="not overwritten"/>
                <context-property name="Statically known namespaces" context-type="static" value="augmented with xmlns:ac='http://www.ac.com/xquery'"/-->
        </context-properties>

    </implementation>

    <syntax>XQuery</syntax>

    <test-run dateRun="{fn:current-dateTime()}">
        <test-suite version="{$test-suite}"/>
        <transformation>
                <p>No unusual transformations.</p>
        </transformation>
        <comparison>
                <p>No unusual comparisons</p>
        </comparison>
        <otherComments>
                <p>The test run took {let $b := for $a in $cases//@dateRun return xs:time($a)
return (max($b) - min($b)) div xs:dayTimeDuration('PT1S')} seconds.</p>
        </otherComments>
    </test-run>

<!-- Results below here -->

{
    for $case in $cases
    order by $case/@name
    return <test-case name="{$case/@name}" result="{$case/@result}" />
}
</test-suite-result>

 
