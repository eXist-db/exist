xquery version "1.0";

import module namespace request="http://exist-db.org/xquery/request";

<html>
    <head>
        <style type="text/css">
        body {{ margin: 25px 75px }}
        th {{ text-align: left; }}
        td.label {{ width: 40%; color: blue; }}
        </style>
    </head>
    <body>
        <h1>Redirect test</h1>
        
        <p>If you can read this page, your request has been successfully redirected to
        the XQuery index.xql. Note that the redirect took place at the server and is not
        visible to the client (the URL in the browser's location bar should not have
        changed).</p>
        
        <table cellspacing="15" width="500">
            <tr><th colspan="2">URIs</th></tr>
            <tr>
                <td class="label">My request URI:</td>
                <td>{request:get-uri()}</td>
            </tr>
            <tr>
                <td class="label">Original request URI this query was called with:</td>
                <td>{request:get-attribute('org.exist.forward.request-uri')}</td>
            </tr>
            <tr><th colspan="2">Parameters</th></tr>
            {
                for $param in request:get-parameter-names()
                return
                    <tr>
                        <td class="label">{$param}</td>
                        <td>{request:get-parameter($param, ())}</td>
                    </tr>
            }
        </table>
        
        <h2>Client redirect</h2>
        
        <p>Clicking below will initiate a client redirect, which leads you to eXist's home page:</p>
        
        <p><a href="?logout=true">Logout</a></p>
    </body>
</html>