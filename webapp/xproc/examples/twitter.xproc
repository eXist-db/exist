<?xml version="1.0"?>
<p:pipeline xmlns:c="http://www.w3.org/ns/xproc-step" xmlns:p="http://www.w3.org/ns/xproc" xmlns:xproc="http://xproc.net/xproc" name="pipeline">
<!-- Need to make sure that the /db/twitter collection exists.
       Maybe p:store should do this automatically? -->
<p:xquery>
    <p:input port="query">
        <p:inline>
            <c:query>
                   xmldb:create-collection("/db", "twitter")
               </c:query>
        </p:inline>
    </p:input>
</p:xquery>
<p:xquery>
    <!--
           <p>Use an XQuery to see if the twitter feed has already been
           cached in the db. If yes, check if the last update is older
           than 5 minutes.
       -->
    <p:input port="query">
        <p:inline>
            <!--
                   TODO: wrapping the query into a CDATA section
                   doesn't work.
                   atom namespace needs extra declaration inside
                   query.
               -->
            <c:query>
                   declare namespace
                   atom="http://www.w3.org/2005/Atom";

                   let $feed :=
                   doc("/db/twitter/existdb.xml")/atom:feed
                   return
                       if (exists($feed) and
                           (xs:dateTime($feed/atom:updated) +
                   xs:dayTimeDuration("PT5M")) &gt;
                   current-dateTime()) then
                           $feed
                       else
                           &lt;notfound/&gt;
               </c:query>
        </p:inline>
    </p:input>
</p:xquery>
<p:choose name="test-uptodate">
    <p:when test=".//notfound">
        <p:http-request name="http-get">
            <p:input port="source">
                <p:inline>
                    <c:request href="http://twitter.com/statuses/user_timeline/existdb.atom" method="get"/>
                </p:inline>
            </p:input>
        </p:http-request>
        <p:filter select="//*:feed"/>
        <p:store href="/db/twitter/existdb.xml" xproc:output="true"/>
    </p:when>
    <p:otherwise>
        <p:filter select="//*:feed"/>
        <p:identity/>
    </p:otherwise>
</p:choose>
<p:xquery>
    <p:input port="source">
        <p:step port="result" step="test-uptodate"/>
    </p:input>
    <p:input port="query">
        <p:data href="/db/xproc/examples/twitter-view.xql" wrapper="c:query" content-type="plain/text" xproc:escape="false"/>
    </p:input>
</p:xquery>
<p:filter select="//*:html"/>
</p:pipeline>
