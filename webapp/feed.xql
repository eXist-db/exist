xquery version "1.0";

declare namespace atom="http://www.w3.org/2005/Atom";

declare variable $atom:server := "http://atomic.exist-db.org";
declare variable $atom:uri := concat($atom:server, "/atom/summary/wiki/blogs/eXist/");

declare function atom:format-entry($feed as element()) {
    <ul>
    {
        for $entry in $feed//atom:entry
        let $link := $entry/atom:link[@rel = 'alternate'][@type = 'text/html']/@href
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

let $uri := xs:anyURI($atom:uri)
let $response := doc($uri)
return
    atom:format-entry($response/atom:feed)
