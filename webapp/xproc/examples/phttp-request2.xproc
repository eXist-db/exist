<p:pipeline name="pipeline"
    xmlns:p="http://www.w3.org/ns/xproc"
    xmlns:c="http://www.w3.org/ns/xproc-step"
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

</p:pipeline>