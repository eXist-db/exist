module namespace install="http://exist-db.org/xquery/install-tools";

import module namespace xdb="http://exist-db.org/xquery/xmldb";

declare function install:header() as element()+ {
    <xf:model xmlns:xf="http://www.w3.org/2002/xforms">
        <xf:instance>
            <action>
                <panel>install</panel>
                <modules/>
            </action>
        </xf:instance>
        <xf:instance id="modules">
            <modules>
                <module id="admin">DB Admin Web Application</module>
                <module id="sandbox">XQuery Sandbox</module>
                <!--module id="functions">Function Documentation</module-->
            </modules>
        </xf:instance>
        <xf:submission id="exec" method="get" action="admin.xql" replace="all"/>
    </xf:model>,
    <style type="text/css">
        .report {{ border-top: 1px solid #777777; }}
        .actions {{ font-size: 77%; }}
        .xforms-submit {{ display: block; margin-top: 1em; }}
    </style>
};

declare function install:main() as element() {
    <div xmlns:xf="http://www.w3.org/2002/xforms" class="panel">
        <h1>Tool Installation</h1>
        
        <p>Use this module to install selected XQuery tool apps into
            the database. The tools will be stored into the db collection
            <b>/db/www</b>.</p>
        <p>Installing the sandbox or the admin tool into the database is
            useful if you want to use eXist in a basic, "stand-alone" setup
            without keeping web resources in the filesystem.</p>
        {
            let $modules := tokenize(request:get-parameter("modules", ()), ' ')
            return
                if (exists($modules)) then
                    let $null := install:create-collection("/db", "www")
                    let $dir := install:webapp-home()
                    return
                        <div class="report">
                            <p>Tools have been installed. You should be able to access
                            them using the following links:</p>
                            <ul>
                                <li><a href="http://localhost:8080/exist/tools/admin/">Admin</a></li>
                                <li><a href="http://localhost:8080/exist/tools/sandbox/">XQuery Sandbox</a></li>
                            </ul>
                            <p>Installation report:</p>
                            <ul class="actions">
                            { install:scripts($dir) }
                            {
                               for $module in $modules
                               return
                                   install:module($module, $dir)
                            }
                            </ul>
                        </div>
               else (
                    <xf:select ref="modules" appearance="full">
                        <xf:label><h2>Modules:</h2></xf:label>
                        <xf:itemset nodeset="instance('modules')//module">
                            <xf:label ref="."/>
                            <xf:value ref="@id"/>
                        </xf:itemset>
                    </xf:select>,
                    <xf:submit submission="exec">
                        <xf:label>Install</xf:label>
                    </xf:submit>
                )
        }
    </div>
};

declare function install:webapp-home() {
    let $home := system:get-exist-home()
    let $pathSep := util:system-property("file.separator")
    return 
        if (doc-available(concat("file:///", $home, "/webapp/index.xml"))) then
            concat($home, $pathSep, "webapp")
        else if(ends-with($home, "WEB-INF")) then
            substring-before($home, "WEB-INF")
        else
            concat($home, $pathSep, "webapp")
};

declare function install:create-collection($parent as xs:string, $collection as xs:string) {
    let $r := xdb:create-collection($parent, $collection)
    return
        <li>Created collection {$r}</li>
};

declare function install:store-files($collection as xs:string, $home as xs:string, $patterns as xs:string, $mimeType as xs:string?) as element()*
{
    let $stored := 
        if ($mimeType) then 
            xdb:store-files-from-pattern($collection, $home, $patterns, $mimeType)
        else
            xdb:store-files-from-pattern($collection, $home, $patterns)
    for $doc in $stored return
        <li>Uploaded: {$doc}</li>
};

declare function install:scripts($dir as xs:string) {
    install:create-collection("/db/www", "scripts"),
    install:store-files("/db/www/scripts", $dir, "scripts/*.js", "application/x-javascript"),
    install:create-collection("/db/www/scripts", "yui"),
    install:store-files("/db/www/scripts/yui", $dir, "scripts/yui/*.js", "application/x-javascript"),
    install:store-files("/db/www/scripts/yui", $dir, "scripts/yui/*.css", "text/css"),
    install:create-collection("/db/www", "xforms"),
    install:create-collection("/db/www/xforms", "xsltforms"),
    install:store-files("/db/www/xforms/xsltforms", $dir, "xforms/xsltforms/*.js", "application/x-javascript"),
    install:store-files("/db/www/xforms/xsltforms", $dir, "xforms/xsltforms/*.css", "text/css"),
    install:store-files("/db/www/xforms/xsltforms", $dir, "xforms/xsltforms/*.xsl", "text/xml"),
    install:store-files("/db/www/xforms/xsltforms", $dir, "xforms/xsltforms/*.gif", "image/gif"),
    install:store-files("/db/www/xforms/xsltforms", $dir, "xforms/xsltforms/message*", "text/plain")
};

declare function install:admin($dir as xs:string) {
    install:create-collection("/db/www", "admin"),
    install:store-files("/db/www/admin", $dir, "admin/*.xql", "application/xquery"),
    install:store-files("/db/www/admin", $dir, "admin/*.jpg", "image/jpeg"),
    install:store-files("/db/www/admin", $dir, "admin/*.xqm", "application/xquery"),
    install:store-files("/db/www/admin", $dir, "admin/*.css", "text/css"),
    install:create-collection("/db/www/admin", "scripts"),
    install:store-files("/db/www/admin/scripts", $dir, "admin/scripts/*.js", "application/x-javascript"),
    install:create-collection("/db/www/admin", "styles"),
    install:store-files("/db/www/admin/styles", $dir, "admin/styles/*.css", "text/css")
};

declare function install:sandbox($dir as xs:string) {
    install:create-collection("/db/www", "sandbox"),
    install:store-files("/db/www/sandbox", $dir, "sandbox/*.xql", "application/xquery"),
    install:create-collection("/db/www/sandbox", "scripts"),
    install:store-files("/db/www/sandbox/scripts", $dir, "sandbox/scripts/*.js", "application/x-javascript"),
    install:create-collection("/db/www/sandbox", "styles"),
    install:store-files("/db/www/sandbox/styles", $dir, "sandbox/styles/*.css", "text/css")
};

(:
declare function install:functions($dir as xs:string) {
    install:create-collection("/db/www", "functions"),
    install:store-files("/db/www/functions", $dir, "xquery/functions.xql", "application/xquery"),
    install:store-files("/db/www/functions", $dir, "xquery/docsetup.xql", "application/xquery"),
    install:store-files("/db/www/functions", $dir, "xquery/sidebar.xml", "text/xml"),
    install:store-files("/db/www/functions", $dir, "admin/install/functions-controller.xql", "application/xquery"),
    xdb:rename("/db/www/functions", "functions-controller.xql", "controller.xql"),
    install:create-collection("/db/www/functions", "styles"),
    install:store-files("/db/www/functions/styles", $dir, "xquery/styles/functions.css", "text/css"),
    install:store-files("/db/www/functions/styles", $dir, "stylesheets/db2xhtml.xsl", "text/xml"),
    install:create-collection("/db/www/functions", "resources"),
    install:store-files("/db/www/functions/resources", $dir, "resources/*.gif", "image/gif"),
    install:store-files("/db/www/functions/resources", $dir, "resources/*.png", "image/png"),
    install:store-files("/db/www/functions/resources", $dir, "resources/*.jpg", "image/jpeg")
};
:)

declare function install:module($module as xs:string, $dir as xs:string) {
    if ($module eq 'admin') then
        install:admin($dir)
    else if ($module eq 'sandbox') then
        install:sandbox($dir)
    (:
    else if ($module eq 'functions') then
        install:functions($dir) 
    :)
    else
        ()
};