xquery version "1.0";

import module namespace xproc = "http://xproc.net/xproc";
import module namespace request="http://exist-db.org/xquery/request";
import module namespace xdb = "http://exist-db.org/xquery/xmldb";

let $uri := request:get-uri()
let $context := request:get-context-path()
let $path := substring-after($uri, $context)
let $name := replace($uri, '^.*/([^/]+)$', '$1')
return
    if ($path = "/xproc/") then
	    <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
    		<redirect url="examples.xml"/>
    	</dispatch>
	else if (ends-with($uri, '.xproc')) then
	    let $docName := replace($uri, '^.*/([^/]+)$', '$1')
	    let $pipeline := doc( concat('/db/xproc/examples/',$docName) )
        let $stdin := doc(concat("xmldb:exist://",request:get-parameter('stdin','/')))
        let $autobind := request:get-parameter('autobind','0')
        let $bindings := request:get-parameter('binding','')
        let $debug := request:get-parameter('debug','0')
        let $timing := request:get-parameter('timing','0')
        let $options := util:parse(request:get-parameter('options',''))
        let $requestparams :=if($autobind eq '1') then
                                for $binding in request:get-parameter-names()
                                return
                                    if($binding eq 'stdin' or $binding eq 'debug' or $binding eq 'autobind') then
                                        ()
                                    else
                                    <binding port="{$binding}">
                                        {util:parse(request:get-parameter($binding,''))}
                                    </binding>
                             else
                                ()
        let $xprocbindings := <bindings>
                                {$requestparams}
                                {util:parse($bindings)/binding}
                            </bindings>
        return
            xproc:run( $pipeline, $stdin, $debug, $timing, $xprocbindings, util:parse($options))
(:
    	    <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
    			<forward url="/rest/db/xproc/examples/{$docName}">
    				<!--add-parameter name="xproc" value="/db/xproc/examples/{$docName}"/-->
    			</forward>
    		</dispatch>
:)

    else if (ends-with($uri, '.xml')) then
        <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
			<view>
				<forward servlet="XSLTServlet">
					<set-attribute name="xslt.stylesheet" 
						value="{$exist:root}/stylesheets/db2xhtml.xsl"/>
				    <set-attribute name="xslt.output.media-type"
                        value="text/html"/>
                	<set-attribute name="xslt.output.doctype-public"
                	    value="-//W3C//DTD XHTML 1.0 Transitional//EN"/>
                	<set-attribute name="xslt.output.doctype-system"
                	    value="resources/xhtml1-transitional.dtd"/>
				</forward>
			</view>
            <cache-control cache="no"/>
		</dispatch>
    else if ($name = ('default-style.css', 'default-style2.css')) then
        let $newPath := replace($path, '^.*/([^/]+/[^/]+)$', '/$1')
        return
        <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
			<forward url="{$newPath}"/>
			<cache-control cache="yes"/>
		</dispatch>
    else
        <ignore xmlns="http://exist.sourceforge.net/NS/exist">
            <cache-control cache="yes"/>
    	</ignore>