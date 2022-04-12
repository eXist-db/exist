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
 : Some tests on features of the test suite itself.
 :)
module namespace ca="http://exist-db.org/xquery/test/xqsuite/custom-assertion";

import module namespace test="http://exist-db.org/xquery/xqsuite"
    at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";

declare variable $ca:var := map {"a": 1, "b": 2};

declare
    %test:assertEquals("Key 'b' is missing", "map-assertion-failure")
function ca:missing-key-default-type() as item()* {
    try {
        ca:map-assertion($ca:var, map {"a": 1, "c": 3})
    }
    catch test:failure {
        $err:description, $err:value?type
    }
};

declare
    %test:assertEquals("Value mismatch for key 'b'", "custom-assertion-failure")
function ca:wrong-value-custom-type() as item()* {
    try {
        ca:map-assertion($ca:var, map {"a": 1, "b": 3})
    }
    catch test:failure {
        $err:description, $err:value?type
    }
};

declare
    %test:assertEquals("Type mismatch", "type-mismatch")
function ca:type-mismatch-custom-type() as item()* {
    try {
        ca:map-assertion($ca:var, [1,2])
    }
    catch test:failure {
        $err:description, $err:value?type
    }
};

declare %private
function ca:map-assertion ($expected as map(*), $actual as item()*) as item()* {
    if (exists($actual) and count($actual) eq 1  and $actual instance of map(*))
    then (
        for-each(map:keys($expected), function ($key as xs:anyAtomicType) {
            if (not(map:contains($actual, $key)))
            then test:fail($expected, $actual, "Key '" || $key || "' is missing", "map-assertion-failure")
            else if ($expected($key) ne $actual($key))
            then test:fail($expected, $actual, "Value mismatch for key '" || $key || "'")
            else ()
        })
        ,
        true()
    )
    else test:fail($expected, $actual, "Type mismatch", "type-mismatch")
};
