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

(:
 : Tests created for issue https://github.com/exist-db/exist/issues/4085
 : by Joe Wicentowski (@joewiz)
 :)
module namespace axpn="http://exist-db.org/xquery/test/axes-persistent-nodes";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $axpn:XML := document {
<root>
    <pb id="pb1"/>
    <div>
        <p>
            <w id="w1"/>
            <w id="w2"/> 
            <w id="w3"/>
        </p>
        <pb id="pb2"/>
        <p>
            <w id="w4"/> 
            <w id="w5"/>
        </p>
    </div>
    <pb id="pb3"/>
</root>
};

declare
    %test:setUp
function axpn:setup() {
    let $testCol := xmldb:create-collection("/db", "test")
    return
        xmldb:store("/db/test", "test.xml", $axpn:XML)
};

declare
    %test:tearDown
function axpn:tearDown() {
    xmldb:remove("/db/test")
};

(: PRECEDING AXIS TESTS :)

declare
    %test:assertEquals("w1:pb1", "w2:pb1", "w3:pb1", "w4:pb2", "w5:pb2")
function axpn:preceding-with-predicate-mem-flwor() {
    for $w in $axpn:XML//w[true()]
    let $preceding-page := $w/preceding::pb[1]
    return
        if ($preceding-page) then
            $w/@id || ":" || $preceding-page/@id
        else
            $w/@id || ":PRECEDING_PB_NOT_FOUND"
};

declare
    %test:assertEquals("w1:pb1", "w2:pb1", "w3:pb1", "w4:pb2", "w5:pb2")
function axpn:preceding-with-predicate-mem-map() {
    $axpn:XML//w[true()]
        ! (./@id || ":" || (./preceding::pb[1]/@id, "PRECEDING_PB_NOT_FOUND")[1])
};

declare
    %test:assertEquals("w1:pb1", "w2:pb1", "w3:pb1", "w4:pb2", "w5:pb2")
function axpn:preceding-with-predicate-db-flwor() {
    for $w in doc("/db/test/test.xml")//w[true()]
    let $preceding-page := $w/preceding::pb[1]
    return
        if ($preceding-page) then
            $w/@id || ":" || $preceding-page/@id
        else
            $w/@id || ":PRECEDING_PB_NOT_FOUND"
};

declare
    %test:assertEquals("w1:pb1", "w2:pb1", "w3:pb1", "w4:pb2", "w5:pb2")
function axpn:preceding-with-predicate-db-map() {
    doc("/db/test/test.xml")//w[true()]
        ! (./@id || ":" || (./preceding::pb[1]/@id, "PRECEDING_PB_NOT_FOUND")[1])
};

declare
    %test:assertEquals("w1:pb1", "w2:pb1", "w3:pb1", "w4:pb2", "w5:pb2")
function axpn:preceding-without-predicate-flwor() {
    for $w in doc("/db/test/test.xml")//w
    let $preceding-page := $w/preceding::pb[1]
    return
        if ($preceding-page) then
            $w/@id || ":" || $preceding-page/@id
        else
            $w/@id || ":PRECEDING_PB_NOT_FOUND"
};

declare
    %test:assertEquals("w1:pb1", "w2:pb1", "w3:pb1", "w4:pb2", "w5:pb2")
function axpn:preceding-without-predicate-map() {
    doc("/db/test/test.xml")//w
        ! (./@id || ":" || (./preceding::pb[1]/@id, "PRECEDING_PB_NOT_FOUND")[1])
};

(: FOLLOWING AXIS TESTS :)

declare
    %test:assertEquals("w1:pb2", "w2:pb2", "w3:pb2", "w4:pb3", "w5:pb3")
function axpn:following-with-predicate-mem-flwor() {
    for $w in $axpn:XML//w[true()]
    let $following-page := $w/following::pb[1]
    return
        if ($following-page) then
            $w/@id || ":" || $following-page/@id
        else
            $w/@id || ":FOLLOWING_PB_NOT_FOUND"
};

declare
    %test:assertEquals("w1:pb2", "w2:pb2", "w3:pb2", "w4:pb3", "w5:pb3")
function axpn:following-with-predicate-mem-map() {
    $axpn:XML//w[true()]
        ! (./@id || ":" || (./following::pb[1]/@id, "FOLLOWING_PB_NOT_FOUND")[1])
};

declare
    %test:assertEquals("w1:pb2", "w2:pb2", "w3:pb2", "w4:pb3", "w5:pb3")
function axpn:following-with-predicate-db-flwor() {
    for $w in doc("/db/test/test.xml")//w[true()]
    let $following-page := $w/following::pb[1]
    return
        if ($following-page) then
            $w/@id || ":" || $following-page/@id
        else
            $w/@id || ":FOLLOWING_PB_NOT_FOUND"
};

declare
    %test:assertEquals("w1:pb2", "w2:pb2", "w3:pb2", "w4:pb3", "w5:pb3")
function axpn:following-with-predicate-db-map() {
    doc("/db/test/test.xml")//w[true()]
        ! (./@id || ":" || (./following::pb[1]/@id, "FOLLOWING_PB_NOT_FOUND")[1])
};

declare
    %test:assertEquals("w1:pb2", "w2:pb2", "w3:pb2", "w4:pb3", "w5:pb3")
function axpn:following-without-predicate-flwor() {
    for $w in doc("/db/test/test.xml")//w
    let $following-page := $w/following::pb[1]
    return
        if ($following-page) then
            $w/@id || ":" || $following-page/@id
        else
            $w/@id || ":FOLLOWING_PB_NOT_FOUND"
};

declare
    %test:assertEquals("w1:pb2", "w2:pb2", "w3:pb2", "w4:pb3", "w5:pb3")
function axpn:following-without-predicate-map() {
    doc("/db/test/test.xml")//w 
        ! (./@id || ":" || (./following::pb[1]/@id, "FOLLOWING_PB_NOT_FOUND")[1])
};
