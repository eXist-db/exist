xquery version "3.0";

module namespace bang="http://exist-db.org/xquery/test/bang";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $bang:works :=
    <works>
        <employee name="Jane Doe 1" gender="female">
            <empnum>E1</empnum>
            <pnum>P1</pnum>
            <hours>40</hours>
        </employee>
        <employee name = "John Doe 2" gender="male">
            <empnum>E1</empnum>
            <pnum>P2</pnum>
            <hours>70</hours>
            <hours>20</hours>Text data from Employee[2]
        </employee>
        <employee name = "Jane Doe 3" gender="female">
            <empnum>E1</empnum>
            <pnum>P3</pnum>
            <hours>80</hours>
        </employee>
        <employee name= "John Doe 4" gender="male">
            <empnum>E1</empnum>
            <pnum>P4</pnum>
            <hours>20</hours>
            <hours>40</hours>
        </employee>
    </works>;

declare
    %test:assertEquals(1, 4, 9, 16, 25, 36, 49, 64, 81, 100)
function bang:numbers1() {
    (1 to 10)!(.*.)
};

declare
    %test:assertEquals(3, 4, 5)
function bang:functions1() {
    ("red", "blue", "green")!string-length()
};

declare
    %test:assertEquals(4, 5, 6)
function bang:functions2() {
    ("red", "blue", "green") ! string-length() ! (.+1)
};

declare
    %test:assertEquals("RED", "BLUE", "GREEN")
function bang:functions3() {
    ("red", "blue", "green") ! upper-case(.)
};

declare
    %test:assertEquals("RED", "BLUE", "GREEN")
function bang:functions4() {
    ("red", "blue", "green") ! bang:upper-case(.)
};

declare %private function bang:upper-case($str as xs:string) {
    upper-case($str)
};

declare
    %test:assertEquals(1, 2, 3)
function bang:position1() {
    ("red", "blue", "green")!position()
};

declare
    %test:assertEquals("false", "false", "true")
function bang:position2() {
    ("red", "blue", "green")!(position() = last())
};

declare
    %test:assertEquals(1, 1, 2, 1, 2, 3)
function bang:position3() {
    (1 to 3) ! ((1 to .) ! position())
};

declare
    %test:assertEquals(3, 3, 3)
function bang:position4() {
    ("red", "blue", "green")!last()
};

declare
    %test:assertEquals("false", "false", "false", "true")
function bang:position5() {
    $bang:works/employee ! (position() = last())
};

declare
    %test:assertEquals("John Doe 2", "Jane Doe 1", "John Doe 2")
function bang:nodes1() {
    ($bang:works/employee[2], $bang:works/employee[1], $bang:works/employee[2]) ! @name ! string()
};

declare
    %test:assertEquals(20)
function bang:nodes2() {
    $bang:works ! employee[2] ! hours[2] ! number()
};

declare
    %test:assertEquals("-18")
function bang:precedence1() {
    2 + ($bang:works ! employee[2] ! hours[2]) ! number() ! (-.)
};

declare
    %test:assertEquals("-3")
function bang:precedence2() {
    -2!(.+1)
};

declare
    %test:assertEquals("-3")
function bang:precedence3() {
    -2!(.+1)
};

declare
    %test:assertEquals(1, 1, 2, 1, 2, 3)
function bang:sequence() {
    (1 to 3) ! (1 to .)
};

declare
    %test:assertEquals(4)
function bang:nodepath() {
    count($bang:works ! employee)
};

declare
    %test:assertEquals("John Doe 4")
function bang:nodepath-reverse() {
    $bang:works/employee/pnum[. = "P4"] ! ancestor::employee ! @name ! string()
};

declare
    %test:assertEquals("<name>John Doe 4</name>")
function bang:constructor() {
    $bang:works/employee[pnum = "P4"] ! <name>{@name/string()}</name>
};

declare
    %test:assertTrue
function bang:implicit-context() {
    count(//* ! local-name(.))
};