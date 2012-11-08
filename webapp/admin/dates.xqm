xquery version "1.0";
(: $Id$ :)
(:
    Module: functions for formatting an xs:dateTime value.
:)

module namespace date="http://exist-db.org/xquery/admin-interface/date";

declare variable $date:months {
	("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct",
	"Nov", "Dec")
};

declare function date:format-date($date as xs:dateTime) as xs:string {
	string-join((
		$date:months[month-from-date($date)],
		xs:string( day-from-date($date)  ),
		xs:string( year-from-date($date) ) ), " ")
};

declare function date:format-int($component as xs:integer) as xs:string {
	if($component lt 10) then
		concat("0", $component)
	else
		xs:string($component)
};

declare function date:format-time($time as xs:dateTime) as xs:string {
	concat(
		date:format-int(hours-from-dateTime($time)), ":",
		date:format-int(minutes-from-dateTime($time)), ":",
		date:format-int(xs:integer(seconds-from-dateTime($time)))
	)
};

declare function date:format-dateTime($dt as xs:dateTime) as xs:string {
    concat(date:format-date($dt), " ", date:format-time($dt))
};
