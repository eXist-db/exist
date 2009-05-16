xquery version "1.0";

(: This is the view part of the twitter client app. twitter.xql forwards
   the twitter timeline in the request attribute "twitter.feed". :)
   
declare namespace tc="http://exist-db.org/xquery/twitter-client";
declare namespace atom="http://www.w3.org/2005/Atom";
declare namespace html="http://www.w3.org/1999/xhtml";

(: Parse the twitter message string. This function will recognize user names, links
   and tags. :)
declare function tc:parse-content($content as xs:string) {
    let $filtered_text := $content
    let $filtered_text := replace($filtered_text,"(http://[A-z0-9/\.?=&amp;\-_%]+)",'<a href="$1" class="url" target="new">$1</a>')
    let $filtered_text := replace($filtered_text,"@([A-z0-9/\.\-_]+)", '<a href="http://twitter.com/$1" class="username">@$1</a>')
    let $filtered_text := replace($filtered_text,"&amp;#([x0-9]+);","entity:$1")
    let $filtered_text := replace($filtered_text,"(&amp;)","$1amp;")
    let $filtered_text := replace($filtered_text,"#([A-z0-9/\-_]+)", '<a href="http://search.twitter.com/search?q=%23$1">#$1</a>')
    let $filtered_text := replace($filtered_text,"\[\[entity:([x0-9]+)\]\]","&amp;#$1;")
    let $filtered_text := concat("<span xmlns='http://www.w3.org/1999/xhtml' class='tw-body'>", $filtered_text, "</span>")
    return util:parse($filtered_text)
};

(: Format an atom entry :)
declare function tc:print-entry($entry as element(atom:entry)) {
    let $currentDate := adjust-date-to-timezone(current-date(), xs:dayTimeDuration("PT0H"))
    let $date := xs:dateTime($entry/atom:published)
    let $dateLine :=
        if (xs:date($date) eq $currentDate) then
            xs:time($date)
        else
            $date
    return
        <li xmlns="http://www.w3.org/1999/xhtml">
            <span class="tw-thumb">
                <img src="{$entry/atom:link[@rel = 'image']/@href}" height="48" width="48"/>
            </span>
            <span class="tw-content">
                {tc:parse-content($entry/atom:content/node())}
                <span class="tw-date">{$dateLine}</span>
            </span>
        </li>
};

(: scan a set of HTML option elements and select the one whose value matches
   the $select argument :)
declare function tc:set-options($select as xs:string, $options as element(html:option)+) {
    for $opt in $options 
    return
        element { node-name($opt) } {
            if ($opt/@value eq $select) then
                attribute selected { "true" }
            else
                (),
            $opt/@*, $opt/node()
        }
};

(: twitter.xql passes the timeline feed in request attribute "twitter.feed" :)
let $feed := (.//atom:feed)
return
    <html xmlns="http://www.w3.org/1999/xhtml">
        <head>
            <style type="text/css">
                body {{ margin: 0; font-family: "Bitstream Vera Sans", sans-serif; }}
                #container {{ margin: 20px auto; width: 620px; }}
                p {{ font-size: smaller; }}
                ul {{ list-style: none; padding: 0;}}
                .twitter li {{ position: relative; padding: 10px 0; }}
                .tw-thumb {{ height: 50px; display: block; 
                    position: absolute; left: 0; overflow: hidden;
                }}
                .tw-content {{ display: block; min-height: 50px; margin-left: 65px;
                    line-height: 1.25em;
                }}
                .tw-body {{ display: block; }}
                .tw-date {{ display: block; font-size: small; color: #C0C0C0; padding-top: 4px;
                    font-family: "Georgia", serif;
                }}
                .username {{ font-weight: bold; }}
                a {{ text-decoration: none; }}
            </style>
        </head>
        <body>
            <div id="container">
                <p>To view the friends timeline, edit twitter.xql and set variable
                    $tc:login to a valid twitter user/password.</p>
                <ul class="twitter">
                {
                    if ($feed) then
                        if ($feed instance of xs:string) then
                            <li>Twitter reported an error: {$feed}</li>
                        else
                            for $entry in $feed/atom:entry
                            order by xs:dateTime($entry/atom:published) descending
                            return
                                tc:print-entry($entry)
                    else ()
                }
                </ul>
            </div>
        </body>
    </html>