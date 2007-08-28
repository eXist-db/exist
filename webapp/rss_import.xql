xquery version "1.0";

declare namespace f="http://exist-db.org/xquery/local-functions";
declare namespace dc="http://purl.org/dc/elements/1.1/";

import module namespace xdb="http://exist-db.org/xquery/xmldb";

declare option exist:serialize "media-type=text/xml";

let $col := xdb:create-collection("/db", "rss")
let $rssUri := xs:anyURI("http://wiki.exist-db.org/exec/rss?snip=start")
let $stored := util:catch("java.lang.Exception",
                   xdb:store($col, "news.rss", $rssUri, "text/xml"),
                   ()
               )           
return
    if ($stored) then
        <ul>
        {
            for $item in doc($stored)//item
            return (
                <p class="date">
                    {substring($item/dc:date, 1, 10)}
                </p>,
                
                <a href="{$item/link/text()}">{$item/title/text()}</a>
            )
        }        
        </ul>
    else
        <p>Failed to load news from Wiki...</p>    