xquery version "1.0";
(: $Id$ :)
(:
    Module: examples setup
:)

module namespace setup="http://exist-db.org/xquery/admin-interface/setup";

declare namespace xdb="http://exist-db.org/xquery/xmldb";
declare namespace request="http://exist-db.org/xquery/request";
declare namespace session="http://exist-db.org/xquery/session";
declare namespace util="http://exist-db.org/xquery/util";
declare namespace system="http://exist-db.org/xquery/system";

declare function setup:main() as element()
{
    <div class="panel">
        <div class="panel-head">Examples Setup</div>
        {
let $action := request:get-parameter("action", ()) return
		if($action) then
		                (
		                    if($action eq "Import Example Data") then
		                    (
		                        setup:importLocal()
		                    )
		                    else if($action eq "Import Remote Files") then
		                    (
		                        setup:importFromURLs()
		                    )
		                    else
		                    (
		                        setup:report()
		                    )
		                )
		                else
		                (
		                    setup:select()
		                )
 
        }
    </div>
};

declare function setup:importLocal() as element()+ 
{
    let $home := system:get-exist-home(),
    $pathSep := util:system-property("file.separator"),
    $dir := if (doc-available(concat("file:///", $home, "/exist-samples/examples.xml"))) then
        (
            concat($home, $pathSep, "exist-samples")
        )
        else if(ends-with($home, "WEB-INF")) then
        (
            concat(substring-before($home, "WEB-INF"), "samples")
        )
        else
        (
            concat($home, $pathSep, "samples")
        )
    return (
                setup:create-collection("/db", "shakespeare"),
                setup:create-collection("/db/shakespeare", "plays"),
                setup:store-files("/db/shakespeare/plays", $dir,  "shakespeare/*.xml", "text/xml"),
                setup:store-files("/db/shakespeare/plays", $dir, "shakespeare/*.xsl", "text/xml"),
                setup:store-files("/db/shakespeare/plays", $dir, "shakespeare/*.css", "text/css"),
                setup:create-collection("/db", "xinclude"),
                setup:create-collection("/db/xinclude", "styles"),
                setup:store-files("/db/xinclude", $dir, "xinclude/*.xsl", "text/xml"),
                setup:store-files("/db/xinclude", $dir, "xinclude/*.xml", "text/xml"),
                setup:store-files("/db/xinclude", $dir, "xinclude/*.xq", "application/xquery"),
                setup:store-files("/db/xinclude", $dir, "xinclude/*.jpg", "image/jpeg"),
                setup:store-files("/db/xinclude/styles", $dir, "xinclude/styles/*", "text/css"),
                setup:create-collection("/db/xinclude", "scripts"),
                setup:create-collection("/db/xinclude/scripts", "syntax"),
                setup:store-files("/db/xinclude/scripts/syntax", $dir, "xinclude/scripts/syntax/*.js", "application/x-javascript"),
                setup:create-collection("/db", "library"),
                setup:store-files("/db/library", $dir, "*.rdf", "text/xml"),
                setup:create-collection("/db", "mods"),
                setup:store-files("/db/mods", $dir, "mods/*.xml", "text/xml"),
                setup:store-files("/db", $dir, "*.xml", "text/xml"),
                setup:create-collection("/db/system/config", "db"),
                setup:create-collection("/db/system/config/db", "mondial"),
                setup:store-files("/db/system/config/db/mondial", $dir, "mondial.xconf", "text/xml")
    )
};

declare function setup:importFromURLs() as element()+
{
    (
        setup:report(),
        <div class="process">
            <h3>Actions:</h3>
            <ul>
            {
                let $includeXmlad := request:get-parameter("xmlad", ()),
                $includeMondial := request:get-parameter("mondial", ()),
 				$includeLocal := request:get-parameter("local", ()) return
                (
					if($includeLocal) then
					( setup:importLocal()
					)
					else (),
                    if($includeXmlad) then
                    (
                        setup:create-collection("/db", "xmlad"),
                        setup:load-URL("/db/xmlad", xs:anyURI("http://surfnet.dl.sourceforge.net/sourceforge/xmlad/xmlad.xml"), "xmlad.xml")
                    )else (),
                    if($includeMondial) then
                    (
                        setup:create-collection("/db", "mondial"),
                        setup:load-URL("/db/mondial", xs:anyURI("http://www.dbis.informatik.uni-goettingen.de/Mondial/mondial-europe.xml"), "mondial.xml")
                    )else ()
                )
            }
            </ul>
        </div>
    )
};

declare function setup:load-URL($collection as xs:string, $url as xs:anyURI, $docName as xs:string) as element()
{
    let $x := xdb:store($collection, $docName, $url) return
        <li>File {$docName} imported from url: {$url}</li>
};

declare function setup:store-files($collection as xs:string, $home as xs:string, $patterns as xs:string, $mimeType as xs:string) as element()*
{
    let $stored := xdb:store-files-from-pattern($collection, $home, $patterns, $mimeType)
    for $doc in $stored return
        <li>Uploaded: {$doc}</li>
};

declare function setup:create-collection($parent as xs:string, $name as xs:string) as element()
{
    let $col := xdb:create-collection($parent, $name) return
        <li class="high">Created collection: {$col}</li>
};


declare function setup:select() as element()
{
    <form action="{session:encode-url(request:get-uri())}" method="post">
        <p>eXist ships with a number of XQuery examples. Some of these
        require certain documents to be stored in the database. The XQuery examples also use some 
		XML data not included with the distribution which can be downloaded by selecting
		the checkboxes below.</p>

		<input type="checkbox" name="local" checked="true"/>
		eXist-db shipped files<br/>
        
        <input type="checkbox" name="xmlad"/>
        <a href="http://sourceforge.net/projects/xmlad/">The XML Acronym Demystifier</a>
        (approx. 384K)<br/>
        
        <input type="checkbox" name="mondial"/>
        <a href="http://dbis.informatik.uni-goettingen.de/Mondial/" checked="true">The Mondial Database (Europe)</a>
        (approx. 410K)
        
        <p><input type="submit" name="action" value="Import Remote Files"/>
        <input type="submit" name="action" value="Skip"/></p>
        <input type="hidden" name="panel" value="setup"/>
    </form>
};

declare function setup:report() as element()
{
    <p>Files have been loaded. You can now go to the
    <a href="../examples.xml">examples page</a>.</p>
};
