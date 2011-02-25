xquery version "1.0";

import module namespace xdb="http://exist-db.org/xquery/xmldb";

xdb:remove("/db/system/config/db/xqdocs"),
xdb:remove("/db/mobiledocs"),
xdb:remove("/db/xqdocs")