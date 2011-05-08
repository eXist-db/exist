xquery version "1.0";

declare option exist:serialize "method=json media-type=application/json";

let $collection := request:get-parameter("collection", ())
let $name := request:get-uploaded-file-name("file[]")
let $data := request:get-uploaded-file-data("file[]")
return
    util:catch("*",
        let $path := xmldb:store($collection, $name, $data)
        return
            <result>
               <name>{$name}</name>
               <type>{xmldb:get-mime-type($path)}</type>
               <size>93928</size>
           </result>,
           <result>
               <name>{$name}</name>
               <error>{$util:exception-message}</error>
           </result>
   )