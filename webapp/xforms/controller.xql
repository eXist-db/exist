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
    if (matches($path, '/xforms/?$')) then
		<dispatch xmlns="http://exist.sourceforge.net/NS/exist">
			<redirect url="{$context}/xforms/examples.xml"/>
		</dispatch>
    (: send docbook docs through the db2xhtml stylesheet :)
   else if (ends-with($uri, 'examples.xml') or ends-with($uri, 'xforms.xml')) then
         <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
 			<view>
 				<forward servlet="XSLTServlet">
 				    <set-attribute name="xslt.output.media-type"
                        value="text/html"/>
                	<set-attribute name="xslt.output.doctype-public"
                	    value="-//W3C//DTD XHTML 1.0 Transitional//EN"/>
                	<set-attribute name="xslt.output.doctype-system"
                	    value="resources/xhtml1-transitional.dtd"/>
 					<set-attribute name="xslt.stylesheet"
 						value="/stylesheets/db2xhtml.xsl"/>
 				</forward>
 			</view>
             <cache-control cache="yes"/>
 		</dispatch>
    else if ($name = ('todo-list.xml', 'shakespeare.xml')) then
        <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
			<view>
				<forward servlet="XSLTServlet">
					<set-attribute name="xslt.stylesheet"
						value="/stylesheets/db2xhtml.xsl"/>
				    <set-attribute name="xslt.syntax-highlight"
				        value="no"/>
				</forward>
			</view>
			<cache-control cache="yes"/>
		</dispatch>
    (: make sure the global css and js files are resolved :)
    else if ($name = ('default-style.css', 'default-style2.css', 'curvycorners.js')
        or matches($path, 'resources/')) then
        let $newPath := replace($path, '^.*/([^/]+/[^/]+)$', '/$1')
        return
            <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
    			<forward url="{$newPath}"/>
    			<cache-control cache="yes"/>
    		</dispatch>
    else if (matches($path, 'syntax/.*\.(css|js)')) then
        let $newPath := replace($path, '^.*/([^/]+/syntax/[^/]+)$', '/$1')
        return
            <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
    			<forward url="{$newPath}"/>
    			<cache-control cache="yes"/>
    		</dispatch>
    else
        (: everything else is passed through :)
        <ignore xmlns="http://exist.sourceforge.net/NS/exist">
            <cache-control cache="yes"/>
    	</ignore>
