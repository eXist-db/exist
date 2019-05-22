xquery version "3.1";

(:~
 : Tests for the RESTXQ serialization of array datatype.
 :)
module namespace arr="http://exist-db.org/test/restxq/arrays";

declare namespace test="http://exist-db.org/xquery/xqsuite";
declare namespace http="http://expath.org/ns/http-client";

declare variable $arr:COLLECTION_CONF :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <triggers>
            <trigger class="org.exist.extensions.exquery.restxq.impl.RestXqTrigger"/>
        </triggers>
    </collection>;

declare variable $arr:RESTXQ_TEST :=
    'xquery version "3.1";

     module namespace rt="http://exist-db.org/restxq/rt";

     declare namespace rest="http://exquery.org/ns/restxq";
     declare namespace output="http://www.w3.org/2010/xslt-xquery-serialization";

     declare
         %rest:GET
         %rest:path("/arrays-test")
         %output:method("json")
         %rest:produces("application/json")
     function rt:json-test() {
         map {
             "status": "ok",
             "counters": array { 1 to 10 }
         }
     };';

declare
    %test:setUp
function arr:setup() {
    let $coll := xmldb:create-collection("/db", "array-test")
    let $confColl := xmldb:create-collection("/db/system/config/db", "array-test")
    return (
        xmldb:store($confColl, "collection.xconf", $arr:COLLECTION_CONF),
        xmldb:store($coll, "test.xql", $arr:RESTXQ_TEST),
        sm:chmod(xs:anyURI($coll || "/test.xql"), "r-xr-xr-x")
    )
};

declare
    %test:tearDown
function arr:cleanup() {
    xmldb:remove("/db/array-test"),
    xmldb:remove("/db/system/config/db/array-test")
};

declare
    %test:pending("Requires a running server")
    %test:assertEquals("ok")
function arr:restxq-serialize() {
    let $req :=
        <http:request href="http://localhost:8080/exist/restxq/arrays-test" method="get">
            <http:header name="Accept" value="application/json"/>
        </http:request>
    return

        let $json := parse-json(util:binary-to-string(http:send-request($req)[2]))
        return
            $json?status
};
