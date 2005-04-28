(: Find depth of document :)

declare namespace f="urn:my-functions";
declare function f:depth ($e as node()*) as xs:integer 
{
let $children := $e/*
return
if (empty($children)) then 0
else 1 + max
 (for $c in $children return f:depth ($c))
};

f:depth(doc("/db/address.xml"))