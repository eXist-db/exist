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
 : XQSuite tests for `declare copy-namespaces preserve, no-inherit`.
 :
 : Per XQuery 3.1 §3.9.3.4, no-inherit governs how namespaces propagate
 : from copied source nodes (variable references) into a constructor.
 : It must NOT prevent name resolution for direct constructors, nor
 : prevent fn:in-scope-prefixes from walking the ancestor chain on
 : directly constructed nested elements.
 :
 : Regression tests for the bugs fixed alongside issue #2182:
 :   - Name resolution failure for default and prefixed names in nested
 :     direct constructors (XPTY0004 / empty paths)
 :   - in-scope-prefixes returning incomplete results for nested direct
 :     constructors (XQTS K2-CopyNamespacesProlog-4/5/9, copynamespace-2)
 :)
module namespace cnn = "http://exist-db.org/xquery/test/copy-namespaces-no-inherit";

declare namespace test = "http://exist-db.org/xquery/xqsuite";
declare namespace ns = "http://example.com/ns";

declare copy-namespaces preserve, no-inherit;

declare
    %test:assertEquals("http://example.com/")
function cnn:default-ns-still-resolves-in-direct-constructor() {
    let $e := <a xmlns="http://example.com/">{ <b/> }</a>
    return namespace-uri($e/*[1])
};

declare
    %test:assertEquals("http://example.com/ns")
function cnn:prefix-still-resolves-in-direct-constructor() {
    let $e := <ns:a xmlns:ns="http://example.com/ns"><ns:b/></ns:a>
    return namespace-uri($e/ns:b)
};

declare
    %test:assertEquals(3)
function cnn:in-scope-prefixes-include-ancestor-bindings-on-direct-construction() {
    let $e := <e3 xmlns:n3="urn:n3">
                <e2 xmlns:n2="urn:n2">
                  <e1 xmlns:n1="urn:n1"/>
                </e2>
              </e3>
    return count(in-scope-prefixes($e/e2/e1)[. = ("n1", "n2", "n3")])
};

