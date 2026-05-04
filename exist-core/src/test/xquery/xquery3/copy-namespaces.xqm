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
 : XQSuite tests for `declare copy-namespaces preserve, inherit` (the
 : default mode). Covers element-constructor name resolution and the
 : behaviour exercised by the issue #2182 reproducer.
 :
 : See XQuery 3.1 §3.9.3.4 for spec semantics.
 : See also copy-namespaces-no-inherit.xqm for the no-inherit mode.
 :)
module namespace cn = "http://exist-db.org/xquery/test/copy-namespaces";

declare namespace test = "http://exist-db.org/xquery/xqsuite";
declare namespace ns = "http://example.com/ns";

declare copy-namespaces preserve, inherit;

declare
    %test:assertEquals("http://example.com/")
function cn:default-ns-inherited-by-child-constructor() {
    let $e := <a xmlns="http://example.com/">{ <b/> }</a>
    return namespace-uri($e/*[1])
};

declare
    %test:assertEquals("http://example.com/ns")
function cn:prefix-visible-to-child-constructor() {
    let $e := <ns:a xmlns:ns="http://example.com/ns"><ns:b/></ns:a>
    return namespace-uri($e/ns:b)
};

declare
    %test:assertEquals(3)
function cn:in-scope-prefixes-include-ancestor-bindings() {
    let $e := <e3 xmlns:n3="urn:n3">
                <e2 xmlns:n2="urn:n2">
                  <e1 xmlns:n1="urn:n1"/>
                </e2>
              </e3>
    return count(in-scope-prefixes($e/e2/e1)[. = ("n1", "n2", "n3")])
};

(:~
 : Issue #2182 reproducer: an xsi:type value references a prefix
 : declared only on an ancestor. With copy-namespaces inherit, selecting
 : the inner element keeps the ancestor binding so the qualified type
 : name remains resolvable.
 :)
declare
    %test:assertTrue
function cn:issue-2182-xsi-type-prefix-preserved() {
    let $xml :=
        <MCCI_IN200101 xmlns="urn:hl7-org:v3"
                       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                       xmlns:hl7nl="urn:hl7-nl:v3">
            <effectiveTime xsi:type="hl7nl:PIVL_TS"/>
        </MCCI_IN200101>
    let $fragment := $xml/*[1]
    return "hl7nl" = in-scope-prefixes($fragment)
};
