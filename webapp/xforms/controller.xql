xquery version "1.0";

import module namespace request="http://exist-db.org/xquery/request";
import module namespace xdb = "http://exist-db.org/xquery/xmldb";
  

(: Note - Disabled the TODO application because the controller.xql is NOT the place to do your application installation!
    Also with the new tighter permissions in eXist-db, the guest user cannot just create /db/todo collection
    There should be a seperate setup button, whereby the user has to enter dba credentials, or better still
    this should be packages as an EXPath pkg.
    -- Adam.
:)

(:~
    Initialize the todo application
:)
(:
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
:)

(:
let $dummy := local:setup()
return
:)

    if ($exist:path eq '/') then
		<dispatch xmlns="http://exist.sourceforge.net/NS/exist">
			<redirect url="examples.xml"/> (: Change this or not? /ljo:)
		</dispatch>
		
    else if (ends-with($exist:path, '/source')) then
        let $resource := substring-before($exist:path, '/source')
        let $mode := request:get-parameter("mode", "xquery")
        return
            <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
        		<forward url="{$exist:controller}{$resource}?source"/>
        		<view>
        			<forward url="../../xquery/source.xql">
                        <set-attribute name="resource" value="{$resource}"/>
                        <set-attribute name="mode" value="{$mode}"/>
                    </forward>
        		</view>
        	</dispatch>
    	
    (: send docbook docs through the db2xhtml stylesheet :)
   else if ($exist:resource = ('examples.xml', 'xforms.xml')) then
         <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
 			<view>
 				<forward servlet="XSLTServlet">
 				    <set-attribute name="xslt.output.media-type" value="text/html"/>
                	<set-attribute name="xslt.output.doctype-public" value="-//W3C//DTD XHTML 1.0 Transitional//EN"/>
                	<set-attribute name="xslt.output.doctype-system" value="resources/xhtml1-transitional.dtd"/>
 					<set-attribute name="xslt.stylesheet" value="{$exist:root}/stylesheets/db2xhtml.xsl"/>
 					<set-attribute name="xslt.root" value="{request:get-context-path()}{$exist:prefix}"/>
 				</forward>
 			</view>
             <cache-control cache="yes"/>
 		</dispatch>
 		
    (:  for the following examples, the xsltforms.xsl stylesheet is applied
        on the *server*, not the client :)
    else if ($exist:resource = ('todo-list.xml', 'shakespeare.xml')) then
        let $relPath := if ($exist:resource eq 'todo-list.xml') then '../' else ''
        return
            <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
    			<view>
    			    <forward servlet="XSLTServlet">
    					<set-attribute name="xslt.stylesheet" value="{$exist:root}/stylesheets/db2xhtml.xsl"/>
    				    <set-attribute name="xslt.syntax-highlight" value="no"/>
						<set-attribute name="xslt.root" value="{request:get-context-path()}{$exist:prefix}"/>
				        <set-attribute name="xslt.base" value="{$exist:root}"/>
    				</forward>
    			    <forward servlet="XSLTServlet">
    			        (: Apply xsltforms.xsl stylesheet :)
    					<set-attribute name="xslt.stylesheet" value="{$relPath}xsltforms/xsltforms.xsl"/>
    				    <set-attribute name="xslt.output.omit-xml-declaration" value="yes"/>
    				    <set-attribute name="xslt.output.indent" value="no"/>
    				    <set-attribute name="xslt.output.media-type" value="text/html"/>
    				    <set-attribute name="xslt.output.method" value="xhtml"/>
    				    <set-attribute name="xslt.baseuri" value="{$relPath}xsltforms/"/>
						<set-attribute name="xslt.xsltforms_home" value="webapp/xforms/xsltforms/"/>
						(: new :)
    				</forward>
    			</view>
    			<cache-control cache="yes"/>
    		</dispatch>
    		
    else if ($exist:resource eq 'test.xql') then
        <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
    		<view>
    		    <forward servlet="XSLTServlet">
    		        (: Apply xsltforms.xsl stylesheet :)
    				<set-attribute name="xslt.stylesheet" value="xsltforms/xsltforms.xsl"/>
    			    <set-attribute name="xslt.output.omit-xml-declaration" value="yes"/>
    			    <set-attribute name="xslt.output.indent" value="no"/>
    			    <set-attribute name="xslt.output.media-type" value="text/html"/>
    			    <set-attribute name="xslt.output.method" value="xhtml"/>
    			    <set-attribute name="xslt.baseuri" value="xsltforms/"/>
    			    <set-attribute name="xslt.xsltforms_home" value="webapp/xforms/xsltforms/"/>
    			</forward>
    		</view>
    		<cache-control cache="yes"/>
    	</dispatch>
    	
    (: make sure the global css and js files are resolved :)
    else if (matches($exist:path, '(resources/|styles/syntax/|scripts/syntax/|logo.jpg|default-style2.css|curvycorners.js)')) then
        let $newPath := replace($exist:path, '^.*((resources/|styles/|scripts/|logo).*)$', '/$1')
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
