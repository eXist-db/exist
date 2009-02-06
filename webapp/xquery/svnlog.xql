xquery version "1.0";

declare namespace svnu="http://exist-db.org/svnutil";

import module namespace svn="http://exist-db.org/xquery/svn"
at "java:org.exist.xquery.modules.svn.SVNModule";

declare option exist:output-size-limit "-1";

declare option exist:serialize "media-type=text/html";

declare variable $USER := "anonymous";
declare variable $PASS := "anonymous";

declare variable $CATEGORIES :=
    <messages>
        <cat abbrev="\[bugfix.*\]">Bug Fixes</cat>
        <cat abbrev="\[feature.*\]">Features</cat>
        <cat abbrev="\[documentation.*\]">Documentation</cat>
        <cat abbrev="\[performance\]">Performance</cat>
        <cat abbrev="\[(lib-)?update\]">Updates</cat>
        <cat abbrev="\[refactor.*\]">Refactoring</cat>
        <cat abbrev="\[test.*\]">Test Suite</cat>
        <cat abbrev="\[general\]">General</cat>
        <cat abbrev="\[build.*\]">Build</cat>
    </messages>
;
 
declare variable $URIS :=
    <uris>
        <uri description="1.2 stable branch">https://exist.svn.sourceforge.net/svnroot/exist/branches/eXist-stable-1.2</uri>
        <uri description="1.3 development branch">https://exist.svn.sourceforge.net/svnroot/exist/trunk/eXist</uri>
    </uris>
;

declare variable $IGNORE := ('[ignore]', '[ingnore]', '[bugtest]', '[comment]', 
    '[improvement]', '[unchecked]', '[cosmetic]');

declare function svnu:translate-category($abbrev as xs:string) as xs:string {
    let $verbose := $CATEGORIES/cat[matches($abbrev, @abbrev)]/text()
    return
        if ($verbose) then string($verbose[1])
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
                <img class="svn-show-files" src="resources/plus.gif"/>
                <ul class="svnpaths" style="display: none;">
                {
                    for $path in $entry/paths/path
                    return
                        <li>
                        {
                            let $file := $path/text()
                            return
                                <a href="http://exist.svn.sourceforge.net/viewvc/exist{$file}?view=markup&amp;pathrev={$entry/@rev}">
                                {$file}
                                </a>
                        }
                        </li>
                }
                </ul>
            </div>
    }
    </div>
};

declare function svnu:print-by-category($log as element(log)) as item()* {
    let $categories :=
        distinct-values(
            for $message in $log/entry/message
            return
                if ($message/text()) then text:groups($message/text(), "^(\[[^\]]+\])")[2] else ()
        )
    let $translated :=
        distinct-values(
            for $cat in $categories where not($cat = $IGNORE) 
            return svnu:translate-category($cat)
        )
    return (
        <ul>
        {
            for $cat at $pos in $translated
            return
                <li><a href="#c{$pos}">{$cat}</a></li>
        }
        </ul>,
        
        for $cat at $pos in $translated
        let $regex := concat('^', $CATEGORIES/cat[. = $cat]/@abbrev)
        return
            <div class="svncat">
                <h1><a name="c{$pos}">{svnu:translate-category($cat)}</a></h1>
                {svnu:to-html($log/entry[matches(message, $regex)])}
            </div>
    )
};

<book>
    <bookinfo>
        <graphic fileref="logo.jpg"/>
        <productname>Open Source Native XML Database</productname>
        <title>SVN Log</title>
        <link rel="stylesheet" type="text/css" href="styles/svn.css"/>
        <script type="text/javascript" src="../scripts/yui/utilities.js"/>
        <script type="text/javascript" src="scripts/svnlog.js"/>
        <author>
            <firstname>Wolfgang M.</firstname>
            <surname>Meier</surname>
        </author>
    </bookinfo>

    <xi:include xmlns:xi="http://www.w3.org/2001/XInclude" href="sidebar.xml"/>
    
    {
        let $sortby := request:get-parameter("sort", "category")
        let $uri := request:get-parameter("uri", $URIS/uri[1]/text())
        return
            <chapter>
                <title>SVN Log for trunk</title>
                
                <div id="svnform">
                    <form action="svnlog.xql" method="GET">
                        <select name="uri">
                        {
                            for $u in $URIS/uri
                            return
                                <option value="{$u}">
                                { if ($u eq $uri) then attribute selected { "true" } else () }
                                {
                                    string($u/@description)
                                }
                                </option>
                        }
                        </select>
                        <label for="sortby"> Sort by </label>
                        <select name="sort">
                            <option value="revision">
                                {if ($sortby eq "revision") then attribute selected { "true" } else ()}
                                Revision
                            </option>
                            <option value="category">
                                {if ($sortby eq "category") then attribute selected { "true" } else ()}
                                Category
                            </option>
                        </select>
                        <input type="submit" value="Refresh" />
                    </form>
                </div>
                {
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
    }
</book>