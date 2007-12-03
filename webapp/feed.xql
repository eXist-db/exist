xquery version "1.0";

declare namespace atom="http://www.w3.org/2005/Atom";

import module namespace httpclient="http://exist-db.org/xquery/httpclient"
    at "java:org.exist.xquery.modules.httpclient.HTTPClientModule";
import module namespace json="http://www.json.org" at "resource:org/exist/xquery/lib/json.xq";

declare variable $atom:uri := "http://atomic.exist-db.org/atom/summary/wiki/blogs/eXist/";

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
                <a href="http://localhost:8000{$link}">
                    {$entry/atom:title/text()}
                </a>
            </li>
    }
    </ul>
};

let $uri := xs:anyURI($atom:uri)
let $response := httpclient:get($uri, false(), ())
return
    atom:format-entry($response/httpclient:body/atom:feed)