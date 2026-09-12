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
 : XQSuite tests for the XQuery 4.0 fn:dateTime-record built-in.
 :
 : See https://www.w3.org/TR/xquery-functions-40/#func-dateTime-record
 :
 : The record itself is a map whose keys (year, month, day, hours,
 : minutes, seconds, timezone) are accessed with the lookup operator.
 : This module exercises the function from XQuery code so that the
 : record-type integration is covered by an XQSuite test, in addition
 : to the existing JUnit tests in RecordTypeTest.
 :)
module namespace rt = "http://exist-db.org/xquery/test/record-types";

declare namespace test = "http://exist-db.org/xquery/xqsuite";

declare
    %test:assertEquals(2026)
function rt:dateTime-record-year() {
    fn:dateTime-record(2026, 4, 27)?year
};

declare
    %test:assertEquals(4)
function rt:dateTime-record-month() {
    fn:dateTime-record(2026, 4, 27)?month
};

declare
    %test:assertEquals(27)
function rt:dateTime-record-day() {
    fn:dateTime-record(2026, 4, 27)?day
};

declare
    %test:assertEquals(2026, 4, 27)
function rt:dateTime-record-multiple-fields() {
    let $dt := fn:dateTime-record(2026, 4, 27)
    return ($dt?year, $dt?month, $dt?day)
};

declare
    %test:assertTrue
function rt:dateTime-record-fields-are-integers() {
    let $dt := fn:dateTime-record(2026, 4, 27)
    return $dt?year instance of xs:integer
        and $dt?month instance of xs:integer
        and $dt?day instance of xs:integer
};

declare
    %test:assertTrue
function rt:dateTime-record-is-map() {
    fn:dateTime-record(2026, 4, 27) instance of map(*)
};

declare
    %test:assertEquals(12)
function rt:dateTime-record-with-time() {
    fn:dateTime-record(2026, 4, 27, 12, 30, 0)?hours
};

declare
    %test:assertEquals(30)
function rt:dateTime-record-with-time-minutes() {
    fn:dateTime-record(2026, 4, 27, 12, 30, 0)?minutes
};
