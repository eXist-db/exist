xquery version "1.0";
(: $Id$ :)
declare namespace response="http://exist-db.org/xquery/response";
declare namespace catalog="http://www.w3.org/2005/02/query-test-XQTSCatalog";
declare namespace hack="http://exist-db.org/xquery/xqts/hacked-test-cases";
declare option exist:serialize "method=xml indent=yes";
declare option exist:output-size-limit "-1";

let $product-version := util:system-property("product-version")
let $product-build := util:system-property("product-build")
let $revision := system:get-revision()
let $test-suite := doc('/db/XQTS/XQTSCatalog.xml')/catalog:test-suite/@version
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
        <!-- { (: for $feature in doc('/db/XQTS/XQTSCatalog.xml')/catalog:test-suite/catalog:features/catalog:feature  return
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
      <context-property name="Statically known namespaces" context-type="static" 
      value="XQuery default augmented with xmlns:exist='http://exist.sourceforge.net/NS/exist' (unbound when the XQTS is run)"/>
      <context-property name="Default element/type namespace" context-type="static" value="XQuery default"/>
      <context-property name="Default function namespace" context-type="static" 
      value="'http://www.w3.org/2005/xpath-functions' by default"/>
      <context-property name="In-scope schema types" context-type="static" value="N/A"/>
      <context-property name="In-scope element declarations" context-type="static" value="N/A"/>
      <context-property name="In-scope attribute declarations" context-type="static" value="N/A"/>
      <context-property name="In-scope variables" context-type="static" value="None"/>
      <context-property name="Context item static type" context-type="static" value="item()?"/>
      <context-property name="Function signatures" context-type="static" value="XQuery default"/>
      <context-property name="Statically known collations" context-type="static" value="See http://exist.sourceforge.net/xquery.html#N10676"/>
      <context-property name="Default collation" context-type="static" value="http://www.w3.org/2005/xpath-functions/collation/codepoint"/>
      <context-property name="Construction mode" context-type="static"/>
      <context-property name="Ordering mode" context-type="static"/>
      <context-property name="Default order for empty sequences" context-type="static"/>
      <context-property name="Boundary-space policy" context-type="static"/>
      <context-property name="Copy-namespaces mode" context-type="static"/>
      <context-property name="Base URI" context-type="static" value="'/db' by default"/>
      <context-property name="Statically known documents" context-type="static" value="By default, all the documents stored in the database for which the user has required permissions"/>
      <context-property name="Statically known collections" context-type="static" value="By default, all the collections stored in the database for which the user has required permissions"/>
      <context-property name="Statically known default collection type" context-type="static"/>
      <context-property name="Context item" context-type="dynamic"/>
      <context-property name="Context position" context-type="dynamic"/>
      <context-property name="Context size" context-type="dynamic"/>
      <context-property name="Variable values" context-type="dynamic"/>
      <context-property name="Function implementations" context-type="dynamic"/>
      <context-property name="Current dateTime" context-type="dynamic" value="Settable using an option, a pragma, or API"/>
      <context-property name="Implicit timezone" context-type="dynamic" value="Settable using an option, a pragma, or API"/>
      <context-property name="Available documents" context-type="dynamic" value="By default, all the documents stored in the database for which the user has required permissions + external URLs"/>
      <context-property name="Available collections" context-type="dynamic" value="By default, all the collections stored in the database for which the user has required permissions"/>
      <context-property name="Default collection" context-type="dynamic" value="By default, all the collections stored in the database for which the user has required permissions"/>
      <context-property name="expressionUnicode" context-type="static" value="UTF-8"/>
      <context-property name="collations" context-type="static" value="By default, 'http://www.w3.org/2005/xpath-functions/collation/codepoint'"/>
      <context-property name="implicitTimezone" context-type="static" value="By default, the system's one"/>
      <context-property name="errorsMethod" context-type="static" value="Errors are thrown as an org.exist.xquery.XPathException. The exception's getMessage() method contains the error code."/>
        </context-properties>

    </implementation>

    <syntax>XQuery</syntax>

    <test-run dateRun="{fn:current-dateTime()}">
        <test-suite version="{$test-suite}"/>
        <transformation>
        {
            if (empty(doc('/db/XQTS/hacked-tests.xml')/hack:test-cases/hack:test-case/hack:expected-result)) then
                <p>No unusual transformations.</p>
            else
                <p>The following tests have been evaluated against results provided by other XQuery processors.</p>,
                (: this paragraph element is not really wanted but we want it to be processed by the XSLT :)
                (:see http://www.w3.org/Bugs/Public/show_bug.cgi?id=5217 :) 
                <p> 
                <table xmlns="" border="1">
                    <th><td>Test name</td><td>Reference software</td><td>Considered result</td></th>
                    {
                        for $hackedTest in doc('/db/XQTS/hacked-tests.xml')/hack:test-cases/hack:test-case/hack:expected-result
                        return
                            <tr>
                                <td>{string($hackedTest/../@name)}</td>
                                <td>{string($hackedTest/@from)}</td>
                                <td>{string($hackedTest)}</td>
                            </tr>
                     }
                </table>
                </p>
                
        }
        </transformation>
        <comparison>
        {
            if (empty(doc('/db/XQTS/hacked-tests.xml')/hack:test-cases/hack:test-case/@compare)) then
                <p>No unusual comparisons</p>
            else
                <p>The following tests have required a comparison method different from the standard one.</p>,
                (:see http://www.w3.org/Bugs/Public/show_bug.cgi?id=5217 :)
                <p> 
                <table xmlns="" border="1">
                    <th><td>Test name</td><td>Comparison method</td></th>
                    {
                        for $hackedTest in doc('/db/XQTS/hacked-tests.xml')/hack:test-cases/hack:test-case[@compare]
                        return
                            <tr>
                                <td>{string($hackedTest/@name)}</td>
                                <td>{string($hackedTest/@compare)}</td>
                            </tr>
                     }
                </table>
                </p>,
                <p>UnnormalizedText : result text nodes are concatenated without introducing (normalized) spaces between them.</p>,
                <p>TextAsXML : result string is evaluated as if it were XML, then an XML comparison is done.</p>
        }
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

 
