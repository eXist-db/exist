xquery version "1.0";

(:~
    Returns the list of distinct authors, dates etc. occurring in the result set.
    The query is called via AJAX when the user expands one of the headings in the
    "filter" box.
:)
import module namespace names="http://exist-db.org/xquery/biblio/names"
    at "names.xql";
    
declare namespace mods="http://www.loc.gov/mods/v3";

declare function local:key($key, $options) {
    <li><a href="?filter=Title&amp;value={$key}&amp;query-tabs=advanced">{$key} ({$options[1]})</a></li>
};

let $type := request:get-parameter("type", ())
let $cached := session:get-attribute("cached")
return
    <ul xmlns="http://www.w3.org/1999/xhtml">
    {
        if ($type eq 'author') then
            let $authors :=
                for $author in $cached//mods:name
                return names:format-name($author)
            let $distinct := distinct-values($authors)
            for $name in $distinct
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
        else if ($type eq 'keywords') then
            let $callback := util:function(xs:QName("local:key"), 2)
            return
                util:index-keys($cached//mods:titleInfo, "", $callback, 600, "lucene-index")
        else
            ()
    }
    </ul>
