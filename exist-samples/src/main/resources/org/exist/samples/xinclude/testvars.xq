xquery version "1.0";

declare namespace xdb="http://exist-db.org/xquery/xmldb";
declare namespace util="http://exist-db.org/xquery/util";

declare namespace display="display-collection";

declare variable $var1 external;
declare variable $var2 external;

<ul>
    <li>$var1 = {$var1}</li>
    <li>$var2 = {$var2}</li>
</ul>