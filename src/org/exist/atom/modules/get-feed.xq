declare namespace atom="http://www.w3.org/2005/Atom";

let $feed := /atom:feed
return element atom:feed{
    $feed/@*, $feed/*,
    for $entry in collection(string-join((substring-before(base-uri($feed),'/.feed.atom'),".feed.entry"),"/"))/atom:entry
    order by xs:dateTime($entry/published) descending
    return $entry
}