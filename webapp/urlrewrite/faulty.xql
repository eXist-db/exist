xquery version "1.0";

let $numParam as xs:integer? := request:get-parameter("number", ())
return
    <html xmlns:style="http://exist-db.org/xquery/style">
        <head>
            <title>Error Handler Test</title>
            <style:default-styles/>
            <style type="text/css">
                label {{ float: left; width: 120px; }}
            </style>
        </head>
        <body>
            <style:page-head base="..">
                <xi:include xmlns:xi="http://www.w3.org/2001/XInclude" href="../sidebar.xml"/>
            </style:page-head>
            <div id="content2col">
                <h2>Error Handler Test</h2>
                
                <p>Submitting the following form will trigger a cast error in the server side
                XQuery. URL rewrite should then redirect to an error handling page.</p>
                
                <form>
                    <p>
                        <label for="number">Enter a number</label>
                        <input name="number" size="20" value="ABCDEFG"/>
                    </p>
                    <p><input type="submit"/></p>
                </form>
            </div>
        </body>
    </html>