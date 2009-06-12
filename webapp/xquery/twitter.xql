xquery version "1.0";

(: -----------------------------------------------------------------------
   A simple twitter client. This is the main script which is called from 
   controller.xql. It retrieves an atom feed from twitter and caches
   it in the xmldb. The data is then passed to twitter-view.xql for
   formatting and display.
   -----------------------------------------------------------------------:)

declare namespace tc="http://exist-db.org/xquery/twitter-client";
declare namespace atom="http://www.w3.org/2005/Atom";
declare namespace html="http://www.w3.org/1999/xhtml";

import module namespace httpclient="http://exist-db.org/xquery/httpclient"
    at "java:org.exist.xquery.modules.httpclient.HTTPClientModule";
import module namespace xdb="http://exist-db.org/xquery/xmldb";

(: To access more than just the tweets posted by a single user (e.g. its friends timeline), 
   you need to authenticate with a valid twitter account. Enter the username/password pair
   below. :)
declare variable $tc:login := ( "user", "password" );

declare variable $tc:update-frequency := xs:dayTimeDuration("PT5M");

(: Create the HTTP basic authentication header if user credentials available :)
declare function tc:get-headers($credentials as xs:string*) {
    if (empty($credentials)) then
        ()
    else
        let $auth := concat('Basic ', util:string-to-binary(concat($credentials[1], ':', $credentials[2])))
        return
            <headers>
                <header name="Authorization" value="{$auth}"/>
            </headers>
};

(: Send an HTTP request to twitter to retrieve the timeline in Atom format :)
declare function tc:get-timeline($credentials as xs:string*, $userId as xs:string, $view as xs:string) {
    let $uri := xs:anyURI(
        concat("http://twitter.com/statuses/", $view, "_timeline/", $userId, ".atom")
    )
    let $headers := tc:get-headers($credentials)
    let $response := httpclient:get($uri, false(), $headers)
    return
        if ($response/@statusCode eq "200") then
            $response/httpclient:body/*
        else if ($response/httpclient:body//error) then
            $response/httpclient:body//error/string()
        else
            concat("Twitter reported an error. Code: ", $response/@statusCode)
};

(: Retrieve the timeline and store it into the db :)
declare function tc:update-timeline($credentials as xs:string*, $userId as xs:string, $view as xs:string) {
    let $null := xdb:create-collection("/db", "twitter")
    let $feed := tc:get-timeline($credentials, $userId, $view)
    return
        if (empty($feed) or $feed instance of xs:string) then
            $feed
        else
            let $docPath := xdb:store("/db/twitter", concat($userId, "_", $view, ".xml"), $feed)
            return
                doc($docPath)/atom:feed
};

(: Main function: returns the timeline in atom format. The data is cached within the database
   and will be renewed every few minutes. :)
declare function tc:timeline($credentials as xs:string*, $userId as xs:string, $view as xs:string) {
    let $feed := doc(concat("/db/twitter/", $userId, "_", $view, ".xml"))/atom:feed
    return
        if (exists($feed) and 
            (xs:dateTime($feed/atom:updated) + $tc:update-frequency) > current-dateTime()) then
            $feed
        else
            tc:update-timeline($credentials, $userId, $view)
};

(: This script will just retrieve the feed, then forward it to
   twitter-view.xql, using a request attribute. The forwarding is done
   through controller.xql :)
let $user := request:get-parameter("user", ())
let $view := request:get-parameter("view", "user")
let $feed :=
    if ($user) then
        tc:timeline(
            if ($tc:login[1] eq 'user') then () else $tc:login,
            $user, $view
        )
    else ()
return
    request:set-attribute("twitter.feed", $feed)
