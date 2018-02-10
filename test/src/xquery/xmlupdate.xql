xquery version "3.1";

module namespace xmlupdate="http://exist-db.org/xquery/test/xmlupdate";

import module namespace test="http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";


declare
    %test:assertEquals('<root><child/></root>')
function xmlupdate:root() {
    let $f  := xmldb:store('/db', 'xupdate.xml', <root/>)
    let $u  := update insert <child/> into doc($f)/root
    return doc($f)
};

declare
%test:assertEquals('<root attr="1"><child/></root>')
function xmlupdate:root_attribute() {
    let $r := xmldb:remove('/db', 'xupdate.xml')
    let $f  := xmldb:store('/db', 'xupdate.xml', <root attr="1"/>)
    let $u  := update insert <child/> into doc($f)/root
    return doc($f)
};

declare
%test:assertEquals('<root attr="1"><foo/><child/></root>')
function xmlupdate:root_attribute_child() {
    let $r := xmldb:remove('/db', 'xupdate.xml')
    let $f  := xmldb:store('/db', 'xupdate.xml', <root attr="1"><foo/></root>)
    let $u  := update insert <child/> into doc($f)/root
    return doc($f)
};

declare
%test:assertEquals('<root attr="1"><foo bar="2"/><child/></root>')
function xmlupdate:root_attribute_child_attribute() {
    let $r := xmldb:remove('/db', 'xupdate.xml')
    let $f  := xmldb:store('/db', 'xupdate.xml', <root attr="1"><foo bar="2"/></root>)
    let $u  := update insert <child/> into doc($f)/root
    return doc($f)
};

declare
%test:assertEquals('<root><!-- foobar --><child/></root>')
function xmlupdate:root_comment() {
    let $f  := xmldb:store('/db', 'xupdate.xml', <root><!-- foobar --></root>)
    let $u  := update insert <child/> into doc($f)/root
    return doc($f)
};

declare
%test:assertEquals('<root attr="1"><!-- foobar --><child/></root>')
function xmlupdate:root_comment_attribute() {
    let $f  := xmldb:store('/db', 'xupdate.xml', <root attr="1"><!-- foobar --></root>)
    let $u  := update insert <child/> into doc($f)/root
    return doc($f)
};