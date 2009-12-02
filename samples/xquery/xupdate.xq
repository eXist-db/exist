xquery version "1.0";

declare namespace xmldb="http://exist-db.org/xquery/xmldb";

declare variable $data {
    <todo-list>
        <todo>
            <description>Add support for binary objects</description>
            <priority level="6"/>

            <date>2003-11-22</date>
            <status>finished</status>
        </todo>
    </todo-list>
};

declare variable $xupdate {
    <xu:modifications version="1.0" xmlns:xu="http://www.xmldb.org/xupdate">
        <xu:rename select="/todo-list/todo/date">
            start-date
        </xu:rename>
        <xu:append select="/todo-list">
            <todo>
                <description>Document supported XML schema types</description> 
                <start-date>{current-date()}</start-date>
                <status>open</status>
            </todo>
        </xu:append> 
        <xu:append select="/todo-list/todo[status='open']">
            <priority level="3"/>
        </xu:append>
        <xu:update select="/todo-list/todo/status">
            closed
        </xu:update>
    </xu:modifications>
};

let $isLoggedIn := xmldb:login("xmldb:exist:///db", "admin", ""),
    $collection := xmldb:create-collection("xmldb:exist:///db", "test"),
    $doc := xmldb:store($collection, "test.xml", $data),
    $mods := xmldb:update($collection, $xupdate)
return
    <result>
        <status>{$mods} modifications processed.</status>
        {for $m in doc("/db/test/test.xml")//todo return $m}
    </result>
