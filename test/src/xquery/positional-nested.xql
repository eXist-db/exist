xquery version "3.1";

module namespace npt="http://exist-db.org/test/nested-positional-predicate";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $npt:DATA :=
    document {
        <xml>
            <a>
                <b>B1</b>
            </a>
            <a>
                <b>B2</b>
                <c>correct</c>
            </a>
            <a>
                <b>B3</b>
                <c>wrong</c>
            </a>
        </xml>
    };

declare
    %test:setUp
function npt:setup() {
    xmldb:create-collection("/db", "test"),
    xmldb:store("/db/test", "test.xml", $npt:DATA)
};

declare
    %test:tearDown
function npt:cleanup() {
    xmldb:remove("/db/test")
};

declare
    %test:assertEquals("<c>correct</c><c>wrong</c>")
function npt:in-memory() {
    $npt:DATA//c[../preceding-sibling::a]
};

declare
    %test:assertEquals("<c>correct</c><c>wrong</c>")
function npt:in-database() {
    doc("/db/test.xml")//c[../preceding-sibling::a]
};


declare
    %test:assertEquals("<c>correct</c><c>wrong</c>")
function npt:in-memory-predicate() {
    $npt:DATA//c[../preceding-sibling::a[1]]
};

declare
    %test:assertEquals("<c>correct</c><c>wrong</c>")
function npt:in-database-predicate() {
    doc("/db/test.xml")//c[../preceding-sibling::a[1]]
};

declare
    %test:assertEquals("<c>correct</c><c>wrong</c>")
function npt:in-memory-position() {
    $npt:DATA//c[../preceding-sibling::a[position() eq 1]]
};

declare
    %test:assertEquals("<c>correct</c><c>wrong</c>")
function npt:in-database-position() {
    doc("/db/test.xml")//c[../preceding-sibling::a[position() eq 1]]
};

declare
    %test:assertEquals("<c>correct</c>")
function npt:in-memory-predicate-and-path() {
    $npt:DATA//c[../preceding-sibling::a[1]/b = 'B1']
};

declare
    %test:assertEquals("<c>correct</c>")
function npt:in-database-predicate-and-path() {
    doc("/db/test.xml")//c[../preceding-sibling::a[1]/b = 'B1']
};

declare
    %test:assertEquals("<c>correct</c>")
function npt:in-memory-position-and-path() {
    $npt:DATA//c[../preceding-sibling::a[position() eq 1]/b = 'B1']
};

declare
    %test:assertEquals("<c>correct</c>")
function npt:in-database-position-and-path() {
    doc("/db/test.xml")//c[../preceding-sibling::a[position() eq 1]/b = 'B1']
};
