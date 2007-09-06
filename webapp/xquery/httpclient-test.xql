xquery version "1.0";

declare namespace request="http://exist-db.org/xquery/request";
declare namespace response="http://exist-db.org/xquery/response";
declare namespace xmldb="http://exist-db.org/xquery/xmldb";
declare namespace util="http://exist-db.org/xquery/util";

declare function local:wrapData( $data, $method ) as element()
{
    let $timestamp := fn:current-dateTime()
    
    return
        <request>
            <method>{$method}</method>
            <payload xmlns="http://exist-db.org/xquery/httpclienttest">
                   { $data }
            </payload>
        </request>

};

let $method := request:get-method()

return
    if( $method eq "POST" or $method eq "PUT" ) then (
        let $payload := request:get-data()
        let $temp := response:set-header( "X-Test-Method", $method )
        return
            if( $payload )then (
                let $data := local:wrapData( $payload, $method )
                return
                    $data 
            ) else (
               let $temp := response:set-status-code( 400 )
               return
                   <error>no data received</error>
            )
    ) else if ($method eq "GET" or $method eq "DELETE" ) then (
             let $temp := response:set-header( "X-Test-Method", $method )
             let $data := local:wrapData( "none", $method )
                return
                    $data 
     ) else if ($method eq "HEAD" or $method eq "OPTIONS" ) then (
        let $temp := response:set-header( "X-Test-Method", $method )
        return ()
    ) else (
        let $temp := response:set-status-code( 405 )
        let $temp := response:set-header( "Allow", "GET POST PUT DELETE HEAD OPTIONS" )
        return
        <error>Invalid HTTP Method: {$method}</error>
    )