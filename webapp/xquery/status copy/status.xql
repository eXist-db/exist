xquery version "1.0";

import module namespace request = "http://exist-db.org/xquery/request";

declare variable $local:jmx-servlet-path := "/status";

declare function local:get-server-uri() as xs:string {
    fn:concat("http://", request:get-server-name(), ":", request:get-server-port(), request:get-context-path())
};


let $docbook := transform:transform(
    doc(fn:concat(local:get-server-uri(), $local:jmx-servlet-path)),
    doc("file:///Users/aretter/NetBeansProjects/eXist/webapp/xquery/status/status.xslt"),
    ()
) return
    
    
   
    transform:stream-transform(
        $docbook,
        doc("file:///Users/aretter/NetBeansProjects/eXist/webapp/stylesheets/db2xhtml.xsl"),
        ()
    )  