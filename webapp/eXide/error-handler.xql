xquery version "1.0";

declare option exist:serialize "method=xml media-type=text/xml";

let $input := request:get-data()
return
    <div>
        <h3>Request failed!</h3>
        
        <p>The request to the server failed, maybe due to missing permissions? The server's message is shown below:</p>
        
        {$input//div[@class = 'description']}
    </div>