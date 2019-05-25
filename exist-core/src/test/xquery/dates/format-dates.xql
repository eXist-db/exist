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
function fd:format-month-numeric-width($date as xs:date) {
    format-date($date, "[D,1-2].[M,*-*].[Y,*]")
};

declare
    %test:args("2012-06-26")
    %test:assertEquals("26 JUN 2012")
    %test:args("1970-10-07")
    %test:assertEquals("07 OCT 1970")
function fd:format-month-string-width-caps($date as xs:date) {
    format-date($date, "[D01] [MN,*-3] [Y0001]", "en", (), ())
};

declare
    %test:args("2012-06-26")
    %test:assertEquals("26 Jun 2012")
    %test:args("1970-10-07")
    %test:assertEquals("07 Oct 1970")
function fd:format-month-string-width-title($date as xs:date) {
    format-date($date, "[D01] [MNn,*-3] [Y0001]", "en", (), ())
};

declare
    %test:args("2012-06-26")
    %test:assertEquals("Tuesday, June 26, 2012")
    %test:args("1970-10-07")
    %test:assertEquals("Wednesday, October 7, 1970")
function fd:format-names($date as xs:date) {
    format-date($date, "[FNn], [MNn] [D], [Y]", "en", (), ())
};

declare
    %test:args("2012-06-26")
    %test:assertEquals("Tue Jun 26 2012")
    %test:args("1970-10-07")
    %test:assertEquals("Wed Oct 7 1970")
function fd:format-names-width($date as xs:date) {
    format-date($date, "[FNn,*-3] [MNn,*-3] [D] [Y]", "en", (), ())
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
    format-date($date, "[FNn], [MNn] [D0o], [Y]", "en", (), ())
};

declare
    %test:args("2002-12-31")
    %test:assertEquals("[2002-12-31]")
function fd:format-square-brackets($date as xs:date) {
    format-date($date, "[[[Y0001]-[M01]-[D01]]]")
};

declare
    %test:args("2012-06-26T23:14:22.566+02:00")
    %test:assertEquals("11:14 pm on Tuesday, June 26th, 2012")
function fd:format-dateTime($date as xs:dateTime) {
    format-dateTime($date, "[h00]:[m00] [P] on [FNn], [MNn] [D1o], [Y]", "en", (), ())
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
    %test:args("2012-06-26T12:00:00.000-10:00")
    %test:assertEquals("GMT-10:00")
function fd:format-dateTime-z-negative-double-digit($date as xs:dateTime) {
    format-dateTime($date, "[z]")
};

declare
    %test:args("2012-06-26T12:00:00.000-05:00")
    %test:assertEquals("GMT-05:00")
function fd:format-dateTime-z-negative-single-digit($date as xs:dateTime) {
    format-dateTime($date, "[z]")
};

declare
    %test:args("2012-06-26T12:00:00.000+00:00")
    %test:assertEquals("GMT+00:00")
function fd:format-dateTime-z-zero($date as xs:dateTime) {
    format-dateTime($date, "[z]")
};

declare
    %test:args("2012-06-26T12:00:00.000+05:30")
    %test:assertEquals("GMT+05:30")
function fd:format-dateTime-z-positive-single-digit($date as xs:dateTime) {
    format-dateTime($date, "[z]")
};

declare
    %test:args("2012-06-26T12:00:00.000+13:00")
    %test:assertEquals("GMT+13:00")
function fd:format-dateTime-z-positive-double-digit($date as xs:dateTime) {
    format-dateTime($date, "[z]")
};

declare
    %test:args("2012-06-26T23:14:22.566+02:00")
    %test:assertEquals("20120626231422+02:00")
function fd:format-dateTime-Z($date as xs:dateTime) {
    format-dateTime($date, "[Y0001][M01][D01][H01][m01][s01][Z]")
};

declare
    %test:args("2012-06-26T12:00:00.000-10:00")
    %test:assertEquals("-10:00")
function fd:format-dateTime-Z-negative-double-digit($date as xs:dateTime) {
    format-dateTime($date, "[Z]")
};

declare
    %test:args("2012-06-26T12:00:00.000-05:00")
    %test:assertEquals("-05:00")
function fd:format-dateTime-Z-negative-single-digit($date as xs:dateTime) {
    format-dateTime($date, "[Z]")
};

declare
    %test:args("2012-06-26T12:00:00.000+00:00")
    %test:assertEquals("+00:00")
function fd:format-dateTime-Z-zero($date as xs:dateTime) {
    format-dateTime($date, "[Z]")
};

declare
    %test:args("2012-06-26T12:00:00.000+05:30")
    %test:assertEquals("+05:30")
function fd:format-dateTime-Z-positive-single-digit($date as xs:dateTime) {
    format-dateTime($date, "[Z]")
};

declare
    %test:args("2012-06-26T12:00:00.000+13:00")
    %test:assertEquals("+13:00")
function fd:format-dateTime-Z-positive-double-digit($date as xs:dateTime) {
    format-dateTime($date, "[Z]")
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
    %test:args("2012-06-26T12:00:00.000-10:00")
    %test:assertEquals("-10")
function fd:format-dateTime-Z0-negative-double-digit($date as xs:dateTime) {
    format-dateTime($date, "[Z0]")
};

declare
    %test:args("2012-06-26T12:00:00.000-05:00")
    %test:assertEquals("-5")
function fd:format-dateTime-Z0-negative-single-digit($date as xs:dateTime) {
    format-dateTime($date, "[Z0]")
};

declare
    %test:args("2012-06-26T12:00:00.000+00:00")
    %test:assertEquals("+0")
function fd:format-dateTime-Z0-zero($date as xs:dateTime) {
    format-dateTime($date, "[Z0]")
};

declare
    %test:args("2012-06-26T12:00:00.000+05:30")
    %test:assertEquals("+5:30")
function fd:format-dateTime-Z0-positive-single-digit($date as xs:dateTime) {
    format-dateTime($date, "[Z0]")
};

declare
    %test:args("2012-06-26T12:00:00.000+13:00")
    %test:assertEquals("+13")
function fd:format-dateTime-Z0-positive-double-digit($date as xs:dateTime) {
    format-dateTime($date, "[Z0]")
};

declare
    %test:args("2012-06-26T12:00:00.000-10:00")
    %test:assertEquals("-10:00")
function fd:format-dateTime-Z0-00-negative-double-digit($date as xs:dateTime) {
    format-dateTime($date, "[Z0:00]")
};

declare
    %test:args("2012-06-26T12:00:00.000-05:00")
    %test:assertEquals("-5:00")
function fd:format-dateTime-Z0-00-negative-single-digit($date as xs:dateTime) {
    format-dateTime($date, "[Z0:00]")
};

declare
    %test:args("2012-06-26T12:00:00.000+00:00")
    %test:assertEquals("+0:00")
function fd:format-dateTime-Z0-00-zero($date as xs:dateTime) {
    format-dateTime($date, "[Z0:00]")
};

declare
    %test:args("2012-06-26T12:00:00.000+05:30")
    %test:assertEquals("+5:30")
function fd:format-dateTime-Z0-00-positive-single-digit($date as xs:dateTime) {
    format-dateTime($date, "[Z0:00]")
};

declare
    %test:args("2012-06-26T12:00:00.000+13:00")
    %test:assertEquals("+13:00")
function fd:format-dateTime-Z0-00-positive-double-digit($date as xs:dateTime) {
    format-dateTime($date, "[Z0:00]")
};

declare
    %test:args("2012-06-26T12:00:00.000-10:00")
    %test:assertEquals("-10:00")
function fd:format-dateTime-Z00-00-negative-double-digit($date as xs:dateTime) {
    format-dateTime($date, "[Z00:00]")
};

declare
    %test:args("2012-06-26T12:00:00.000-05:00")
    %test:assertEquals("-05:00")
function fd:format-dateTime-Z00-00-negative-single-digit($date as xs:dateTime) {
    format-dateTime($date, "[Z00:00]")
};

declare
    %test:args("2012-06-26T12:00:00.000+00:00")
    %test:assertEquals("+00:00")
function fd:format-dateTime-Z00-00-zero($date as xs:dateTime) {
    format-dateTime($date, "[Z00:00]")
};

declare
    %test:args("2012-06-26T12:00:00.000+05:30")
    %test:assertEquals("+05:30")
function fd:format-dateTime-Z00-00-positive-single-digit($date as xs:dateTime) {
    format-dateTime($date, "[Z00:00]")
};

declare
    %test:args("2012-06-26T12:00:00.000+13:00")
    %test:assertEquals("+13:00")
function fd:format-dateTime-Z00-00-positive-double-digit($date as xs:dateTime) {
    format-dateTime($date, "[Z00:00]")
};

declare
    %test:args("2012-06-26T12:00:00.000-10:00")
    %test:assertEquals("-1000")
function fd:format-dateTime-Z0000-negative-double-digit($date as xs:dateTime) {
    format-dateTime($date, "[Z0000]")
};

declare
    %test:args("2012-06-26T12:00:00.000-05:00")
    %test:assertEquals("-0500")
function fd:format-dateTime-Z0000-negative-single-digit($date as xs:dateTime) {
    format-dateTime($date, "[Z0000]")
};

declare
    %test:args("2012-06-26T12:00:00.000+00:00")
    %test:assertEquals("+0000")
function fd:format-dateTime-Z0000-zero($date as xs:dateTime) {
    format-dateTime($date, "[Z0000]")
};

declare
    %test:args("2012-06-26T12:00:00.000+05:30")
    %test:assertEquals("+0530")
function fd:format-dateTime-Z0000-positive-single-digit($date as xs:dateTime) {
    format-dateTime($date, "[Z0000]")
};

declare
    %test:args("2012-06-26T12:00:00.000+13:00")
    %test:assertEquals("+1300")
function fd:format-dateTime-Z0000-positive-double-digit($date as xs:dateTime) {
    format-dateTime($date, "[Z0000]")
};

declare
    %test:args("2012-06-26T12:00:00.000-10:00")
    %test:assertEquals("-10:00")
function fd:format-dateTime-Z00-00t-negative-double-digit($date as xs:dateTime) {
    format-dateTime($date, "[Z00:00t]")
};

declare
    %test:args("2012-06-26T12:00:00.000-05:00")
    %test:assertEquals("-05:00")
function fd:format-dateTime-Z00-00t-negative-single-digit($date as xs:dateTime) {
    format-dateTime($date, "[Z00:00t]")
};

declare
    %test:args("2012-06-26T12:00:00.000+00:00")
    %test:assertEquals("Z")
function fd:format-dateTime-Z00-00t-zero($date as xs:dateTime) {
    format-dateTime($date, "[Z00:00t]")
};

declare
    %test:args("2012-06-26T12:00:00.000+05:30")
    %test:assertEquals("+05:30")
function fd:format-dateTime-Z00-00t-positive-single-digit($date as xs:dateTime) {
    format-dateTime($date, "[Z00:00t]")
};

declare
    %test:args("2012-06-26T12:00:00.000+13:00")
    %test:assertEquals("+13:00")
function fd:format-dateTime-Z00-00t-positive-double-digit($date as xs:dateTime) {
    format-dateTime($date, "[Z00:00t]")
};

declare
    %test:args("2012-06-26T12:00:00.000-10:00")
    %test:assertEquals("W")
function fd:format-dateTime-ZZ-negative-double-digit($date as xs:dateTime) {
    format-dateTime($date, "[ZZ]")
};

declare
    %test:args("2012-06-26T12:00:00.000-05:00")
    %test:assertEquals("R")
function fd:format-dateTime-ZZ-negative-single-digit($date as xs:dateTime) {
    format-dateTime($date, "[ZZ]")
};

declare
    %test:args("2012-06-26T12:00:00.000+00:00")
    %test:assertEquals("Z")
function fd:format-dateTime-ZZ-zero($date as xs:dateTime) {
    format-dateTime($date, "[ZZ]")
};

declare
    %test:args("2012-06-26T12:00:00.000+05:30")
    %test:assertEquals("+05:30")
function fd:format-dateTime-ZZ-positive-single-digit($date as xs:dateTime) {
    format-dateTime($date, "[ZZ]")
};

declare
    %test:args("2012-06-26T12:00:00.000+13:00")
    %test:assertEquals("+13:00")
function fd:format-dateTime-ZZ-positive-double-digit($date as xs:dateTime) {
    format-dateTime($date, "[ZZ]")
};

declare
    %test:pending("[ZN] is not yet supported")
    %test:args("2012-06-26T12:00:00.000-10:00")
    %test:assertEquals("HST")
function fd:format-dateTime-ZN-negative-double-digit($date as xs:dateTime) {
    format-dateTime($date, "[ZN]", (), (), "us")
};

declare
    %test:pending("[ZN] is not yet supported")
    %test:args("2012-06-26T12:00:00.000-05:00")
    %test:assertEquals("EST")
function fd:format-dateTime-ZN-negative-single-digit($date as xs:dateTime) {
    format-dateTime($date, "[ZN]", (), (), "us")
};

declare
    %test:pending("[ZN] is not yet supported")
    %test:args("2012-06-26T12:00:00.000+00:00")
    %test:assertEquals("GMT")
function fd:format-dateTime-ZN-zero($date as xs:dateTime) {
    format-dateTime($date, "[ZN]", (), (), "us")
};

declare
    %test:pending("[ZN] is not yet supported")
    %test:args("2012-06-26T12:00:00.000+05:30")
    %test:assertEquals("IST")
function fd:format-dateTime-ZN-positive-single-digit($date as xs:dateTime) {
    format-dateTime($date, "[ZN]", (), (), "us")
};

declare
    %test:pending("[ZN] is not yet supported")
    %test:args("2012-06-26T12:00:00.000+13:00")
    %test:assertEquals("+13:00")
function fd:format-dateTime-ZN-positive-double-digit($date as xs:dateTime) {
    format-dateTime($date, "[ZN]", (), (), "us")
};

declare
    %test:pending("[ZN] is not yet supported")
    %test:args("2012-06-26T12:00:00.000-10:00")
    %test:assertEquals("06:00 EST")
function fd:format-dateTime-ZN-NY-negative-double-digit($date as xs:dateTime) {
    format-dateTime($date, "[H00]:[M00] [ZN]", (), (), "America/New_York")
};

declare
    %test:pending("[ZN] is not yet supported")
    %test:args("2012-06-26T12:00:00.000-05:00")
    %test:assertEquals("12:00 EST")
function fd:format-dateTime-ZN-NY-negative-single-digit($date as xs:dateTime) {
    format-dateTime($date, "[H00]:[M00] [ZN]", (), (), "America/New_York")
};

declare
    %test:pending("[ZN] is not yet supported")
    %test:args("2012-06-26T12:00:00.000+00:00")
    %test:assertEquals("07:00 EST")
function fd:format-dateTime-ZN-NY-zero($date as xs:dateTime) {
    format-dateTime($date, "[H00]:[M00] [ZN]", (), (), "America/New_York")
};

declare
    %test:pending("[ZN] is not yet supported")
    %test:args("2012-06-26T12:00:00.000+05:30")
    %test:assertEquals("01:30 EST")
function fd:format-dateTime-ZN-NY-positive-single-digit($date as xs:dateTime) {
    format-dateTime($date, "[H00]:[M00] [ZN]", (), (), "America/New_York")
};

declare
    %test:pending("[ZN] is not yet supported")
    %test:args("2012-06-26T12:00:00.000+13:00")
    %test:assertEquals("18:00 EST")
function fd:format-dateTime-ZN-NY-positive-double-digit($date as xs:dateTime) {
    format-dateTime($date, "[H00]:[M00] [ZN]", (), (), "America/New_York")
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
    %test:args("2012-06-27Z")
    %test:assertEquals("Mittwoch, 27. Juni 2012")
    %test:args("1970-10-07Z")
    %test:assertEquals("Mittwoch, 7. Oktober 1970")
function fd:language-de($date as xs:date) {
    format-date($date, "[FNn], [D1o] [MNn] [Y]", "de", (), ())
};

declare
    %test:args("2012-06-01Z")
    %test:assertEquals("пятница, 1 июня 2012")
    %test:args("1970-10-07Z")
    %test:assertEquals("среда, 7 октября 1970")
function fd:language-ru($date as xs:date) {
    format-date($date, "[FNn], [D1o] [MNn] [Y]", "ru", (), ())
};

declare
%test:args("2012-06-01Z")
%test:assertEquals("vendredi, 1er juin 2012")
%test:args("1970-10-07Z")
%test:assertEquals("mercredi, 7 octobre 1970")
function fd:language-fr($date as xs:date) {
    format-date($date, "[FNn], [D1o] [MNn] [Y]", "fr", (), ())
};

declare
    %test:args("2018-05-21Z")
    %test:assertEquals("Monday")
    %test:args("2018-05-22Z")
    %test:assertEquals("Tuesday")
    %test:args("2018-05-23Z")
    %test:assertEquals("Wednesday")
    %test:args("2018-05-24Z")
    %test:assertEquals("Thursday")
    %test:args("2018-05-25Z")
    %test:assertEquals("Friday")
    %test:args("2018-05-26Z")
    %test:assertEquals("Saturday")
    %test:args("2018-05-27Z")
    %test:assertEquals("Sunday")
function fd:written-day-en($date-str as xs:string) {
    format-date(xs:date($date-str), "[FNn]", "en", (), ())
};
