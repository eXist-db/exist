xquery version "1.0";

import module namespace xdb="http://exist-db.org/xquery/xmldb";
import module namespace xdiff="http://exist-db.org/xquery/xmldiff"
at "java:org.exist.xquery.modules.xmldiff.XmlDiffModule";
import module namespace v="http://exist-db.org/versioning"
at "resource:org/exist/versioning/xquery/versioning.xqm";

declare namespace t="http://exist-db.org/xquery/test";

declare function t:setup() {
    xdb:create-collection("/db/system/config", "db"),
    xdb:create-collection("/db", "test"),
    xdb:store("/db/system/config/db", "collection.xconf",
        <collection xmlns="http://exist-db.org/collection-config/1.0">
            <triggers>
                <trigger event="store,remove,update"
                    class="org.exist.versioning.VersioningTrigger">
                </trigger>
            </triggers>
        </collection>
    ),
    xdb:store-files-from-pattern("/db/test", "extensions/versioning/test", "*.xml")
};

declare function t:store($revision as element(v:revision), $docId as xs:string) {
    xdb:store("/db/test", $docId,
        $revision/*
    )
};

declare function t:store-revisions($test as element(v:test), $docId as xs:string) {
    for $rev in $test/v:revision
    return
        t:store($rev, $docId)
};

declare function t:test($test as element(v:test)) {
    let $docId := concat($test/@id, '.xml')
    let $stored :=
        t:store-revisions($test, $docId)
    let $doc := doc(concat("/db/test/", $docId))
    let $reconstructed :=
        v:get-revision($doc, v:list-revisions($doc)[last()])
    let $testPassed := xdiff:compare($doc, $reconstructed)
    return
        <v:test>
            {$test/@id}
            <result>{$testPassed}</result>
            {
                if (not($testPassed)) then (
                    <expected>{$doc}</expected>,
                    <found>{$reconstructed}</found>
                ) else ()
            }
        </v:test>
};

t:setup(),
<v:testSuite>
{
    for $test in /v:testSuite/v:test
    return
        t:test($test)
}
</v:testSuite>