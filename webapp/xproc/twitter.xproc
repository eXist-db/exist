<?xml version="1.0" encoding="UTF-8"?>
<p:pipeline name="pipeline"
    xmlns:p="http://www.w3.org/ns/xproc"
    xmlns:c="http://www.w3.org/ns/xproc-step"
    xmlns:xproc="http://xproc.net/xproc"
    xmlns:xhtml="http://www.w3.org/1999/xhtml"
    xmlns:atom="http://www.w3.org/2005/Atom">
    <p:http-request name="http-get">
        <p:input port="source">
            <p:inline>
                <c:request href="http://twitter.com/statuses/user_timeline/existdb.atom" 
                    method="get"/>
            </p:inline>
        </p:input>
    </p:http-request>
    <p:filter select="//atom:feed"/>
    
    <p:xquery>
        <p:input port="query">
            <p:data href="/db/xproc/twitter-view.xql" 
                wrapper="c:query" 
                content-type="plain/text" 
                xproc:escape="false"/>
        </p:input>
    </p:xquery>
    <!-- Doesn't work, complains about undefined namespace xhtml: -->
    <!--p:filter select="/c:result/xhtml:html"/-->
    <!-- Works: -->
    <p:filter select="/c:result/*"/>
</p:pipeline>