(:
    Module: examples setup
:)
module namespace setup="http://exist-db.org/xquery/admin-interface/setup";

declare namespace xdb="http://exist-db.org/xquery/xmldb";
declare namespace request="http://exist-db.org/xquery/request";
declare namespace util="http://exist-db.org/xquery/util";

declare variable $setup:shakesPat {
    ( "shakespeare/*.xml", "shakespeare/*.xsl" )
};

declare variable $setup:xincludePat { "xinclude/*.xml" };

declare function setup:main() as element() {
    <div class="panel">
        <div class="panel-head">Examples Setup</div>
        {
            let $action := request:request-parameter("action", ())
            return
                if($action) then
                    if($action eq "Import Sample Data") then
                        setup:importLocal()
                    else
                        setup:importFromURLs()
                else
                    setup:page1()
        }
    </div>
};

declare function setup:importLocal() as element()+ {
    let $home := 
            concat(
                util:system-property("exist.home"), util:system-property("file.separator"),
                "samples")
    return (
        <div class="actions">
            <ul>
            {
                setup:create-collection("/db", "shakespeare"),
                setup:create-collection("/db/shakespeare", "plays"),
                setup:store-files("/db/shakespeare/plays", $home, $setup:shakesPat),
                setup:create-collection("/db", "xinclude"),
                setup:store-files("/db/xinclude", $home, $setup:xincludePat),
                setup:store-files("/db", $home, ("*.xml", "*.rdf"))
            }
            </ul>
        </div>,
        setup:page2()
    )
};

declare function setup:importFromURLs() as element()+ {
    <div class="actions">
        <ul>
        {
            setup:create-collection("/db", "xmlad"),
            setup:load-URL("/db/xmlad",
                "http://belnet.dl.sourceforge.net/sourceforge/xmlad/xmlad.xml",
                "xmlad.xml")
        }
        </ul>
    </div>
};

declare function setup:load-URL($collection, $url, $docName) as element() {
    xdb:store($collection, $docName, xs:anyURI($url)),
    <li>File xmlad.xml imported from url: {$url}</li>
};

declare function setup:store-files($collection, $home, $patterns) as element()* {
    let $stored := xdb:store-files-from-pattern($collection, $home, $patterns)
    for $doc in $stored
    return
        <li>Uploaded: {$doc}</li>
};

declare function setup:create-collection($parent, $name) as element() {
    let $col := xdb:create-collection($parent, $name)
    return
        <li class="high">Created collection: {util:collection-name($col)}</li>
};

declare function setup:page1() as element()+ {
    <p>eXist ships with a number of XQuery and other examples. Some of these
        require certain documents to be stored in the database. Clicking on the button 
        below will import the required data.</p>,
    <form action="{request:encode-url(request:request-uri())}" method="POST">
        <input type="submit" name="action" value="Import Sample Data"/>
        <input type="hidden" name="panel" value="setup"/>
    </form>
};

declare function setup:page2() as element()+ {
    <p>The XQuery examples also use some XML data not included with the distribution.
    I can try to download the corresponding documents. Do you want me to do so?</p>,
    <form action="{request:encode-url(request:request-uri())}" method="POST">
        <input type="submit" name="action" value="Import Remote Files"/>
        <input type="hidden" name="panel" value="setup"/>
    </form>
};
