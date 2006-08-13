declare option exist:serialize "method=xml indent=yes";
declare option exist:output-size-limit "-1";
<test-suite-result xmlns="http://www.w3.org/2005/02/query-test-XQTSResult"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">


        <implementation name="{util:system-property("product-version")}" version="{util:system-property("product-build")}" anonymous-result-column="false">

                <organization name="eXist Open Source native XML database"  website="http://www.exist-db.org" anonymous="false"/>

                <submittor name="eXist XQTS testsuite runner" email="me@not.com"/>

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
{ for $feature in doc('/db/XQTS/XQTSCatalog.xml')/*:test-suite/*:features/*:feature
							return
							<feature name="{$feature/@name}" supported="true"/>
						}</features>


                <context-properties>
                        <!--context-property name="Default collation" context-type="static"  value="not overwritten"/>
                        <context-property name="Statically known namespaces" context-type="static" value="augmented with xmlns:ac='http://www.ac.com/xquery'"/-->
                </context-properties>

        </implementation>

        <syntax>XQuery</syntax>

        <test-run dateRun="{fn:current-dateTime()}">
                <test-suite version="{doc('/db/XQTS/XQTSCatalog.xml')/*:test-suite/@version}"/>
                <transformation>
                        <p>No unusual transformations.</p>
                </transformation>
                <comparison>
                        <p>No unusual comparisons</p>
                </comparison>
                <otherComments>
                        <p>The test run took n seconds.</p>
                </otherComments>
        </test-run>

				<!-- Results below here -->
{
    for $case in fn:collection(  '/db/XQTS' )//*:test-case
    return <test-case name="{$case/@name}" result="{$case/@result}"  />
}
</test-suite-result>

 