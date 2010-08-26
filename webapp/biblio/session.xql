xquery version "1.0";

(:~
    Handles the actual display of the search result. The pagination jQuery plugin in jquery-utils.js
    will call this query to retrieve the next page of search results.
    
    The query returns a simple table with four columns: 
    1) the number of the current record, 
    2) a link to save the current record in "My Lists", 
    3) the type of resource (represented by an icon), and 
    4) the data to display.
:)

import module namespace mods="http://www.loc.gov/mods/v3" at "retrieve-mods.xql";
import module namespace jquery="http://exist-db.org/xquery/jquery" at "resource:org/exist/xquery/lib/jquery.xql";

declare namespace bs="http://exist-db.org/xquery/biblio/session";

declare option exist:serialize "media-type=application/xhtml+xml";

declare function bs:retrieve($start as xs:int, $count as xs:int) {
    let $cached := session:get-attribute("mods:cached")
    let $stored := session:get-attribute("mods-personal-list")
    let $total := count($cached)
    let $available :=
        if ($start + $count gt $total) then
            $total - $start + 1
        else
            $count
    return
        <table xmlns="http://www.w3.org/1999/xhtml">
        {
            for $item at $pos in subsequence($cached, $start, $available)
            let $currentPos := $start + $pos - 1
            let $id := concat(document-uri(root($item)), '#', util:node-id($item))
            let $saved := $stored//*[@id = $id]
            return
                <tr>
                    <td class="current">{$currentPos}</td>
                    {
                        if ($count gt 1) then
                            <td class="actions">
                                <a id="save_{$id}" href="#{$currentPos}" class="save">
                                    <img title="save to my list" 
                                        src="{if ($saved) then 'disk_gew.gif' else 'disk.gif'}"
                                        class="{if ($saved) then 'stored' else ''}"/>
                                </a>
                            </td>
                        else
                            ()
                    }
                    <td>
                        <img title="{$item/mods:typeOfResource/string()}" 
                          src="img/{mods:return-type(string($currentPos), $item)}.png"/>
                    </td>
                    {
                        if ($count eq 1) then
                            <td class="detail-view">
                                <div class="actions-toolbar">
                                    <a id="save_{$id}" href="#{$currentPos}" class="save">
                                       <img title="save to my list" 
                                           src="{if ($saved) then 'disk_gew.gif' else 'disk.gif'}"
                                           class="{if ($saved) then 'stored' else ''}"/>
                                   </a>
                                   {
                                       if($count eq 1)then(
                                           <a class="remove-resource" href="#{$id}"><img title="delete" src="img/delete.png"/></a>,
                                           <a class="move-resource" href="#{$id}"><img title="move" src="img/shape_move_front.png"/></a>
                                       )else()
                                   }
                                </div>
                                { mods:format-full(string($currentPos), $item) }
                            </td>
                        else
                            <td class="pagination-toggle"><a>{mods:format-short(string($currentPos), $item)}</a></td>
                    }
                </tr>
        }
        </table>
};

session:create(),
let $start0 := request:get-parameter("start", ())
let $start := xs:int(if ($start0) then $start0 else 1)
let $count0 := request:get-parameter("count", ())
let $count := xs:int(if ($count0) then $count0 else 10)
return
    bs:retrieve($start, $count)
