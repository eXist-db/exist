(:  This XQuery is used to generate a collection listing
    in response to a GET request on a collection
:)
xquery version "1.0";

(::pragma exist:output-size-limit -1 ::)

import module namespace xdb="http://exist-db.org/xquery/xmldb";

declare namespace f="urn:my-functions";

declare variable $f:months {
	("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct",
	"Nov", "Dec")
};

declare function f:format-date($date as xs:dateTime) as xs:string {
	string-join((
		xs:string(item-at($f:months, month-from-date($date))),
		xs:string(day-from-date($date)),
		xs:string(year-from-date($date))), " ")
};

declare function f:format-int($component as xs:integer) as xs:string {
	if($component lt 10) then
		concat("0", $component)
	else
		xs:string($component)
};

declare function f:format-time($time as xs:dateTime) as xs:string {
	concat(
		f:format-int(hours-from-dateTime($time)), ":",
		f:format-int(minutes-from-dateTime($time)), ":",
		f:format-int(xs:integer(seconds-from-dateTime($time)))
	)
};

declare function f:format-dateTime($dt as xs:dateTime) as xs:string {
    concat(f:format-date($dt), " ", f:format-time($dt))
};

declare function f:display-child-resources($collection as xs:string)
as element()* {
    for $child in xdb:get-child-resources($collection)
    order by $child
    return
        <tr>
            <td><a target="_new" href="{$uri}/{$child}">{$child}</a></td>
            <td class="perm">{xdb:permissions-to-string(xdb:get-permissions($collection, $child))}</td>
            <td>{xdb:get-owner($collection, $child)}</td>
            <td>{xdb:get-group($collection, $child)}</td>
            <td>{f:format-dateTime(xdb:created($collection, $child))}</td>
            <td>{f:format-dateTime(xdb:last-modified($collection, $child))}</td>
            <td>{fn:ceiling(xdb:size($collection, $child) div 1024)}</td>
        </tr>
};

declare function f:display-child-collections($collection as xs:string)
as element()* {
    for $child in xdb:get-child-collections($collection)
    let $path := concat($collection, '/', $child),
        $created := xdb:created($path)
    order by $child
    return
        <tr>
            <td><a href="{$uri}/{$child}">{$child}</a></td>
            <td class="perm">{xdb:permissions-to-string(xdb:get-permissions($path))}</td>
            <td>{xdb:get-owner($path)}</td>
            <td>{xdb:get-group($path)}</td>
            <td>{f:format-dateTime($created)}</td>
            <td/>
            <td/>
        </tr>
};

<html>
    <head>
        <title>{$collection}</title>
    </head>
    <body>
        <h2>Contents of {$collection}</h2>
        <table border="0" cellspacing="8">
            <tr>
                <th align="left">Name</th>
                <th align="left">Permissions</th>
                <th align="left">Owner</th>
                <th align="left">Group</th>
                <th align="left">Created</th>
                <th align="left">Last Modified</th>
            </tr>
            {f:display-child-collections($collection)}
            {f:display-child-resources($collection)}
        </table>
    </body>
</html>