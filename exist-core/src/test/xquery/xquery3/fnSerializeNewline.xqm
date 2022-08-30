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

module namespace ser="http://exist-db.org/xquery/test/serialize";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare
    %test:assertTrue
function ser:exist-insert-final-newline-false() {
    let $doc := <root>
    <nested />
    </root>
    let $serialized := fn:serialize($doc,
        map {xs:QName("exist:insert-final-newline"): false()})
    return fn:ends-with($serialized, ">")
};

declare
    %test:assertTrue
function ser:exist-insert-final-newline-true() {
    let $doc := <root>
    <nested />
    </root>
    let $serialized := fn:serialize($doc,
        map {xs:QName("exist:insert-final-newline"): true()})
    return fn:ends-with($serialized, "&#x0A;")
};

declare
    %test:assertTrue
function ser:exist-insert-final-newline-false-json() {
    let $doc := map { "a": 1 }
    let $serialized := fn:serialize($doc,
        map {
            "method": "json",
            "exist:insert-final-newline": false()
        }
    )
    return fn:ends-with($serialized, "}")
};

declare
    %test:assertTrue
function ser:exist-insert-final-newline-true-json() {
    let $doc := map { "a": 1 }
    let $serialized := fn:serialize($doc,
        map {
            "method": "json",
            "exist:insert-final-newline": true()
        }
    )
    return fn:ends-with($serialized, "&#x0A;")
};
