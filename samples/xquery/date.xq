xquery version "1.0";

declare variable $months {
	("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct",
	"Nov", "Dec")
};

declare function local:format-date($date as xs:dateTime) as xs:string {
	string-join((
		item-at($months, month-from-date($date)),
		day-from-date($date),
		year-from-date($date)), " ")
};

declare function local:format-int($component as xs:integer) as xs:string {
	if($component lt 10) then
		concat("0", $component)
	else
		xs:string($component)
};

declare function local:format-time($time as xs:dateTime) as xs:string {
	concat(
		local:format-int(hours-from-dateTime($time)), ":",
		local:format-int(minutes-from-dateTime($time)), ":",
		local:format-int(xs:integer(seconds-from-dateTime($time)))
	)
};

declare function local:format-dateTime($tz as xdt:dayTimeDuration) as xs:string {
	let $now := adjust-time-to-timezone(current-dateTime(), $tz)
	return
		concat(local:format-date($now), " ", local:format-time($now))
};
	
let $tz := xdt:dayTimeDuration("PT2H")
return
	<now>
		{local:format-dateTime($tz)}
	</now>
