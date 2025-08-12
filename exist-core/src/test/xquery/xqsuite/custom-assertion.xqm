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
    %test:assertEquals("Custom message", "expected", "actual", "custom-assertion-failure")
function ca:test-fail-3() as item()* {
    try {
        test:fail("Custom message", "expected", "actual")
    }
    catch test:failure {
        $err:description, $err:value?expected, $err:value?actual, $err:value?type
    }
};

declare
    %test:assertEquals("Custom message", "expected", "actual", "custom-type")
function ca:test-fail-4() as item()* {
    try {
        test:fail("Custom message", "expected", "actual", "custom-type")
    }
    catch test:failure {
        $err:description, $err:value?expected, $err:value?actual, $err:value?type
    }
};

declare
    %test:assertEquals("Custom message", "expected", "actual", "custom-assertion-failure")
function ca:test-fail-fallback() as item()* {
    try {
        error($test:FAILURE, "Custom message", map {
            "expected": "expected",
            "actual": "actual",
            "type": $test:CUSTOM_ASSERTION_FAILURE_TYPE
        })
    }
    catch test:failure {
        $err:description, $err:value?expected, $err:value?actual, $err:value?type
    }
};

declare
    %test:assertTrue
function ca:map-assertion-pass() as item()* {
    ca:map-assertion($ca:var, map {"b": 2, "a": 1})
};

declare
    %test:assertEquals("Key 'b' is missing", "{""a"":1}", "map-assertion-failure")
function ca:map-assertion-missing-key() as item()* {
    try {
        ca:map-assertion($ca:var, map {"a": 1})
    }
    catch test:failure {
        $err:description,
        fn:serialize($err:value?actual, map{"method":"json"}),
        $err:value?type
    }
};

declare
    %test:assertEquals("Value mismatch for key 'b'", "{""b"":3,""a"":1}", "map-assertion-failure")
function ca:map-assertion-wrong-value() as item()* {
    try {
        ca:map-assertion($ca:var, map {"a": 1, "b": 3})
    }
    catch test:failure {
        $err:description,
        fn:serialize($err:value?actual, map{"method":"json"}),
        $err:value?type
    }
};

declare
    %test:assertEquals("Additional keys found: (o, 23)", "{""a"":1,""o"":""o"",""23"":3}", "map-assertion-failure")
function ca:map-assertion-additional-key() as item()* {
    try {
        ca:map-assertion($ca:var, map {"a": 1, 23: 3, "o": "o"})
    }
    catch test:failure {
        $err:description,
        fn:serialize($err:value?actual, map{"method":"json"}),
        $err:value?type
    }
};

declare
    %test:assertEquals("Type mismatch", "[1,2]", "type-mismatch")
function ca:map-assertion-type-mismatch() as item()* {
    try {
        ca:map-assertion($ca:var, [1,2])
    }
    catch test:failure {
        $err:description,
        fn:serialize($err:value?actual, map{"method":"json"}),
        $err:value?type
    }
};

(:
 : custom assertion, which could also be imported from a library module
 :)

declare %private variable $ca:MAP_ASSERTION_TYPE := "map-assertion-failure";

declare %private
function ca:map-assertion ($expected as map(*), $actual as item()*) as item()* {
    if (not(exists($actual)))
    then test:fail("Actual is empty", $expected, $actual, "type-mismatch")
    else if (count($actual) gt 1)
    then test:fail("Actual is a sequence with more than one item", $expected, $actual, "type-mismatch")
    else if (not($actual instance of map(*)))
    then test:fail("Type mismatch", $expected, $actual, "type-mismatch")
    else if (not(empty(
        map:keys(map:remove($actual, map:keys($expected))))))
    then test:fail(
             "Additional keys found: (" || string-join(
                map:keys(map:remove($actual, map:keys($expected))), ', ') || ")",
             $expected,
             $actual,
             $ca:MAP_ASSERTION_TYPE
        )
    else (
        for-each(map:keys($expected), ca:map-assert-key(?, $expected, $actual)),
        true()
    )
};

declare %private
function ca:map-assert-key ($key as xs:anyAtomicType, $expected as map(*), $actual as map(*)) as item()* {
    if (not(map:contains($actual, $key)))
    then test:fail("Key '" || $key || "' is missing", $expected, $actual, $ca:MAP_ASSERTION_TYPE)
    else if ($expected($key) ne $actual($key))
    then test:fail("Value mismatch for key '" || $key || "'", $expected, $actual, $ca:MAP_ASSERTION_TYPE)
    else ()
};
