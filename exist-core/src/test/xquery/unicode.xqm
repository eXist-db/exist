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

module namespace uni="http://exist-db.org/xquery/test/unicode";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $uni:SIP :=
    <container>
        <smp>&#x2a3c6;</smp>
    </container>
;

declare variable $uni:COLLECTION_NAME := "test-unicode";

declare
    %test:setUp
function uni:setup() {
    xmldb:create-collection("/db", $uni:COLLECTION_NAME)
};

declare
    %test:tearDown
function uni:cleanup() {
    xmldb:remove($uni:COLLECTION_NAME)
};

declare function uni:transform($element as element(container)) as element() {
    element { node-name($element) } {
        $element/@*,
        for $child in $element/node()
        return
            if ($child instance of element(smp))
            then $child/string()
            else $child
    }
};

declare
    %test:assertEquals(172998)
function uni:sip-dom-memtree() {
    string-to-codepoints($uni:SIP/smp)
};

declare
    %test:assertEquals(172998)
function uni:sip-dom-persistent() {
    let $stored := xmldb:store("/db/" || $uni:COLLECTION_NAME, "sip.xml", $uni:SIP)
    return
        fn:string-to-codepoints(fn:doc($stored)/container/smp)
};

declare
    %test:assertEquals(172998)
function uni:sip-dom-memtree-transform() {
    let $transformed := uni:transform($uni:SIP)
    return
        fn:string-to-codepoints($transformed)
};

declare
    %test:assertEquals(172998)
function uni:sip-dom-persistent-transform() {
    let $transformed := uni:transform($uni:SIP)
    let $stored := xmldb:store("/db/" || $uni:COLLECTION_NAME, "sip-transformed.xml", $transformed)
    return
        fn:string-to-codepoints(fn:doc($stored)/container)
};

declare
    %test:assertEquals(172998)
function uni:sip-dom-persistent-transform-2() {
    let $stored := xmldb:store("/db/" || $uni:COLLECTION_NAME, "sip.xml", $uni:SIP)
    let $transformed := uni:transform(fn:doc($stored)/container)
    let $stored := xmldb:store("/db/" || $uni:COLLECTION_NAME, "sip-transformed.xml", $transformed)
    return
        fn:string-to-codepoints(fn:doc($stored)/container)
};
