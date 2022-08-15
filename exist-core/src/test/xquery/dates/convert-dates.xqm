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
xquery version "3.0";

module namespace fd="http://exist-db.org/xquery/test/convert-dates";

declare namespace test="http://exist-db.org/xquery/xqsuite";

(: verify that we can convert xs:string to :xs:dateTimeStamp  :)
declare
    %test:args("2012-06-26T23:14:22.566+02:00")
    %test:assertEquals("11:14 pm on Tuesday, June 26th, 2012")
function fd:format-dateTimeStamp($date as xs:dateTimeStamp) {
    format-dateTime($date, "[h00]:[m00] [P] on [FNn], [MNn] [D1o], [Y]", "en", (), ())
};

(: verify that we can't convert xs:string without timezone to :xs:dateTimeStamp  :)
declare
    %test:args("2012-06-26T23:14:22.566")
    %test:assertError
function fd:convert-date-time-stamp($date as xs:dateTimeStamp) {
    format-dateTime($date, "[h00]:[m00] [P] on [FNn], [MNn] [D1o], [Y]", "en", (), ())
};

declare
    %test:args("2022-05-17T16:24:06.003+02:00", "PT2H")
    %test:assertEquals("2022-05-17T18:24:06.003+02:00")
function fd:add-test($date as xs:dateTimeStamp, $duration as xs:dayTimeDuration) {
    $duration + $date
};

declare
    %test:args("2022-08-10T12:36:51.723+02:00", "2022-07-10T12:36:51.723+02:00")
    %test:assertEquals("P31D")
function fd:subtract-test($date1 as xs:dateTimeStamp, $date2 as xs:dateTimeStamp) {
    ($date1 - $date2) cast as xs:string
};

declare
    %test:args("2022-08-10T12:36:51.723+02:00", "2022-07-10T12:36:51.723+02:00")
    %test:assertEquals("P31D")
function fd:subtract-from-dateTime-test($date1 as xs:dateTime, $date2 as xs:dateTimeStamp) {
    ($date1 - $date2) cast as xs:string
};

(: verify that fn:current-dateTime() return type is xs:dateTimeStamp  :)
declare
    %test:assertEquals("true")
function fd:current-dateTime-type() {
    fn:current-dateTime() instance of xs:dateTimeStamp
};

declare
    %test:args("2022-05-17T16:24:06.003+02:00", "PT2H")
    %test:assertEquals("true")
function fd:return-type-test($date as xs:dateTimeStamp, $duration as xs:dayTimeDuration) {
    ($duration + $date) instance of xs:dateTimeStamp
};

declare
    %test:args("not-a-dateTimeStamp")
    %test:assertError
function fd:not-a-dateTimeStamp($date as xs:dateTimeStamp) {
    $date instance of xs:dateTimeStamp
};

declare
    %test:args("2022-05-17T17:16:00.000")
    %test:assertError
function fd:not-a-dateTimeStamp2($date as xs:dateTimeStamp) {
    $date instance of xs:dateTimeStamp
};

declare
    %test:args("2022-05-17T17:16:00.000Z")
    %test:assertEquals("true")
function fd:create-dateTimeStamp($date as xs:dateTimeStamp) {
    $date instance of xs:dateTimeStamp
};

declare
    %test:args("2022-05-17T17:16:00.000+01:00")
    %test:assertEquals("true")
function fd:create-dateTimeStamp2($date as xs:dateTimeStamp) {
    $date instance of xs:dateTimeStamp
};