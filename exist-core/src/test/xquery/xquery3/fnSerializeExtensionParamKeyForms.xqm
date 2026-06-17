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

(:~
 : Accepted key forms for eXist's implementation-defined serialization parameters in the
 : fn:serialize options map. The map form is parsed by SerializerUtils.getSerializationOptions,
 : shared by fn:serialize, file:sync and file:serialize, so the behavior asserted here is uniform
 : across all of them.
 :
 : The W3C XSLT/XQuery Serialization 3.1 spec requires implementation-defined serialization
 : parameters supplied via the options map to be keyed by an xs:QName in the implementation's
 : namespace. That QName form is the primary, privileged form and is what every other test in the
 : code base uses.
 :
 : Only the xs:QName form is accepted. eXist historically also matched the same parameter keyed by a
 : bare xs:string of the QName's "exist:..." lexical form, because a map key-comparison bug coerced a
 : QName lookup to match a string key by value; that non-spec-conformant behavior was removed when the
 : map same-key bug was fixed (eXist-db/exist#6491). This file is the canonical place documenting the
 : resulting key-form contract: the exist-namespace QName key is honored; both the prefixed
 : "exist:..." string key and the bare local name are ignored.
 :
 : Vehicle: expand-xincludes. Its default is true(), so an ACCEPTED key set to false() preserves the
 : <xi:include>, whereas an IGNORED key falls back to the default and expands it -- which is how
 : acceptance is detected.
 :)
module namespace skf = "http://exist-db.org/xquery/test/serialize-extension-param-key-forms";

declare namespace test = "http://exist-db.org/xquery/xqsuite";

declare variable $skf:exist-ns := "http://exist.sourceforge.net/NS/exist";
declare variable $skf:collection := "/db/serialize-keyform-test";

declare
    %test:setUp
function skf:setup() {
    let $col := xmldb:create-collection("/db", "serialize-keyform-test")
    return (
        xmldb:store($skf:collection, "target.xml", <inc>INCLUDED</inc>),
        xmldb:store($skf:collection, "host.xml",
            <doc xmlns:xi="http://www.w3.org/2001/XInclude"><xi:include href="target.xml"/></doc>)
    )
};

declare
    %test:tearDown
function skf:tear-down() {
    if (xmldb:collection-available($skf:collection)) then xmldb:remove($skf:collection) else ()
};

declare %private function skf:host() as node() {
    doc($skf:collection || "/host.xml")/element()
};

(: PRIMARY, spec-conformant form: the exist-namespace QName key is honored (false() preserves the include). :)
declare
    %test:assertTrue
function skf:qname-key-honored() {
    contains(serialize(skf:host(), map { QName($skf:exist-ns, "expand-xincludes"): false() }), "xi:include")
};

(:
 : The prefixed "exist:..." string key is NOT accepted. eXist tolerated it before the map same-key
 : fix (eXist-db/exist#6491); now it is ignored, so expand-xincludes falls back to its default (true)
 : and the include is expanded -- same as the bare-local case below. The W3C Serialization 3.1 spec
 : sanctions only the xs:QName form (above) for implementation-defined parameters.
 :)
declare
    %test:assertTrue
function skf:prefixed-string-key-rejected() {
    let $s := serialize(skf:host(), map { "exist:expand-xincludes": false() })
    return contains($s, "INCLUDED") and not(contains($s, "xi:include"))
};

(: The bare local name (no "exist:" prefix) is NOT accepted; the parameter falls back to its default
 : (true), so the include is expanded. :)
declare
    %test:assertTrue
function skf:bare-local-key-ignored() {
    let $s := serialize(skf:host(), map { "expand-xincludes": false() })
    return contains($s, "INCLUDED") and not(contains($s, "xi:include"))
};
