xquery version "1.0";

let $repo := request:get-parameter("package", ())
let $icon := repo:get-resource($repo, "icon.png")
return
    response:stream-binary($icon, "image/png", ())