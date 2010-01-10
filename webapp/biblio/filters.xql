xquery version "1.0";

(:~
    Returns the list of distinct authors, dates etc. occurring in the result set.
    The query is called via AJAX when the user expands one of the headings in the
    "filter" box.
:)
import module namespace mods="http://www.loc.gov/mods/v3" at "retrieve-mods.xql";

let $type := request:get-parameter("type", ())
let $cached := session:get-attribute("cached")
return
    <ul xmlns="http://www.w3.org/1999/xhtml">
    {
        if ($type eq 'author') then
            let $authors :=
                for $author in $cached//mods:name
                return mods:format-name($author, 1)
            for $name in distinct-values($authors)
            order by $name
            return
                <li><a href="?filter=Author&amp;value={$name}&amp;query-tabs=advanced">{$name}</a></li>
        else if ($type eq 'date') then
            let $dates :=
                for $info in $cached/mods:originInfo
                return
                    ($info/mods:dateCreated | $info/mods:dateIssued)[1]
            for $date in distinct-values($dates)
            order by $date descending
            return
                <li><a href="?filter=Date&amp;value={$date}&amp;query-tabs=advanced">{$date}</a></li>
        else
            ()
    }
    </ul>