xquery version "3.0";

(:~ Additional tests for the fn:count function :)
module namespace comments="http://exist-db.org/xquery/test/comments";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare 
    %test:assertEquals("<r><a>1</a>(:comment:)<b>2</b>(:<c>3</c>:)<d>4</d></r>")
function comments:comments-in-xml() {
    (:for eXist 2.2:)
    <r><a>1</a>(:comment:)<b>2</b>(:<c>3</c>:)<d>4</d></r>
};

declare 
    %test:assertEquals('<r><a b="a (: comment :)">1</a></r>')
function comments:ignore-comment-in-attrib() {
    <r><a b="a (: comment :)">1</a></r>
};

declare 
    %test:assertEquals('<r><a b="a 1">1</a></r>')
function comments:comment-in-enclosed-attr() {
    <r><a b="a {(: comment :)1}">1</a></r>
};

declare 
    %test:assertEquals('<r><a>1</a></r>')
function comments:comment-in-enclosed() {
    (: before :)
    <r><a>{(: start :)1(: end :)}</a></r>
    (: after :)
};