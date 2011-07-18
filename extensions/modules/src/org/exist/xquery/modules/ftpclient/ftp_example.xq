xquery version "1.0";

import module namespace ftpclient="http://exist-db.org/xquery/ftpclient";

let $connection := ftpclient:get-connection("ftp.host.com", "username", "password") return

    let $file-to-send := util:binary-doc("/db/ftp_test/image.jpg") return
        ftpclient:send-binary-file($connection, "remote/dir", "image.jpg")

(: ftpclient:list($connection, "remote/dir") :)
(: let $file := ftpclient:get-binary-file($connection, "remote/dir", "file.name") :)