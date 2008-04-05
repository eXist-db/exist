xquery version "1.0";

declare namespace svnu="http://exist-db.org/svnutil";

import module namespace svn="http://exist-db.org/xquery/svn"
at "java:org.exist.xquery.modules.svn.SVNModule";

declare option exist:serialize "media-type=text/html";

declare variable $USER := "anonymous";
declare variable $PASS := "anonymous";

declare variable $CATEGORIES :=
    <messages>
        <cat abbrev="[bugfix]">Bug Fixes</cat>
        <cat abbrev="[feature]">Features</cat>
        <cat abbrev="[documentation]">Documentation</cat>
    </messages>
;
 
declare variable $URIS :=
    <uris>
        <uri description="1.2 stable branch">https://exist.svn.sourceforge.net/svnroot/exist/branches/eXist-stable-1.2</uri>
        <uri description="1.3 development branch">https://exist.svn.sourceforge.net/svnroot/exist/trunk/eXist</uri>
    </uris>
;

declare function svnu:translate-category($abbrev as xs:string) as xs:string {
    let $verbose := $CATEGORIES/cat[@abbrev = $abbrev]/text()
    return
        if ($verbose) then string($verbose)
        else $abbrev
};

declare function svnu:to-html($entries as element(entry)*) as element() {
    <div class="svnlog">
    {
        for $entry in $entries
        order by xs:integer($entry/@rev) descending
        return
            <div class="svnentry">
                <div class="svnrev">
                <a href="http://exist.svn.sourceforge.net/exist/?rev={$entry/@rev}&amp;view=rev">
                {string($entry/@rev)}</a> 
                by <span class="svnauthor">{string($entry/@author)}</span>
                at {string($entry/@date)}
                </div>
                <p class="svncomment">{$entry/message/text()}</p>
            </div>
    }
    </div>
};

declare function svnu:print-by-category($log as element(log)) as item()* {
    let $categories :=
        distinct-values(
            for $message in $log/entry/message
            return text:groups($message/text(), "^(\[[^\]]+\])")[2]
        )
    for $cat in $categories
    where $cat != '[ignore]'
    return
        <div class="svncat">
            <h1>{svnu:translate-category($cat)}</h1>
            {svnu:to-html($log/entry[starts-with(message, $cat)])}
        </div>
};

<book>
    <bookinfo>
        <graphic fileref="logo.jpg"/>
        <productname>Open Source Native XML Database</productname>
        <title>SVN Log</title>
        <link rel="stylesheet" type="text/css" href="styles/svn.css"/>
        <author>
            <firstname>Wolfgang M.</firstname>
            <surname>Meier</surname>
        </author>
    </bookinfo>

    <xi:include xmlns:xi="http://www.w3.org/2001/XInclude" href="sidebar.xml"/>
    
    <chapter>
        <title>SVN Log for trunk</title>
        
        <div id="svnform">
            <form action="svnlog.xq" method="GET">
                <label for="sortby">Sort by </label>
                <select name="sort">
                    <option value="revision">Revision</option>
                    <option value="category">Category</option>
                </select>
                <select name="uri">
                {
                    for $uri in $URIS/uri
                    return
                        <option value="{$uri}">{string($uri/@description)}</option>
                }
                </select>
                <input type="submit" value="Go" />
            </form>
        </div>
        {
            let $sortby := request:get-parameter("sort", "revision")
            let $uri := request:get-parameter("uri", $URIS/uri[1]/text())
            let $log := collection("/db/svn")/log[@uri = $uri]
            return (
                <h2>{string($URIS/uri[. = $uri]/@description)}</h2>,
                if ($sortby eq "category") then
                    svnu:print-by-category($log)
                else
                    svnu:to-html($log/entry)
            )
        }
    </chapter>
</book>