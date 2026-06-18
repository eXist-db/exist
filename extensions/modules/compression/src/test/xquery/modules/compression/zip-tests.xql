(:
 : eXist-db Open Source Native XML Database
 : Copyright (C) 2001 The eXist-db Authors
 :
 : info@exist-db.org
 : http://www.exist-db.org
 :
 : This library is free software; you can redistribute it and/or
 : modify it under the terms of the GNU Lesser General Public
 : License as published by the Free Software Foundation; either
 : version 2.1 of the License, or (at your option) any later version.
 :
 : This library is distributed in the hope that it will be useful,
 : but WITHOUT ANY WARRANTY; without even the implied warranty of
 : MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 : Lesser General Public License for more details.
 :
 : You should have received a copy of the GNU Lesser General Public
 : License along with this library; if not, write to the Free Software
 : Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 :)
xquery version "3.1";

module namespace z="http://exist-db.org/testsuite/zips";

declare namespace util = "http://exist-db.org/xquery/util";
declare namespace xi = "http://www.w3.org/2001/XInclude";
declare namespace output = "http://www.w3.org/2010/xslt-xquery-serialization";
declare namespace exist = "http://exist.sourceforge.net/NS/exist";

import module namespace test="http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";
import module namespace compression="http://exist-db.org/xquery/compression";


declare variable $z:collection-name := "unzip-test";
declare variable $z:collection := "/db/" || $z:collection-name;


declare variable $z:myFile-name := "!#$%()*+,-.:;=?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[]^_abcdefghijklmnopqrstuvwxyz{}~ ÇüéâäàåçêëèïîìÄÅÉæÆôöòûùÿÖÜ¢£¥₧ƒáíóúñÑªº¿αßΓπΣσµτΦΘΩδ∞φε.xml";
declare variable $z:myFile-serialized := "<file/>";

(: declare UTF8 encoded binary :)
declare variable $z:myStaticUTF8ContentBase64 := xs:base64Binary("UEsDBBQACAgIAOBYl0UAAAAAAAAAAAAAAADCAAAAISMkJSgpKissLS46Oz0/QEFCQ0RFRkdISUpLTE1OT1BRUlNUVVZXWFlaW11eX2FiY2RlZmdoaWprbG1ub3BxcnN0dXZ3eHl6e31+IMOHw7zDqcOiw6TDoMOlw6fDqsOrw6jDr8Ouw6zDhMOFw4nDpsOGw7TDtsOyw7vDucO/w5bDnMKiwqPCpeKCp8aSw6HDrcOzw7rDscORwqrCusK/zrHDn86Tz4DOo8+DwrXPhM6mzpjOqc604oiez4bOtS54bWyzsa/IzVEoSy0qzszPs1Uy1DNQUkjNS85PycxLt1UKDXHTtVCyt+OyScvMSdW3AwBQSwcIwWbL3zAAAAAuAAAAUEsBAhQAFAAICAgA4FiXRcFmy98wAAAALgAAAMIAAAAAAAAAAAAAAAAAAAAAACEjJCUoKSorLC0uOjs9P0BBQkNERUZHSElKS0xNTk9QUVJTVFVWV1hZWltdXl9hYmNkZWZnaGlqa2xtbm9wcXJzdHV2d3h5ent9fiDDh8O8w6nDosOkw6DDpcOnw6rDq8Oow6/DrsOsw4TDhcOJw6bDhsO0w7bDssO7w7nDv8OWw5zCosKjwqXigqfGksOhw63Ds8O6w7HDkcKqwrrCv86xw5/Ok8+AzqPPg8K1z4TOps6YzqnOtOKIns+GzrUueG1sUEsFBgAAAAABAAEA8AAAACABAAAAAA==");



(: Verify zero byte sized resource :)
declare
	%test:assertEquals("")
function z:zeroByteBinResource() {
    let $collection-uri :="/db/"
    let $resource-name :="empty.txt"
    let $contents :=""

    let $empty-file := xmldb:store-as-binary($collection-uri, $resource-name, $contents)

    let $zip := compression:zip(<entry type="uri" name="{$collection-uri}">{$collection-uri||$resource-name}</entry>, true())

    return util:binary-to-string(util:binary-doc($collection-uri||$resource-name))
};


(:~
 : Serialization options supplied via the optional 5th-argument map(*) must be applied to the
 : XML resources written to the archive (per call). The exist:expand-xincludes extension parameter
 : is keyed by its exist-namespace QName (the spec-conformant form); it is the strongest observable
 : here because the change survives the round-trip through unzip's XML re-parse.
 :)
declare variable $z:exist-ns := "http://exist.sourceforge.net/NS/exist";
declare variable $z:serialize-collection := "/db/compression-serialize-test";
declare variable $z:xinclude-target-name := "xinclude-target.xml";
declare variable $z:xinclude-host-name := "xinclude-host.xml";

declare %private function z:store-xinclude-fixture() as empty-sequence() {
    let $_ := xmldb:create-collection("/db", "compression-serialize-test")
    let $_ := xmldb:store($z:serialize-collection, $z:xinclude-target-name, document { <inc>INCLUDED</inc> })
    let $_ := xmldb:store($z:serialize-collection, $z:xinclude-host-name,
        document {
            <doc xmlns:xi="http://www.w3.org/2001/XInclude"><xi:include href="{$z:xinclude-target-name}"/></doc>
        })
    return ()
};

declare %private function z:host-entry() as element(entry) {
    <entry type="uri" name="{$z:xinclude-host-name}">{$z:serialize-collection || "/" || $z:xinclude-host-name}</entry>
};

declare %private function z:unzip-entry-filter($path as xs:anyURI, $type as xs:string, $param as item()*) as xs:boolean {
    true()
};

declare %private function z:unzip-entry-data($path as xs:anyURI, $type as xs:string, $data as item()?, $param as item()*) as item()? {
    $data
};

declare %private function z:zip-host-then-extract($serialization-options as item()?) as item()? {
    let $_ := z:store-xinclude-fixture()
    let $zip := compression:zip(z:host-entry(), false(), "", "UTF8", $serialization-options)
    return compression:unzip($zip,
        util:function(xs:QName("z:unzip-entry-filter"), 3), (),
        util:function(xs:QName("z:unzip-entry-data"), 4), ())
};

(: the archived resource is inspected by structure (not re-serialized: re-serializing a preserved
 : xi:include with a relative href would re-trigger XInclude resolution against a missing base URI). :)

(: exist:expand-xincludes=true (exist-namespace QName key) expands the include in the archived resource :)
declare
    %test:assertTrue
function z:zipExpandXIncludesYes() {
    let $extracted := z:zip-host-then-extract(map { QName($z:exist-ns, "expand-xincludes"): true() })
    return exists($extracted//inc[. = "INCLUDED"]) and empty($extracted//xi:include)
};

(: exist:expand-xincludes=false preserves the include (control; also the default) :)
declare
    %test:assertTrue
function z:zipExpandXIncludesNo() {
    let $extracted := z:zip-host-then-extract(map { QName($z:exist-ns, "expand-xincludes"): false() })
    return exists($extracted//xi:include)
};

(: no map argument: unchanged from the pre-existing 2-arg behavior (the serializer's own default,
 : which expands xincludes); supplying exist:expand-xincludes=false is what preserves the include. :)
declare
    %test:assertTrue
function z:zipNoSerializationOptions() {
    let $_ := z:store-xinclude-fixture()
    let $zip := compression:zip(z:host-entry(), false())
    let $extracted := compression:unzip($zip,
        util:function(xs:QName("z:unzip-entry-filter"), 3), (),
        util:function(xs:QName("z:unzip-entry-data"), 4), ())
    return exists($extracted//inc[. = "INCLUDED"]) and empty($extracted//xi:include)
};

(: a standard W3C parameter (string key) and an exist extension parameter (QName key) coexist in one map :)
declare
    %test:assertTrue
function z:zipMixedKeyParams() {
    let $extracted := z:zip-host-then-extract(map {
        "indent": false(),
        QName($z:exist-ns, "expand-xincludes"): true()
    })
    return exists($extracted//inc[. = "INCLUDED"]) and empty($extracted//xi:include)
};

(: the options argument also accepts the W3C output:serialization-parameters element form
 : (as fn:serialize does); the exist extension parameter is an exist-namespace child element, and
 : in the element form a boolean uses value="yes"/"no". expand-xincludes=no preserves the include
 : (an observable change from the expand-by-default). :)
declare
    %test:assertTrue
function z:zipSerializationParametersElement() {
    let $extracted := z:zip-host-then-extract(
        <output:serialization-parameters>
            <output:indent value="no"/>
            <exist:expand-xincludes value="no"/>
        </output:serialization-parameters>
    )
    return exists($extracted//xi:include)
};

