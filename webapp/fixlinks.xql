(:
    For the demo server: rewrite all links to documentation pages and
    replace them with the static files on exist-db.org
:)
xquery version "1.0";

declare namespace xhtml="http://www.w3.org/1999/xhtml";

declare option exist:serialize "method=xhtml media-type=text/html";

(:
    XML=`ls -1 *.xml`; for F in $XML; do echo "<map url='$F' replace='$F'/>"; done
:)
declare variable $local:MAP :=
    <mapping>
        <map url='acknowledge.xml' replace='acknowledge.html'/>
        <map url='ant-tasks.xml' replace='ant-tasks.html'/>
        <map url='atompub.xml' replace='atompub.html'/>
        <map url='backup.xml' replace='backup.html'/>
        <map url='beginners-guide-to-xrx-v4.xml' replace='beginners-guide-to-xrx-v4.html'/>
        <map url='building.xml' replace='building.html'/>
        <map url='client.xml' replace='client.html'/>
        <map url='community.xml' replace='community.html'/>
        <map url='config-cache.xml' replace='config-cache.html'/>
        <map url='config-compression.xml' replace='config-compression.html'/>
        <map url='config-context.xml' replace='config-context.html'/>
        <map url='config-counter.xml' replace='config-counter.html'/>
        <map url='config-datetime.xml' replace='config-datetime.html'/>
        <map url='config-example.xml' replace='config-example.html'/>
        <map url='config-file.xml' replace='config-file.html'/>
        <map url='config-fluent.xml' replace='config-fluent.html'/>
        <map url='config-openid.xml' replace='config-openid.html'/>
        <map url='configuration.xml' replace='configuration.html'/>
        <map url='config-versioning.xml' replace='config-versioning.html'/>
        <map url='config-xqdoc.xml' replace='config-xqdoc.html'/>
        <map url='config-xslt.xml' replace='config-xslt.html'/>
        <map url='credits.xml' replace='credits.html'/>
        <map url='debugger.xml' replace='debugger.html'/>
        <map url='deployment.xml' replace='deployment.html'/>
        <map url='devguide_codereview.xml' replace='devguide_codereview.html'/>
        <map url='devguide_indexes.xml' replace='devguide_indexes.html'/>
        <map url='devguide_log4j.xml' replace='devguide_log4j.html'/>
        <map url='devguide_manifesto.xml' replace='devguide_manifesto.html'/>
        <map url='devguide_rest.xml' replace='devguide_rest.html'/>
        <map url='devguide_soap.xml' replace='devguide_soap.html'/>
        <map url='devguide.xml' replace='devguide.html'/>
        <map url='devguide_xmldb.xml' replace='devguide_xmldb.html'/>
        <map url='devguide_xmlrpc.xml' replace='devguide_xmlrpc.html'/>
        <map url='devguide_xquery.xml' replace='devguide_xquery.html'/>
        <map url='documentation.xml' replace='documentation.html'/>
        <map url='download.xml' replace='download.html'/>
        <map url='eclipse.xml' replace='eclipse.html'/>
        <map url='exist-stack.xml' replace='exist-stack.html'/>
        <map url='extensions.xml' replace='extensions.html'/>
        <map url='facts.xml' replace='facts.html'/>
        <map url='ftlegacy.xml' replace='ftlegacy.html'/>
        <map url='function_modules.xml' replace='function_modules.html'/>
        <map url='header.xml' replace='header.html'/>
        <map url='indexing.xml' replace='indexing.html'/>
        <map url='index.xml' replace='index.html'/>
        <map url='installing-exist-on-amazon-ec2.xml' replace='installing-exist-on-amazon-ec2.html'/>
        <map url='jmx.xml' replace='jmx.html'/>
        <map url='journal.xml' replace='journal.html'/>
        <map url='kwic.xml' replace='kwic.html'/>
        <map url='ldap-security.xml' replace='ldap-security.html'/>
        <map url='lucene.xml' replace='lucene.html'/>
        <map url='ngram.xml' replace='ngram.html'/>
        <map url='production_good_practice.xml' replace='production_good_practice.html'/>
        <map url='production_web_proxying.xml' replace='production_web_proxying.html'/>
        <map url='quickstart.xml' replace='quickstart.html'/>
        <map url='readying-centos-for-exist.xml' replace='readying-centos-for-exist.html'/>
        <map url='roadmap.xml' replace='roadmap.html'/>
        <map url='scheduler.xml' replace='scheduler.html'/>
        <map url='security.xml' replace='security.html'/>
        <map url='sidebar.xml' replace='sidebar.html'/>
        <map url='template.xml' replace='template.html'/>
        <map url='triggers.xml' replace='triggers.html'/>
        <map url='tuning.xml' replace='tuning.html'/>
        <map url='ubuntu-server.xml' replace='ubuntu-server.html'/>
        <map url='update_ext.xml' replace='update_ext.html'/>
        <map url='upgrading.xml' replace='upgrading.html'/>
        <map url='urlrewrite.xml' replace='urlrewrite.html'/>
        <map url='validation.xml' replace='validation.html'/>
        <map url='versioning.xml' replace='versioning.html'/>
        <map url='webdav.xml' replace='webdav.html'/>
        <map url='xacml-dev.xml' replace='xacml-dev.html'/>
        <map url='xacml-features.xml' replace='xacml-features.html'/>
        <map url='xacml-intro.xml' replace='xacml-intro.html'/>
        <map url='xacml-usage.xml' replace='xacml-usage.html'/>
        <map url='xacml.xml' replace='xacml.html'/>
        <map url='xinclude.xml' replace='xinclude.html'/>
        <map url='xmlprague06.xml' replace='xmlprague06.html'/>
        <map url='xquery.xml' replace='xquery.html'/>
    </mapping>
;

declare function local:transform($node as node()) {
    typeswitch ($node)
        case document-node() return
            local:transform($node/*)
        case element(xhtml:a) return
            let $link := 
                if (contains($node/@href, "/")) then
                    replace($node/@href, "^.*/([^/]+)$", "$1")
                else
                    $node/@href
            return
                if ($local:MAP/map[@url = $link]) then
                    <xhtml:a href="http://exist-db.org/{$local:MAP/map[@url = $link]/@replace}">
                    {
                        $node/@*[local-name(.) != 'href'],
                        for $child in $node/node() return local:transform($child)
                    }
                    </xhtml:a>
                else
                    $node
        case element() return
            element { node-name($node) } {
                $node/@*,
                for $child in $node/node() return local:transform($child)
            }
        default return
            $node
};

let $input := request:get-data()
let $log := util:log("DEBUG", $input)
return
    local:transform($input)
