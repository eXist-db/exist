xquery version "1.0";

declare namespace atom="http://www.w3.org/2005/Atom";
declare namespace wiki="http://exist-db.org/xquery/wiki";

declare option exist:serialize "method=text media-type=application/json";

declare variable $atom:server := "http://exist-db.org";
declare variable $atom:uri := concat($atom:server, "/apps/wiki/atom/blogs/eXist/");
declare variable $atom:params := "?count=4";
declare variable $atom:entry := concat($atom:server, "/apps/wiki/blogs/eXist/");

declare function atom:format-entry($feed as element()) {
    <ul>
    {
        for $entry in subsequence($feed//atom:entry, 1, 4)
        let $link := concat($atom:entry, $entry/wiki:id)
        return
            <li>
                <p class="date">
                    {substring($entry/atom:published, 1, 10)}
                </p>
                <a href="{$link}">
                    {$entry/atom:title/text()}
                </a>
            </li>
    }
    </ul>
};

let $uri := xs:anyURI(concat($atom:uri, $atom:params))
let $response := doc($uri)
let $output :=
    util:serialize(atom:format-entry($response/atom:feed), "indent=no")
return
	concat("atomCallback('", $output, "');")
