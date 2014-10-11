xquery version "3.0";

module namespace fd="http://exist-db.org/xquery/test/format-dates";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare
    %test:args("2012-06-26")
    %test:assertEquals("26.6.2012")
    %test:args("2012-06-01")
    %test:assertEquals("1.6.2012")
function fd:simple-date($date as xs:date) {
    format-date($date, "[D].[M].[Y]")
};

declare
    %test:args("2012-06-26")
    %test:assertEquals("26.06.2012")
    %test:args("2012-06-01")
    %test:assertEquals("01.06.2012")
function fd:simple-format($date as xs:date) {
    format-date($date, "[D00].[M01].[Y0]")
};

declare
    %test:args("2012-06-26")
    %test:assertEquals("26.6.2012")
    %test:args("2012-06-01")
    %test:assertEquals("1.6.2012")
function fd:format-width($date as xs:date) {
    format-date($date, "[D,1-2].[M,*-*].[Y,*]")
};

declare
    %test:args("2012-06-26")
    %test:assertEquals("Tuesday, June 26, 2012")
    %test:args("2012-06-01")
    %test:assertEquals("Friday, June 1, 2012")
function fd:format-names($date as xs:date) {
    format-date($date, "[FNn], [MNn] [D], [Y]")
};

declare
    %test:args("2012-06-26")
    %test:assertEquals("Tuesday, June 26th, 2012")
    %test:args("2012-06-01")
    %test:assertEquals("Friday, June 1st, 2012")
    %test:args("2012-06-02")
    %test:assertEquals("Saturday, June 2nd, 2012")
    %test:args("2012-06-03")
    %test:assertEquals("Sunday, June 3rd, 2012")
function fd:format-ordinal($date as xs:date) {
    format-date($date, "[FNn], [MNn] [D0o], [Y]")
};

declare
    %test:args("2012-06-26T23:14:22.566+02:00")
    %test:assertEquals("11:14 pm on Tuesday, June 26th, 2012")
function fd:format-dateTime($date as xs:dateTime) {
    format-dateTime($date, "[h00]:[m00] [P] on [FNn], [MNn] [D1o], [Y]")
};

declare
    %test:args("2012-06-26+02:00")
    %test:assertEquals("20120626GMT+02:00")
function fd:format-date-z($date as xs:date) {
    format-date($date, "[Y0001][M01][D01][z]")
};

declare
    %test:args("2012-06-26+02:00")
    %test:assertEquals("20120626+02:00")
function fd:format-date-Z($date as xs:date) {
    format-date($date, "[Y0001][M01][D01][Z]")
};

declare
    %test:args("2012-06-26T23:14:22.566+02:00")
    %test:assertEquals("20120626231422GMT+02:00")
function fd:format-dateTime-z($date as xs:dateTime) {
    format-dateTime($date, "[Y0001][M01][D01][H01][m01][s01][z]")
};

declare
    %test:args("2012-06-26T23:14:22.566+02:00")
    %test:assertEquals("20120626231422+02:00")
function fd:format-dateTime-Z($date as xs:dateTime) {
    format-dateTime($date, "[Y0001][M01][D01][H01][m01][s01][Z]")
};

declare
    %test:args("2012-06-26T23:14:22.566")
    %test:assertEquals("20120626231422")
function fd:format-dateTime-no-tz-z($date as xs:dateTime) {
    format-dateTime($date, "[Y0001][M01][D01][H01][m01][s01][z]")
};

declare
    %test:args("2012-06-26T23:14:22.566")
    %test:assertEquals("20120626231422")
function fd:format-dateTime-no-tz-Z($date as xs:dateTime) {
    format-dateTime($date, "[Y0001][M01][D01][H01][m01][s01][Z]")
};

declare
    %test:args("17:45:50")
    %test:assertEquals("17:45:50")
    %test:args("09:45:50")
    %test:assertEquals("09:45:50")
function fd:simple-time($time as xs:time) {
    format-time($time, "[H00]:[m00]:[s]")
};

declare
    %test:args("17:45:50")
    %test:assertEquals("05:45 pm")
    %test:args("09:45:50")
    %test:assertEquals("09:45 am")
function fd:time-am-pm($time as xs:time) {
    format-time($time, "[h00]:[m00] [P]")
};

declare
    %test:assertError
function fd:date-format-in-format-time() {
    format-time(current-time(), "[Y]")
};

declare
    %test:assertError
function fd:time-format-in-format-date() {
    format-date(current-dateTime(), "[H]")
};

declare
    %test:args("2012-06-27T10:21:55.082+02:00")
    %test:assertEquals("Mittwoch, 27. Juni 2012")
    %test:args("1970-10-07T10:21:55.082+02:00")
    %test:assertEquals("Mittwoch, 7. Oktober 1970")
function fd:language($date as xs:dateTime) {
    format-date($date, "[FNn], [D1o] [MNn] [Y]", "de", (), ())
};

declare
    %test:args("2012-06-27T10:21:55.082+02:00")
    %test:assertEquals("Среда, 27 Июня 2012")
    %test:args("1970-10-07T10:21:55.082+02:00")
    %test:assertEquals("Среда, 7 Октября 1970")
function fd:language-ru($date as xs:dateTime) {
    format-date($date, "[FNn], [D1o] [MNn] [Y]", "ru", (), ())
};