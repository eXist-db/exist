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

(:~ FODT0001 (date/time overflow) and FODT0002 (duration overflow) detection
  : per XQuery 3.1 section 10.1.1, mirroring the QT3 misc-CombinedErrorCodes
  : tests FODT0001-* and FODT0002-*. :)
module namespace dt="http://exist-db.org/xquery/test/datetime-overflow";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare %test:assertError("FODT0001")
function dt:adjust-datetime-overflow() {
    fn:adjust-dateTime-to-timezone(
        xs:dateTime("25252734927766555-07-28T23:59:59-14:00"),
        xs:dayTimeDuration("PT14H"))
};

declare %test:assertError("FODT0001")
function dt:adjust-date-overflow() {
    fn:adjust-date-to-timezone(
        xs:date("25252734927766555-07-28-14:00"),
        xs:dayTimeDuration("PT14H"))
};

declare %test:assertError("FODT0001")
function dt:datetime-plus-daytime-overflow() {
    xs:dateTime("25252734927766555-07-28T23:59:59-14:00")
        + xs:dayTimeDuration("PT14H")
};

declare %test:assertError("FODT0001")
function dt:date-plus-yearmonth-overflow() {
    xs:date("25252734927766555-07-28-14:00")
        + xs:yearMonthDuration("P1Y0M")
};

declare %test:assertError("FODT0001")
function dt:date-minus-date-overflow() {
    xs:date("25252734927766555-07-28-14:00")
        - xs:date("-25252734927766555-07-28-14:00")
};

declare %test:assertError("FODT0002")
function dt:daytime-plus-overflow() {
    xs:dayTimeDuration("P5999999999999999999DT00H00M01S")
        + xs:dayTimeDuration("P4999999999999999999DT00H00M01S")
};

declare %test:assertError("FODT0002")
function dt:daytime-mult-overflow() {
    xs:dayTimeDuration("P5999999999999999999DT00H00M01S") * 2
};

declare %test:assertError("FODT0002")
function dt:daytime-div-overflow() {
    xs:dayTimeDuration("P5999999999999999999DT00H00M01S") div 0.5
};

declare %test:assertError("FODT0002")
function dt:daytime-minus-overflow() {
    xs:dayTimeDuration("P5999999999999999999DT00H00M01S")
        - xs:dayTimeDuration("-P5999999999999999999DT00H00M01S")
};

(: ValidateExpr (XQuery 3.1 section 3.18.1) is rejected with XQST0075 since
 : eXist does not implement the Schema Validation Feature. The expressions
 : are wrapped in util:eval() so XQST0075 is observed dynamically by the
 : test framework rather than at module-compile time.
 : Either XQTY0030 or XQST0075 satisfy K-CombinedErrorCodes-9..12. :)
declare %test:assertError("XQST0075")
function dt:validate-default() {
    util:eval('validate { 1 }')
};

declare %test:assertError("XQST0075")
function dt:validate-lax() {
    util:eval('validate lax { 1 }')
};

declare %test:assertError("XQST0075")
function dt:validate-strict() {
    util:eval('validate strict { 1 }')
};

(: Sanity: ordinary date arithmetic continues to work. :)
declare %test:assertEquals("2020-01-02")
function dt:date-plus-day-ok() {
    xs:string(xs:date("2020-01-01") + xs:dayTimeDuration("P1D"))
};

declare %test:assertEquals("P1DT1S")
function dt:daytime-plus-ok() {
    xs:string(xs:dayTimeDuration("PT24H") + xs:dayTimeDuration("PT1S"))
};

(: XQST0125: %public/%private annotations are forbidden on inline functions. :)
declare %test:assertError("XQST0125")
function dt:inline-public-annotation() {
    util:eval('let $f := %public function($x as xs:integer) as xs:integer { $x + 1 } return $f(1)')
};

declare %test:assertError("XQST0125")
function dt:inline-private-annotation() {
    util:eval('let $f := %private function($x as xs:integer) as xs:integer { $x + 1 } return $f(1)')
};
