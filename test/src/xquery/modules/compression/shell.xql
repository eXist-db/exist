xquery version "3.0";

import module namespace uz="http://exist-db.org/testsuite/unzips" at "unzip-tests.xql";

(:uz:setup():)

(:uz:fnContentAvailable("myFile.xml"):)

(:file:read("file:///C:/pierreIC.scenarios"):)

(:doc("file:///C:/pierreIC.scenarios"):)

(:doc("./pierreIC.scenarios"):)

uz:setup() 
 
(:<res>{util:binary-doc("/db/unzip-test/myZip.zip")}</res>:)