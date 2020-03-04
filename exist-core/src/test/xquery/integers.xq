xquery version "3.1";

module namespace it = "http://exist-db.org/xquery/test/integer-tests";
 
declare namespace test = "http://exist-db.org/xquery/xqsuite";

(:~
 : Tests for integers.
 :
 : Note, in some tests the purpose of the nested HoF is to circumvent
 : the Automatic Type Conversion provided by XQSuite,
 : see: http://www.exist-db.org/exist/apps/doc/xqsuite#type-conversion
 :)

declare
    %test:args("-1")
    %test:assertError("err:XPTY0004")
    %test:args("0")
    %test:assertError("err:XPTY0004")
    %test:args("1")
    %test:assertError("err:XPTY0004")
function it:function-signature-negative-integer-from-string($test-value as xs:string) {
    function($negative-integer as xs:negativeInteger) {
        $negative-integer
    }($test-value)
};

declare
    %test:args(0)
    %test:assertError("err:FORG0001")
    %test:args(1)
    %test:assertError("err:FORG0001")
function it:function-signature-negative-integer-from-integer($test-value as xs:integer) {
    function($negative-integer as xs:negativeInteger) {
        $negative-integer
    }($test-value)
};

declare 
    %test:args("-1")
    %test:assertError("err:XPTY0004")
    %test:args("0")
    %test:assertError("err:XPTY0004")
    %test:args("1")
    %test:assertError("err:XPTY0004")
function it:function-signature-positive-integer-from-string($test-value as xs:string) {
    function($positive-integer as xs:positiveInteger) {
        $positive-integer
    }($test-value)
};

declare
    %test:args(0)
    %test:assertError("err:FORG0001")
    %test:args(1)
    %test:assertError("err:FORG0001")
function it:function-signature-positive-integer-from-integer($test-value as xs:integer) {
    function($positive-integer as xs:positiveInteger) {
        $positive-integer
    }($test-value)
};

declare
    %test:args("-1")
    %test:assertEquals("-1")
    %test:args("0")
    %test:assertError("err:FORG0001")
    %test:args("1")
    %test:assertError("err:FORG0001")
function it:string-cast-as-negative-integer($string as xs:string) as xs:negativeInteger {
    $string cast as xs:negativeInteger
};

declare 
    %test:args("-1")
    %test:assertEquals("-1")
    %test:args(0)
    %test:assertError("err:FORG0001")
    %test:args(1)
    %test:assertError("err:FORG0001")
function it:integer-cast-as-negative-integer($integer as xs:integer) as xs:negativeInteger {
    $integer cast as xs:negativeInteger
};

declare
    %test:args("-1")
    %test:assertError("err:FORG0001")
    %test:args("0")
    %test:assertError("err:FORG0001")
    %test:args("1")
    %test:assertEquals(1)
function it:string-cast-as-positive-integer($string as xs:integer) as xs:positiveInteger {
    $string cast as xs:positiveInteger
};

declare 
    %test:args("-1")
    %test:assertError("err:FORG0001")
    %test:args(0)
    %test:assertError("err:FORG0001")
    %test:args(1)
    %test:assertEquals(1)
function it:integer-cast-as-positive-integer($integer as xs:integer) as xs:positiveInteger {
    $integer cast as xs:positiveInteger
};

declare
    %test:args("-1")
    %test:assertTrue
    %test:args("0")
    %test:assertFalse
    %test:args("1")
    %test:assertFalse
function it:string-castable-as-negative-integer($string as xs:string) as xs:boolean {
    $string castable as xs:negativeInteger
};

declare 
    %test:args("-1")
    %test:assertTrue
    %test:args(0)
    %test:assertFalse
    %test:args(1)
    %test:assertFalse
function it:integer-castable-as-negative-integer($integer as xs:integer) as xs:boolean {
    $integer castable as xs:negativeInteger
};

declare
    %test:args("-1")
    %test:assertFalse
    %test:args("0")
    %test:assertFalse
    %test:args("1")
    %test:assertTrue
function it:string-castable-as-positive-integer($string as xs:integer) as xs:boolean {
    $string castable as xs:positiveInteger
};

declare
    %test:args("-1")
    %test:assertFalse
    %test:args(0)
    %test:assertFalse
    %test:args(1)
    %test:assertTrue
function it:integer-castable-as-positive-integer($integer as xs:integer) as xs:boolean {
    $integer castable as xs:positiveInteger
};

declare
    %test:args("-1")
    %test:assertFalse
    %test:args("0")
    %test:assertFalse
    %test:args("1")
    %test:assertFalse
function it:string-instance-of-negative-integer($string as xs:string) as xs:boolean {
    $string instance of xs:negativeInteger
};

declare 
    %test:args("-1")
    %test:assertFalse
    %test:args(0)
    %test:assertFalse
    %test:args(1)
    %test:assertFalse
function it:integer-instance-of-negative-integer($integer as xs:integer) as xs:boolean {
    $integer instance of xs:negativeInteger
};

declare
    %test:args("-1")
    %test:assertFalse
    %test:args("0")
    %test:assertFalse
    %test:args("1")
    %test:assertFalse
function it:string-instance-of-positive-integer($string as xs:string) as xs:boolean  {
    $string instance of xs:positiveInteger
};

declare 
    %test:args("-1")
    %test:assertFalse
    %test:args(0)
    %test:assertFalse
    %test:args(1)
    %test:assertFalse
function it:integer-instance-of-positive-integer($integer as xs:integer) as xs:boolean  {
    $integer instance of xs:positiveInteger
};
