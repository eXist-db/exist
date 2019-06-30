xquery version "3.1";

module namespace parse-ietf-date-spec="http://exist-db.org/xquery/test/parse-ietf-date-spec";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare
    %test:args('Tue, 18 Jun 2019 13:10:56 GMT', '2019-06-18T13:10:56Z') %test:assertTrue
    %test:args('Tue, 01 Jun 70 13:10:56 +5:00', '1970-06-01T13:10:56+05:00') %test:assertTrue
    %test:args('Tue 25 Dec 81 18:12:34 EST', '1981-12-25T18:12:34-05:00') %test:assertTrue
    %test:args('Tue 01-Jan-70 00:00:00 -1:30', '1970-01-01T00:00:00-01:30') %test:assertTrue
    %test:args("Wed, 06 Jun 1994 07:29:35 GMT", "1994-06-06T07:29:35Z") %test:assertTrue
    %test:args(" Wed, 6 Jun 94 07:29:35 GMT ", "1994-06-06T07:29:35Z") %test:assertTrue
    %test:args("Wed Jun 06 11:54:45 EST 2013", "2013-06-06T11:54:45-05:00") %test:assertTrue
    %test:args("Sunday, 06-Nov-94 08:49:37 GMT", "1994-11-06T08:49:37Z") %test:assertTrue
    %test:args("&#10;Sun,&#10;&#09;06&#10;&#09;-&#10;&#09;Nov&#10;&#09;-&#10;&#09;94&#10;&#09;08:49:37&#10;&#09;GMT&#10;", "1994-11-06T08:49:37Z") %test:assertTrue
    %test:args("Wed, 6 Jun 94 07:29:35 +0100 (CET)", "1994-06-06T07:29:35+01:00") %test:assertTrue
function parse-ietf-date-spec:test-valid-date ($date, $expected) {
    fn:parse-ietf-date($date) eq xs:dateTime($expected)
};

declare
    %test:assertTrue
function parse-ietf-date-spec:test-empty-date () {
    empty(fn:parse-ietf-date(()))
};


declare
    %test:args('') %test:assertError("FORG0010")
    %test:args('asdfa') %test:assertError("FORG0010")

    %test:args(0) %test:assertError("FORG0010")
    %test:args(1) %test:assertError("FORG0010")

    %test:args('-1') %test:assertError("FORG0010")
    %test:args('Tue, 30 Feb 2019 13:10:56 GMT') %test:assertError("FORG0010")
    %test:args('Tue Feb 28 2019 13:10:56 XYZ') %test:assertError("FORG0010")
    %test:args('Tue Feb 28 -2019 13:10:56') %test:assertError("FORG0010")
    %test:args('Tue Feb 28 2019 30:10') %test:assertError("FORG0010")
function parse-ietf-date-spec:test-invalid-date ($date) {
    fn:parse-ietf-date($date)
};
