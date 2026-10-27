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

(:~ Comparing xs:gMonth and xs:gDay values: a missing timezone is the implicit timezone,
  : per XPath F&O 3.1 section 10.4. The tests build their timezone-bearing values from
  : implicit-timezone(), so they hold in any timezone; they fail only where the implicit
  : timezone is not UTC, as in exist-core's surefire runs (Europe/Berlin). :)
module namespace git="http://exist-db.org/xquery/test/gregorian-implicit-timezone";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $git:date := adjust-date-to-timezone(xs:date("2015-10-10"), implicit-timezone());

declare
    %test:assertTrue
function git:gmonth-eq-without-timezone() {
    $git:date cast as xs:gMonth eq xs:gMonth("--10")
};

declare
    %test:assertTrue
function git:gday-eq-without-timezone() {
    $git:date cast as xs:gDay eq xs:gDay("---10")
};

declare
    %test:assertEquals(1, 1)
function git:distinct-values-without-timezone() {
    count(distinct-values(($git:date cast as xs:gMonth, xs:gMonth("--10")))),
    count(distinct-values(($git:date cast as xs:gDay, xs:gDay("---10"))))
};

declare
    %test:assertEquals(1)
function git:group-by-without-timezone() {
    count(
        for $d in ($git:date cast as xs:gDay, xs:gDay("---10"))
        group by $d
        return $d
    )
};

(: the other types already compared this way :)
declare
    %test:assertTrue
function git:gmonthday-eq-without-timezone() {
    $git:date cast as xs:gMonthDay eq xs:gMonthDay("--10-10")
};

(: a value in UTC equals one without a timezone only if the implicit timezone is UTC :)
declare
    %test:assertTrue
function git:utc-eq-without-timezone-only-in-utc() {
    let $in-utc := implicit-timezone() eq xs:dayTimeDuration("PT0S")
    return
        (xs:gMonth("--10Z") eq xs:gMonth("--10")) eq $in-utc
        and (xs:gDay("---10Z") eq xs:gDay("---10")) eq $in-utc
};

(: different timezones, same instant: 1972-12-11T00:00+12:00 and 1972-12-10T00:00-12:00 :)
declare
    %test:assertTrue
function git:gday-eq-different-timezones-same-instant() {
    xs:gDay("---11+12:00") eq xs:gDay("---10-12:00")
};

(: the examples in XPath F&O 3.1 for op:gDay-equal and op:gMonth-equal with explicit timezones :)
declare
    %test:assertEquals("false", "true", "false")
function git:spec-examples() {
    xs:gDay("---12-05:00") eq xs:gDay("---12Z"),
    xs:gDay("---12-05:00") eq xs:gDay("---12-05:00"),
    xs:gMonth("--12-14:00") eq xs:gMonth("--12+10:00")
};

declare
    %test:assertError("XPTY0004")
function git:gday-eq-gmonth-is-type-error() {
    xs:gDay("---10") eq xs:gMonth("--10")
};
