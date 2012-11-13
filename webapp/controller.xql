(: 	This is the main controller for the web application. It is called from the
	XQueryURLRewrite filter configured in web.xml. :)
xquery version "3.0";

(:~ -------------------------------------------------------
    Main controller: handles all requests not matched by
    sub-controllers.
    ------------------------------------------------------- :)

declare namespace c="http://exist-db.org/xquery/controller";

import module namespace request="http://exist-db.org/xquery/request";
import module namespace xdb = "http://exist-db.org/xquery/xmldb";

declare function local:get-dashboard() {
	(: TODO: is there an easier way to determine the location of an app? :)
	let $meta := repo:get-resource("http://exist-db.org/apps/dashboard", "repo.xml")
	let $data := if (exists($meta)) then util:binary-to-string($meta) else ()
	return
    	if (exists($data)) then
            util:parse($data)//repo:target/string()
        else
            ()
};

let $query := request:get-parameter("q", ())
return
	(: redirect webapp root to index.xml :)
    if ($exist:path eq '') then
	   <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
	     <redirect url="{concat(request:get-uri(), '/')}"/>
	   </dispatch>
    else if ($exist:path eq '/') then
    	let $dashboard := local:get-dashboard()
    	return
			<dispatch xmlns="http://exist.sourceforge.net/NS/exist">
			{
				if ($dashboard) then
					<redirect url="apps/{substring-after($dashboard, '/db/')}/"/>
				else
					<redirect url="404.html"/>
			}
			</dispatch>
	else
		<ignore xmlns="http://exist.sourceforge.net/NS/exist">
            <cache-control cache="yes"/>
		</ignore>
