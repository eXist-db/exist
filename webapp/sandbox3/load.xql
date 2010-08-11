xquery version "1.0";

let $path := request:get-parameter("path", ())
let $mime := xmldb:get-mime-type($path)
let $isBinary := util:is-binary-doc($path)
return
    if ($isBinary) then
        let $data := util:binary-doc($path)
        return
            response:stream-binary($data, $mime, ())
    else (
        response:set-header("Content-Type", $mime),
        doc($path)
    )