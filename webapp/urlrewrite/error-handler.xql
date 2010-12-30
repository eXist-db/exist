xquery version "1.0";

let $input := request:get-data()
return
    <html xmlns:style="http://exist-db.org/xquery/style">
        <head>
            <title>Error</title>
            <style:default-styles/>
            <style type="text/css">
                .description {{ margin: 10px; padding: 20px 10px; border: 2px solid red; }}
            </style>
        </head>
        <body>
            <style:page-head/>
            <div id="content1col">
                <h2>An error has occurred</h2>
                {$input//div[@class = 'description']}
                
                <p><a href="examples.xml">Back</a> to the examples.</p>
            </div>
        </body>
    </html>