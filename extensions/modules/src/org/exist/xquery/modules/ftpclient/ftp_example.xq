xquery version "1.0";

import module namespace ftpclient="http://exist-db.org/xquery/ftpclient";

(:ftpclient:getDirectoryList("localhost","username","password","remote/dir"):)

(:let $file := ftpclient:getFile("localhost","username","password","remote/dir", "file.name"):)

let $file_to_sent := util:binary-doc("/db/ftp_test/image.jpg")

return ftpclient:sendFile("localhost","username","password","remote/dir", "file.name")