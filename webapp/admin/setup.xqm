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
declare namespace file="http://exist-db.org/xquery/file";

declare namespace xproc="http://xproc.net/xproc";

declare function setup:main() as element()
{
    <div class="panel">
        <div class="panel-head">Install betterFORM Examples</div>
        {
let $action := request:get-parameter("action", ()) return
		if($action) then
        (
            if($action eq "Import Files") then
            (
                setup:importLocal()
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
        ,
    $betterform-home := if (ends-with($home, "WEB-INF")) then
        (
            concat( substring-before($home, "WEB-INF"), "/betterform/samples")
        )
        else
        (
            concat($home, "/extensions/betterform/main")
        ),

    $betterform-xrx := concat($betterform-home, "/xrx/timetracker/"),
    $betterform-xforms := concat($betterform-home, "/xforms")

    return (
                setup:create-collection("/db/system/config", "db"),

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
                setup:store-files("/db/xproc", $webapp, "xproc/*.xql", "application/xquery"),

                (: Storing XForms in the db won't work right now :)
                (:
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

				setup:create-collection("/db", "betterform"),
				setup:create-collection("/db/betterform", "apps"),
				setup:create-collection("/db/betterform/apps", "timetracker"),				
				setup:store-files("/db/betterform/apps/timetracker", $betterform-xrx, "*.xql","application/xquery"),
				setup:store-files("/db/betterform/apps/timetracker", $betterform-xrx, "*.html","text/html"),
				setup:store-files("/db/betterform/apps/timetracker", $betterform-xrx, "*.xml","application/xml"),
				setup:create-collection("/db/betterform/apps/timetracker", "data"),
				setup:store-files("/db/betterform/apps/timetracker/data", $betterform-xrx, "data/*.xml", "application/xml"),
				setup:create-collection("/db/betterform/apps/timetracker/data", "task"),
				setup:store-files("/db/betterform/apps/timetracker/data/task", $betterform-xrx, "data/task/*.xml","application/xml"),
				setup:create-collection("/db/betterform/apps/timetracker/", "edit"),
				setup:store-files("/db/betterform/apps/timetracker/edit", $betterform-xrx, "edit/*.xql","application/xquery"),
				setup:create-collection("/db/betterform/apps/timetracker/", "lucene"),
				setup:store-files("/db/betterform/apps/timetracker/lucene", $betterform-xrx, "lucene/*.xconf","application/xml"),
				setup:create-collection("/db/betterform/apps/timetracker/", "reports"),
				setup:store-files("/db/betterform/apps/timetracker/reports", $betterform-xrx, "reports/*.xql","application/xquery"),
				setup:create-collection("/db/betterform/apps/timetracker/", "resources"),
				setup:store-files("/db/betterform/apps/timetracker/resources", $betterform-xrx, "resources/*.css","text/css"),
				setup:create-collection("/db/betterform/apps/timetracker/resources", "images"),
				setup:store-files("/db/betterform/apps/timetracker/resources/images", $betterform-xrx, "resources/images/*.gif","image/gif"),
				setup:store-files("/db/betterform/apps/timetracker/resources/images", $betterform-xrx, "resources/images/*.png","image/png"),
				setup:store-files("/db/betterform/apps/timetracker/resources/images", $betterform-xrx, "resources/images/*.jpg","image/jpeg"),
				setup:create-collection("/db/betterform/apps/timetracker/", "search"),
				setup:store-files("/db/betterform/apps/timetracker/search", $betterform-xrx, "search/*.xql","application/xquery"),
				setup:store-files("/db/betterform/apps/timetracker/search", $betterform-xrx, "search/*.html","text/html"),
				setup:create-collection("/db/betterform/apps/timetracker/", "update"),
				setup:store-files("/db/betterform/apps/timetracker/update", $betterform-xrx, "update/*.xql","application/xquery"),
				setup:create-collection("/db/betterform/apps/timetracker/", "views"),
				setup:store-files("/db/betterform/apps/timetracker/views", $betterform-xrx, "views/*.xql","application/xquery"),
				setup:store-files("/db/betterform/apps/timetracker/views", $betterform-xrx, "views/*.html","text/html"),
				setup:store-files("/db/betterform/apps/timetracker/views", $betterform-xrx, "views/*.xsl","application/xml"),


				(: Storing betterFORM dashboard :)
				setup:create-collection("/db/betterform", "utils"),
                setup:store-files("/db/betterform/utils", $betterform-xforms, "utils/*.xql","application/xquery"),

				(: Storing betterFORM XForms reference forms :)
				setup:store-files("/db/betterform", $betterform-xforms, "*.xhtml","application/xhtml+xml"),
				setup:create-collection("/db/betterform", "forms"),
				setup:create-collection("/db/betterform/forms", "reference"),
				setup:store-files("/db/betterform/forms/reference", $betterform-xforms, "reference/*.xhtml","application/xhtml+xml"),
				setup:store-files("/db/betterform/forms/reference", $betterform-xforms, "reference/*.css","text/css"),
				setup:store-files("/db/betterform/forms/reference", $betterform-xforms, "reference/*.html","text/html"),
				setup:create-collection("/db/betterform/forms/reference", "images"),
				setup:store-files("/db/betterform/forms/reference/images", $betterform-xforms, "reference/images/*.png","image/png"),
				setup:store-files("/db/betterform/forms/reference/images", $betterform-xforms, "reference/images/*.gif","image/gif"),
				setup:create-collection("/db/betterform/forms/reference", "resources"),
				setup:store-files("/db/betterform/forms/reference/resources", $betterform-xforms, "reference/resources/*.xhtml","application/xhtml+xml"),
				setup:store-files("/db/betterform/forms/reference/resources", $betterform-xforms, "reference/resources/*.html","text/html"),
				setup:store-files("/db/betterform/forms/reference/resources", $betterform-xforms, "reference/resources/*.css","text/css"),
				setup:store-files("/db/betterform/forms/reference/resources", $betterform-xforms, "reference/resources/*.js","application/x-javascript"),
				setup:store-files("/db/betterform/forms/reference/resources", $betterform-xforms, "reference/resources/*.jpg","image/jpeg"),
				setup:create-collection("/db/betterform/forms", "demo"),
				setup:store-files("/db/betterform/forms/demo", $betterform-xforms, "demo/*.xhtml","application/xhtml+xml"),
				setup:create-collection("/db/betterform/forms/demo", "styles"),
				setup:store-files("/db/betterform/forms/demo/styles", $betterform-xforms, "demo/styles/*.css","text/css"),
				setup:store-files("/db/betterform/forms/demo", $betterform-xforms, "demo/*.xml","application/xml")
    )
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
    if(xdb:group-exists($group))then
    (
        <li class="high">Verified group: {$group} exists</li>
    )
    else
    (
        let $result := xdb:create-group($group, xdb:get-current-user()) return
            if($result)then (
                <li class="high">Created group: {$group}</li>
            ) else (
                <li class="error">Failed to create group: {$group}</li>
            )
    )
};

declare function setup:set-collection-group($collection as xs:string, $group as xs:string) as element() {
    let $null := xdb:set-collection-permissions($collection, xdb:get-owner($collection), $group, xdb:get-permissions($collection)) return
        <li class="high">Set collection '{$collection}' group to '{$group}'</li>
};

declare function setup:set-collection-permissions($collection as xs:string, $permissions as xs:string) as element() {
        let $null := xdb:set-collection-permissions($collection, xdb:get-owner($collection), xdb:get-group($collection), xdb:string-to-permissions($permissions)) return
            <li class="high">Set collection '{$collection}' permissions '{$permissions}'.</li>
};

declare function setup:select() as element()
{
    <form action="{session:encode-url(request:get-uri())}" method="post">
        <p>Load the betterFORM, XProc and related examples into the database. Most of the core examples
        of eXist-db are now available as separate applications via the package repository (see menu 
        entry to the left).</p>

		<input type="hidden" name="local" value="true"/>
        
        <p><input type="submit" name="action" value="Import Files"/>
        <input type="submit" name="action" value="Skip"/></p>
        <input type="hidden" name="panel" value="setup"/>
        <p>(*) Contains data for the betterFORM and xproc examples.</p>
    </form>
};

declare function setup:report() as element()
{
    <p>Files have been loaded. You can now go to the
    <a href="../examples.xql">DemoApps examples page</a>.</p>
};
