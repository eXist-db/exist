xquery version "1.0";

module namespace ord = "http://exist-db.org/test/order";

declare namespace test = "http://exist-db.org/xquery/xqsuite";

declare variable $ord:items as element(item)+ := (
        <item>
         <ref>e1</ref>
         <msDesc type="Ll"/>
        </item>
        ,
        <item>
         <ref>e2</ref>
         <msDesc type="P"/>
        </item>
        ,
        <item>
         <ref>e3</ref>
         <msDesc type="Ll"/>
        </item>
        ,
        <item>
         <ref>e4</ref>
         <msDesc/>
        </item>
);

declare
    %test:assertEquals("e2", "e1", "e3", "e4")
function ord:default-order-for-empty-sequence-is-empty-least() {
    for $item in $ord:items
    let $sort-condition as xs:boolean? := $item/msDesc/@type eq 'P'
    order by $sort-condition descending
    return
        $item/ref/string()
};

declare
    %test:assertEquals("e2", "e1", "e3", "e4")
function ord:order-empty-sequence-as-empty-least() {
    for $item in $ord:items
    let $sort-condition as xs:boolean? := $item/msDesc/@type eq 'P'
    order by $sort-condition descending empty least
    return
        $item/ref/string()
};

declare
    %test:assertEquals("e4", "e2", "e1", "e3")
function ord:order-empty-sequence-as-empty-greatest() {
    for $item in $ord:items
    let $sort-condition as xs:boolean? := $item/msDesc/@type eq 'P'
    order by $sort-condition descending empty greatest
    return
        $item/ref/string()
};
