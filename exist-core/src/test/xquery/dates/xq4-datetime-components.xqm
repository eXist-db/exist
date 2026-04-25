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
xquery version "4.0";

(:~
 : XQuery 4.0 date/time component extraction tests (QT4CG PR #1481).
 : In XQ4, fn:*-from-dateTime functions accept any Gregorian date/time type.
 :)
module namespace dtc="http://exist-db.org/xquery/test/xq4-datetime-components";

declare namespace test="http://exist-db.org/xquery/xqsuite";

(: ===== year-from-dateTime ===== :)

declare
    %test:assertEquals(2025)
function dtc:year-from-dateTime-date() {
    fn:year-from-dateTime(xs:date("2025-03-15"))
};

declare
    %test:assertEquals(2025)
function dtc:year-from-dateTime-gYear() {
    fn:year-from-dateTime(xs:gYear("2025"))
};

declare
    %test:assertEquals(2025)
function dtc:year-from-dateTime-gYearMonth() {
    fn:year-from-dateTime(xs:gYearMonth("2025-06"))
};

declare
    %test:assertEmpty
function dtc:year-from-dateTime-time() {
    fn:year-from-dateTime(xs:time("13:20:00"))
};

declare
    %test:assertEmpty
function dtc:year-from-dateTime-gMonth() {
    fn:year-from-dateTime(xs:gMonth("--06"))
};

declare
    %test:assertEmpty
function dtc:year-from-dateTime-gDay() {
    fn:year-from-dateTime(xs:gDay("---15"))
};

declare
    %test:assertEmpty
function dtc:year-from-dateTime-gMonthDay() {
    fn:year-from-dateTime(xs:gMonthDay("--06-15"))
};

(: ===== month-from-dateTime ===== :)

declare
    %test:assertEquals(3)
function dtc:month-from-dateTime-date() {
    fn:month-from-dateTime(xs:date("2025-03-15"))
};

declare
    %test:assertEquals(6)
function dtc:month-from-dateTime-gYearMonth() {
    fn:month-from-dateTime(xs:gYearMonth("2025-06"))
};

declare
    %test:assertEquals(6)
function dtc:month-from-dateTime-gMonth() {
    fn:month-from-dateTime(xs:gMonth("--06"))
};

declare
    %test:assertEquals(6)
function dtc:month-from-dateTime-gMonthDay() {
    fn:month-from-dateTime(xs:gMonthDay("--06-15"))
};

declare
    %test:assertEmpty
function dtc:month-from-dateTime-time() {
    fn:month-from-dateTime(xs:time("13:20:00"))
};

declare
    %test:assertEmpty
function dtc:month-from-dateTime-gYear() {
    fn:month-from-dateTime(xs:gYear("2025"))
};

declare
    %test:assertEmpty
function dtc:month-from-dateTime-gDay() {
    fn:month-from-dateTime(xs:gDay("---15"))
};

(: ===== day-from-dateTime ===== :)

declare
    %test:assertEquals(15)
function dtc:day-from-dateTime-date() {
    fn:day-from-dateTime(xs:date("2025-03-15"))
};

declare
    %test:assertEquals(15)
function dtc:day-from-dateTime-gDay() {
    fn:day-from-dateTime(xs:gDay("---15"))
};

declare
    %test:assertEquals(15)
function dtc:day-from-dateTime-gMonthDay() {
    fn:day-from-dateTime(xs:gMonthDay("--06-15"))
};

declare
    %test:assertEmpty
function dtc:day-from-dateTime-time() {
    fn:day-from-dateTime(xs:time("13:20:00"))
};

declare
    %test:assertEmpty
function dtc:day-from-dateTime-gYear() {
    fn:day-from-dateTime(xs:gYear("2025"))
};

(: ===== hours-from-dateTime ===== :)

declare
    %test:assertEquals(13)
function dtc:hours-from-dateTime-time() {
    fn:hours-from-dateTime(xs:time("13:20:30"))
};

declare
    %test:assertEmpty
function dtc:hours-from-dateTime-date() {
    fn:hours-from-dateTime(xs:date("2025-03-15"))
};

declare
    %test:assertEmpty
function dtc:hours-from-dateTime-gYear() {
    fn:hours-from-dateTime(xs:gYear("2025"))
};

(: ===== minutes-from-dateTime ===== :)

declare
    %test:assertEquals(20)
function dtc:minutes-from-dateTime-time() {
    fn:minutes-from-dateTime(xs:time("13:20:30"))
};

declare
    %test:assertEmpty
function dtc:minutes-from-dateTime-date() {
    fn:minutes-from-dateTime(xs:date("2025-03-15"))
};

(: ===== seconds-from-dateTime ===== :)

declare
    %test:assertEquals(30)
function dtc:seconds-from-dateTime-time() {
    fn:seconds-from-dateTime(xs:time("13:20:30"))
};

declare
    %test:assertEquals(30.5)
function dtc:seconds-from-dateTime-time-fractional() {
    fn:seconds-from-dateTime(xs:time("13:20:30.5"))
};

declare
    %test:assertEmpty
function dtc:seconds-from-dateTime-date() {
    fn:seconds-from-dateTime(xs:date("2025-03-15"))
};

(: ===== timezone-from-dateTime ===== :)

declare
    %test:assertEquals("PT5H")
function dtc:timezone-from-dateTime-date() {
    fn:timezone-from-dateTime(xs:date("2025-03-15+05:00"))
};

declare
    %test:assertEquals("-PT5H")
function dtc:timezone-from-dateTime-time() {
    fn:timezone-from-dateTime(xs:time("13:20:00-05:00"))
};

declare
    %test:assertEmpty
function dtc:timezone-from-dateTime-no-tz() {
    fn:timezone-from-dateTime(xs:gYear("2025"))
};

(: ===== Existing XQ 3.1 behavior: xs:dateTime arg still works ===== :)

declare
    %test:assertEquals(1999)
function dtc:year-from-dateTime-dateTime() {
    fn:year-from-dateTime(xs:dateTime("1999-05-31T13:20:00-05:00"))
};

declare
    %test:assertEquals(5)
function dtc:month-from-dateTime-dateTime() {
    fn:month-from-dateTime(xs:dateTime("1999-05-31T13:20:00-05:00"))
};

declare
    %test:assertEquals(31)
function dtc:day-from-dateTime-dateTime() {
    fn:day-from-dateTime(xs:dateTime("1999-05-31T13:20:00-05:00"))
};

declare
    %test:assertEquals(13)
function dtc:hours-from-dateTime-dateTime() {
    fn:hours-from-dateTime(xs:dateTime("1999-05-31T13:20:00-05:00"))
};

declare
    %test:assertEquals(20)
function dtc:minutes-from-dateTime-dateTime() {
    fn:minutes-from-dateTime(xs:dateTime("1999-05-31T13:20:00-05:00"))
};

declare
    %test:assertEquals(0)
function dtc:seconds-from-dateTime-dateTime() {
    fn:seconds-from-dateTime(xs:dateTime("1999-05-31T13:20:00-05:00"))
};

declare
    %test:assertEquals("-PT5H")
function dtc:timezone-from-dateTime-dateTime() {
    fn:timezone-from-dateTime(xs:dateTime("1999-05-31T13:20:00-05:00"))
};

(: ===== Empty sequence input ===== :)

declare
    %test:assertEmpty
function dtc:year-from-dateTime-empty() {
    fn:year-from-dateTime(())
};
