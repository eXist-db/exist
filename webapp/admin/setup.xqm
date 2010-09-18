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

declare namespace xproc="http://xproc.net/xproc";

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
		                    else if($action eq "Import Files") then
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
        ),
    $webapp := if (doc-available(concat("file:///", $home, "/webapp/index.xml"))) then
        (
            concat($home, $pathSep, "webapp")
        )
        else if(ends-with($home, "WEB-INF")) then
        (
            substring-before($home, "WEB-INF")
        )
        else
        (
            concat($home, $pathSep, "webapp")
        )
        (: ,
        $betterform := concat($home, "/extensions/betterform/src/main/xrx/timetracker/")
        :)
    return (
                setup:create-collection("/db/system/config", "db"),
                setup:create-collection("/db/system/config/db", "shakespeare"),
                setup:store-files("/db/system/config/db/shakespeare", $dir, 
                    "shakespeare/collection.xconf", "text/xml"),
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
                setup:create-collection("/db/system/config/db", "mods"),
                setup:store-files("/db/system/config/db/mods", $dir, "mods/collection.xconf", "text/xml"),
                setup:create-collection("/db", "mods"),
                setup:create-collection("/db/mods", "eXist"),
                setup:create-collection("/db/mods", "samples"),
                setup:create-collection("/db/mods", "users"),
                setup:create-group("biblio.users"),
                setup:set-collection-group("/db/mods/users", "biblio.users"),
                setup:set-collection-group-writable("/db/mods/users"),
                setup:store-files("/db/mods/samples", $dir, "mods/*.xml", "text/xml"),
                setup:store-files("/db/mods/eXist", $dir, "mods/eXist/*.xml", "text/xml"),
                setup:store-files("/db", $dir, "*.xml", "text/xml"),
                setup:create-collection("/db/system/config/db", "mondial"),
                setup:store-files("/db/system/config/db/mondial", $dir, "mondial.xconf", "text/xml"),
                setup:create-collection("/db/system/config/db", "xmlad"),
                setup:store-files("/db/system/config/db/xmlad", $dir, "acronyms.xconf", "text/xml"),

                (: Storing Testing :)
                setup:create-collection("/db", "testing"),
                setup:store-files("/db/testing", $webapp, "testing/*.xml", "text/html"),
                setup:store-files("/db/testing", $webapp, "testing/*.xq*", "application/xquery"),
                setup:create-collection("/db/testing", "examples"),
                setup:store-files("/db/testing/examples", $webapp, "testing/examples/*.xml", "text/html"),
                setup:store-files("/db/testing/examples", $webapp, "testing/examples/*.xq*", "application/xquery"),

                (: Storing XProc :)
                setup:create-collection("/db", "xproc"),
                setup:create-collection("/db/xproc", "unit-test"),
                setup:create-collection("/db/xproc", "xml"),
                setup:create-collection("/db/xproc", "xslt"),
                setup:create-collection("/db/xproc", "result"),
                setup:create-collection("/db/xproc", "examples"),
                setup:create-collection("/db/xproc", "pipelines"),
                setup:store-files("/db/xproc/unit-test", $webapp, "xproc/unit-test/*.xml", "text/xml"),
                setup:store-files("/db/xproc/unit-test", $webapp, "xproc/unit-test/*.xql", "application/xquery"),
                setup:store-files("/db/xproc/unit-test", $webapp, "xproc/unit-test/*.xsl", "text/xml"),

                setup:store-files("/db/xproc/pipelines", $webapp, "xproc/pipelines/*.xml", "text/xml"),
                setup:store-files("/db/xproc/xml", $webapp, "xproc/xml/*.xml", "text/xml"),
                setup:store-files("/db/xproc/xslt", $webapp, "xproc/xml/*.xsl", "text/xml"),

                setup:store-files("/db/xproc/examples", $webapp, "xproc/examples/*.xproc", "application/xml+xproc"),
                setup:store-files("/db/xproc/examples", $webapp, "xproc/examples/*.xml", "text/xml"),

                setup:store-files("/db/xproc/examples", $webapp, "xproc/examples/*.xql", "application/xquery"),
                setup:store-files("/db/xproc", $webapp, "xproc/*.xml", "text/xml"),
                setup:store-files("/db/xproc", $webapp, "xproc/*.xql", "application/xquery") (:,

                (: Storing XForms in the db won't work right now :)

                setup:create-collection("/db", "xforms"),
                setup:store-files("/db/xforms", $webapp, "xforms/*.xml", "text/html"),
                setup:store-files("/db/xforms", $webapp, "xforms/*.xq*", "application/xquery"),
                setup:create-collection("/db/xforms", "xsltforms"),
                setup:create-collection("/db/xforms", "data"),
                setup:store-files("/db/xforms/xsltforms", $webapp, "xforms/xsltforms/*.properties", "text/plain"),
                setup:store-files("/db/xforms/xsltforms", $webapp, "xforms/xsltforms/*.png", "image/png"),
                setup:store-files("/db/xforms/xsltforms", $webapp, "xforms/xsltforms/*.gif", "image/gif"),
                setup:store-files("/db/xforms/xsltforms", $webapp, "xforms/xsltforms/*.xsl","text/xml"),
                setup:store-files("/db/xforms/xsltforms", $webapp, "xforms/xsltforms/*.js","application/x-javascript"),
                setup:store-files("/db/xforms/xsltforms", $webapp, "xforms/xsltforms/*.css","text/css")
                :)


				(: Storing betterFORM XRX sample application :)
				(:
				setup:create-collection("/db", "betterform"),
				setup:create-collection("/db/betterform", "apps"),
				setup:create-collection("/db/betterform/apps", "timetracker"),				
				setup:store-files("/db/betterform/apps/timetracker", $betterform, "*.xql","application/xquery"),
				setup:store-files("/db/betterform/apps/timetracker", $betterform, "*.html","text/html"),
				setup:store-files("/db/betterform/apps/timetracker", $betterform, "*.xml","application/xml"),
				setup:create-collection("/db/betterform/apps/timetracker", "data"),
				setup:store-files("/db/betterform/apps/timetracker/data", $betterform, "data/*.xml", "application/xml"),
				setup:create-collection("/db/betterform/apps/timetracker/data", "task"),
				setup:store-files("/db/betterform/apps/timetracker/data/task", $betterform, "data/task/*.xml","application/xml"),
				setup:create-collection("/db/betterform/apps/timetracker/", "edit"),
				setup:store-files("/db/betterform/apps/timetracker/edit", $betterform, "edit/*.xql","application/xquery"),
				setup:create-collection("/db/betterform/apps/timetracker/", "lucene"),
				setup:store-files("/db/betterform/apps/timetracker/lucene", $betterform, "lucene/*.xconf","application/xml"),
				setup:create-collection("/db/betterform/apps/timetracker/", "reports"),
				setup:store-files("/db/betterform/apps/timetracker/reports", $betterform, "reports/*.xql","application/xquery"),
				setup:create-collection("/db/betterform/apps/timetracker/", "resources"),
				setup:store-files("/db/betterform/apps/timetracker/resources", $betterform, "resources/*.css","text/css"),
				setup:create-collection("/db/betterform/apps/timetracker/resources", "images"),
				setup:store-files("/db/betterform/apps/timetracker/resources/images", $betterform, "resources/images/*.gif","image/gif"),
				setup:store-files("/db/betterform/apps/timetracker/resources/images", $betterform, "resources/images/*.png","image/png"),
				setup:store-files("/db/betterform/apps/timetracker/resources/images", $betterform, "resources/images/*.jpg","image/jpeg"),  
				setup:create-collection("/db/betterform/apps/timetracker/", "search"),
				setup:store-files("/db/betterform/apps/timetracker/search", $betterform, "search/*.xql","application/xquery"),
				setup:store-files("/db/betterform/apps/timetracker/search", $betterform, "search/*.html","text/html"),
				setup:create-collection("/db/betterform/apps/timetracker/", "update"),
				setup:store-files("/db/betterform/apps/timetracker/update", $betterform, "update/*.xql","application/xquery"),
				setup:create-collection("/db/betterform/apps/timetracker/", "views"),
				setup:store-files("/db/betterform/apps/timetracker/views", $betterform, "views/*.xql","application/xquery"),
				setup:store-files("/db/betterform/apps/timetracker/views", $betterform, "views/*.html","text/html"),
				setup:store-files("/db/betterform/apps/timetracker/views", $betterform, "views/*.xsl","application/xml")
                :)
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

declare function setup:create-group($group as xs:string) as element()
{
    let $result := xdb:create-group($group) return
        if($result)then (
            <li class="high">Created group: {$group}</li>
        ) else (
            <li class="error">Failed to create group: {$group}</li>
        )
};

declare function setup:set-collection-group($collection as xs:string, $group as xs:string) as element() {
    let $null := xdb:set-collection-permissions($collection, xdb:get-owner($collection), $group, xdb:get-permissions($collection)) return
        <li class="high">Set collection '{$collection}' group to '{$group}'</li>
};

declare function setup:set-collection-group-writable($collection as xs:string) as element() {

    let $current-permissions := xdb:get-permissions($collection),
    $new-permissions := xdb:string-to-permissions(replace(xdb:permissions-to-string($current-permissions), "(.{3}).{2}(.{4})", "$1rw$2")) return

        let $null := xdb:set-collection-permissions($collection, xdb:get-owner($collection), xdb:get-group($collection), $new-permissions) return
            <li class="high">Made collection '{$collection}' group writable.</li>
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
        
        <p><input type="submit" name="action" value="Import Files"/>
        <input type="submit" name="action" value="Skip"/></p>
        <input type="hidden" name="panel" value="setup"/>
    </form>
};

declare function setup:report() as element()
{
    <p>Files have been loaded. You can now go to the
    <a href="../examples.xml">examples page</a>.</p>
};
