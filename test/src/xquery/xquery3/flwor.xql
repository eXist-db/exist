xquery version "3.0";

module namespace flwor="http://exist-db.org/xquery/test/flwor";

declare namespace test="http://exist-db.org/xquery/xqsuite";

(: https://github.com/eXist-db/exist/issues/739#issuecomment-130997865 :)
declare
    %test:assertEquals(1, 2, 1, 2, 3, 1, 2, 3, 4)
function flwor:orderby-for-multi() {
    for $x in (3, 2, 4)
    order by $x
    for $y in (1 to $x)
    return $y
};

declare
    %test:assertEquals("a1", "a2", "b1", "b2")
function flwor:orderby-multi() {
  for $a in ("b", "a")
  order by $a
  for $b in (2, 1)
  order by $b
  return
    $a || $b
};

declare
    %test:assertEquals("a1", "a2", "b1", "b2")
function flwor:orderby-where-multi() {
    for $a in ("b", "a", "c")
    order by $a
    where $a = ("a", "b")
    for $b in (2, 1, 5, 6, 9)
    where $b < 4
    order by $b
    return
        $a || $b
};

declare
    %test:assertEquals(2, 4)
function flwor:where-multi() {
    for $a in 1 to 10
    where $a mod 2 = 0
    where $a < 5
    return
        $a
};

declare
    %test:assertEquals(1, 1)
function flwor:where-multi-groupby() {
    let $xml :=
        <t>
            <n class="a">1</n>
            <n class="b">2</n>
            <n class="a">1</n>
            <n class="a">3</n>
        </t>
    for $a in $xml/n
    where $a != 3
    group by $c := $a/@class
    where count($a) > 1
    return
        $a/string()
};

declare
    %test:args(0)
    %test:assertEquals("[]")
    %test:args(1)
    %test:assertEquals("[1]")
    %test:args(6)
    %test:assertEquals("[1]", "[2]", "[3]", "[4]", "[6]")
function flwor:allowing-empty($n as xs:integer) {
    for $x allowing empty in 1 to $n
    where not($x = 5)
    return concat("[", $x, "]")
};
