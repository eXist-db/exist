xquery version "1.0";

import module namespace request="http://exist-db.org/xquery/request";
import module namespace xdb = "http://exist-db.org/xquery/xmldb";

(:~
    Initialize the todo application
:)
declare function local:setup() {
    if (not(collection("/db/todo"))) then
        let $coll := xdb:create-collection("/db", "todo")
        let $home := system:get-exist-home()
    	let $dir :=
    		if (doc-available(concat("file:///", $home, "/webapp/download.xml"))) then
    			concat($home, "/webapp")
    		else if(ends-with($home, "WEB-INF")) then
    			substring-before($home, "WEB-INF")
    		else
    			concat($home)
    	return (
    		xdb:store-files-from-pattern("/db/todo", concat($dir, "/xforms/tasks"), "todo-projects.xml", "text/xml"),
    		xdb:store("/db/todo", "3d5ce5a0-2420-4eb9-a94f-fcbe05589f99.xml",
    		    <todo id="3d5ce5a0-2420-4eb9-a94f-fcbe05589f99">
                    <name>Add some tasks and projects</name>
                    <project>default</project>
                    <priority>0</priority>
                    <progress/>
                    <description/>
                </todo>
            )
    	)
    else ()
};

let $dummy := local:setup()
let $uri := request:get-uri()
let $context := request:get-context-path()
let $path := substring-after($uri, $context)
let $name := replace($uri, '^.*/([^/]+)$', '$1')
return
    (: send docbook docs through the db2xhtml stylesheet :)
    if (ends-with($uri, 'examples.xml') or ends-with($uri, 'xforms.xml')) then
        <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
			<view>
				<forward servlet="XSLTServlet">
					<set-attribute name="xslt.stylesheet"
						value="/stylesheets/db2html.xsl"/>
				</forward>
			</view>
            <cache-control cache="no"/>
		</dispatch>
    else
        <ignore xmlns="http://exist.sourceforge.net/NS/exist">
            <cache-control cache="yes"/>
    	</ignore>