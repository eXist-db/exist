xquery version "3.1";

(:~
 : Tests for boolean operators in different contexts
 :)
module namespace op="http://exist-db.org/test/boolean-ops";

declare namespace test="http://exist-db.org/xquery/xqsuite";
declare namespace tei="http://www.tei-c.org/ns/1.0";

declare variable $op:DATA1 :=
    document {
        <root>
            <foo/>
            <bar/>
        </root>
    };

declare variable $op:DATA2 :=
    <TEI xmlns="http://www.tei-c.org/ns/1.0">
        <person xml:id="accolti_pietro">
            <persName>
                <forename>Pietro</forename>
                <surname>Accolti</surname>
            </persName>
        </person>
    </TEI>;
    
declare
    %test:setUp
function op:setup() {
    xmldb:create-collection("/db", "boolean-ops-test"),
    xmldb:store("/db/boolean-ops-test", "test1.xml", $op:DATA1),
    xmldb:store("/db/boolean-ops-test", "test2.xml", $op:DATA2)
};

declare
    %test:tearDown
function op:cleanup() {
    xmldb:remove("/db/boolean-ops-test")
};

(: Simple tests :)

declare 
    %test:assertEquals("false", "false", "false", "true", "false", "false", "true", "false", "true", 
        "true", "true")
function op:and-simple-boolean() {
    () and (),
    0 and 0,
    1 and 0,
    1 and 1,
    "" and "",
    "" and "a",
    "a" and "b",
    <a/> and (),
    <a/> and <b/>,
    <a/> and (<a/>, <b/>),
    <a/> and (<a/>, false())
};

declare 
    %test:assertEquals("false", "false", "true", "true", "false", "true", "true", "true", "true", "true",
        "true")
function op:or-simple-boolean() {
    () or (),
    0 or 0,
    1 or 0,
    1 or 1,
    "" or "",
    "" or "a",
    "a" or "b",
    <a/> or (),
    <a/> or <b/>,
    <a/> or (<a/>, <b/>),
    <a/> and (<a/>, false())
};

declare 
    %test:assertEquals("true", "true", "true", "false", "true", "true", "false", "true", "false", 
        "false", "false")
function op:and-simple-boolean-negated() {
    not(() and ()),
    not(0 and 0),
    not(1 and 0),
    not(1 and 1),
    not("" and ""),
    not("" and "a"),
    not("a" and "b"),
    not(<a/> and ()),
    not(<a/> and <b/>),
    not(<a/> and (<a/>, <b/>)),
    not(<a/> and (<a/>, false()))
};

declare 
    %test:assertEquals("true", "true", "false", "false", "true", "false", "false", "false", "false", "false",
        "false")
function op:or-simple-boolean-negated() {
    not(() or ()),
    not(0 or 0),
    not(1 or 0),
    not(1 or 1),
    not("" or ""),
    not("" or "a"),
    not("a" or "b"),
    not(<a/> or ()),
    not(<a/> or <b/>),
    not(<a/> or (<a/>, <b/>)),
    not(<a/> and (<a/>, false()))
};

(: Tests on stored data :)

declare 
    %test:assertEquals("<test><result>true</result><result>true</result></test>")
function op:or-enclosed-path-stored() {
    let $x := doc('/db/boolean-ops-test/test1.xml')/root
    let $y := doc('/db/boolean-ops-test/test1.xml')/root
    return 
        <test>
            <result>{$x/foo or $x/bar}</result>
            <result>{$y/foo or $y/bar}</result> 
        </test>
};

declare 
    %test:assertEquals("<test><result>false</result><result>false</result></test>")
function op:or-enclosed-path-stored-false() {
    let $x := doc('/db/boolean-ops-test/test1.xml')/root
    let $y := doc('/db/boolean-ops-test/test1.xml')/root
    return 
        <test>
            <result>{$x/foo1 or $x/bar1}</result>
            <result>{$y/foo1 or $y/bar1}</result> 
        </test>
};

declare 
    %test:assertEquals("<test><result>true</result><result>true</result></test>")
function op:and-enclosed-path-stored() {
    let $x := doc('/db/boolean-ops-test/test1.xml')/root
    let $y := doc('/db/boolean-ops-test/test1.xml')/root
    return 
        <test>
            <result>{$x/foo and $x/bar}</result>
            <result>{$y/foo and $y/bar}</result> 
        </test>
};

declare 
    %test:assertEquals("<test><result>false</result><result>false</result></test>")
function op:and-enclosed-path-stored-false() {
    let $x := doc('/db/boolean-ops-test/test1.xml')/root
    let $y := doc('/db/boolean-ops-test/test1.xml')/root
    return 
        <test>
            <result>{$x/foo and $x/bar1}</result>
            <result>{$y/foo1 and $y/bar}</result> 
        </test>
};

declare 
    %test:assertEquals("Accolti, Pietro")
function op:and-if-path-let-stored() {
    let $node := doc('/db/boolean-ops-test/test2.xml')/id('accolti_pietro')/tei:persName
    let $tr := if ($node/tei:surname and $node/tei:forename) then ", " else ""
    return 
        concat($node/tei:surname, $tr, $node/tei:forename)
};

declare 
    %test:assertEquals("both")
function op:and-if-path-stored() {
    let $node := doc('/db/boolean-ops-test/test2.xml')/id('accolti_pietro')/tei:persName
    return
        if ($node/tei:surname and $node/tei:forename) then "both" else ""
};

declare 
    %test:assertEquals("true")
function op:and-path-stored() {
    let $node := doc('/db/boolean-ops-test/test2.xml')/id('accolti_pietro')/tei:persName
    return
        $node/tei:surname and $node/tei:forename
};

declare 
    %test:assertEquals("false")
function op:and-path-stored-false() {
    let $node := doc('/db/boolean-ops-test/test2.xml')/id('accolti_pietro')/tei:persName
    return
        $node/tei:surname and $node/tei:forename1
};

declare 
    %test:assertEquals("true")
function op:or-path-stored() {
    let $node := doc('/db/boolean-ops-test/test2.xml')/id('accolti_pietro')/tei:persName
    return
        $node/tei:surname or $node/tei:forename
};

declare 
    %test:assertEquals("false")
function op:or-path-stored-false() {
    let $node := doc('/db/boolean-ops-test/test2.xml')/id('accolti_pietro')/tei:persName
    return
        $node/tei:surname1 or $node/tei:forename1
};

declare 
    %test:assertEquals("Accolti")
function op:and-filter-stored() {
    doc('/db/boolean-ops-test/test2.xml')/id('accolti_pietro')/tei:persName[tei:surname and tei:forename]
        /tei:surname/string()
};

declare
    %test:assertEquals("Accolti")
function op:or-filter-stored() {
    doc('/db/boolean-ops-test/test2.xml')/id('accolti_pietro')/tei:persName[tei:surname or tei:forename]
        /tei:surname/string()
};

declare 
    %test:assertEmpty
function op:and-filter-stored-false() {
    doc('/db/boolean-ops-test/test2.xml')/id('accolti_pietro')/tei:persName[tei:surname and tei:forename1]
        /tei:surname/string()
};

declare 
    %test:assertEmpty
function op:or-filter-stored-false() {
    doc('/db/boolean-ops-test/test2.xml')/id('accolti_pietro')/tei:persName[tei:surname1 or tei:forename1]
        /tei:surname/string()
};

declare 
    %test:assertEquals("Accolti")
function op:or-filter-boolean-stored() {
    doc('/db/boolean-ops-test/test2.xml')/id('accolti_pietro')/tei:persName[boolean(tei:surname or tei:forename)]
        /tei:surname/string()
};

declare 
    %test:assertEmpty
function op:or-filter-boolean-stored-false() {
    doc('/db/boolean-ops-test/test2.xml')/id('accolti_pietro')/tei:persName[boolean(tei:surname1 or tei:forename1)]
        /tei:surname/string()
};

declare 
    %test:assertEquals("Accolti")
function op:and-filter-boolean-stored() {
    doc('/db/boolean-ops-test/test2.xml')/id('accolti_pietro')/tei:persName[boolean(tei:surname and tei:forename)]
        /tei:surname/string()
};

declare 
    %test:assertEmpty
function op:and-filter-boolean-stored-false() {
    doc('/db/boolean-ops-test/test2.xml')/id('accolti_pietro')/tei:persName[boolean(tei:surname and tei:forename1)]
        /tei:surname/string()
};

declare 
    %test:assertEquals("Accolti")
function op:and-filter-if-stored() {
    doc('/db/boolean-ops-test/test2.xml')/id('accolti_pietro')
        /tei:persName[if (tei:surname and tei:forename) then true() else false()]
        /tei:surname/string()
};

declare 
    %test:assertEquals("Accolti")
function op:and-filter-var-stored() {
    let $name := doc('/db/boolean-ops-test/test2.xml')/id('accolti_pietro')/tei:persName
    return
        $name[tei:surname and tei:forename]/tei:surname/string()
};

declare 
    %test:assertEquals("Accolti")
function op:or-filter-var-stored() {
    let $name := doc('/db/boolean-ops-test/test2.xml')/id('accolti_pietro')/tei:persName
    return
        $name[tei:surname or tei:forename]/tei:surname/string()
};

(: Tests on in memory data :)

declare 
    %test:assertEquals("<test><result>true</result><result>true</result></test>")
function op:or-enclosed-path-in-mem() {
    let $x := $op:DATA1/root
    let $y := $op:DATA1/root
    return 
        <test>
            <result>{$x/foo or $x/bar}</result>
            <result>{$y/foo or $y/bar}</result> 
        </test>
};

declare 
    %test:assertEquals("<test><result>false</result><result>false</result></test>")
function op:or-enclosed-path-in-mem-false() {
    let $x := $op:DATA1/root
    let $y := $op:DATA1/root
    return 
        <test>
            <result>{$x/foo1 or $x/bar1}</result>
            <result>{$y/foo1 or $y/bar1}</result> 
        </test>
};

declare 
    %test:assertEquals("<test><result>true</result><result>true</result></test>")
function op:and-enclosed-path-in-mem() {
    let $x := $op:DATA1/root
    let $y := $op:DATA1/root
    return 
        <test>
            <result>{$x/foo and $x/bar}</result>
            <result>{$y/foo and $y/bar}</result> 
        </test>
};

declare 
    %test:assertEquals("<test><result>false</result><result>false</result></test>")
function op:and-enclosed-path-in-mem-false() {
    let $x := $op:DATA1/root
    let $y := $op:DATA1/root
    return 
        <test>
            <result>{$x/foo and $x/bar1}</result>
            <result>{$y/foo1 and $y/bar}</result> 
        </test>
};

declare 
    %test:assertEquals("Accolti, Pietro")
function op:and-if-path-let-in-mem() {
    let $node := $op:DATA2//tei:persName
    let $tr := if ($node/tei:surname and $node/tei:forename) then ", " else ""
    return 
        concat($node/tei:surname, $tr, $node/tei:forename)
};

declare 
    %test:assertEquals("both")
function op:and-if-path-in-mem() {
    let $node := $op:DATA2//tei:persName
    return
        if ($node/tei:surname and $node/tei:forename) then "both" else ""
};

declare 
    %test:assertEquals("true")
function op:and-path-in-mem() {
    let $node := $op:DATA2//tei:persName
    return
        $node/tei:surname and $node/tei:forename
};

declare 
    %test:assertEquals("false")
function op:and-path-in-mem-false() {
    let $node := $op:DATA2//tei:persName
    return
        $node/tei:surname and $node/tei:forename1
};

declare 
    %test:assertEquals("true")
function op:or-path-in-mem() {
    let $node := $op:DATA2//tei:persName
    return
        $node/tei:surname or $node/tei:forename
};

declare 
    %test:assertEquals("false")
function op:or-path-in-mem-false() {
    let $node := $op:DATA2//tei:persName
    return
        $node/tei:surname1 or $node/tei:forename1
};

declare 
    %test:assertEquals("Accolti")
function op:and-filter-in-mem() {
    $op:DATA2//tei:persName[tei:surname and tei:forename]
        /tei:surname/string()
};

declare
    %test:assertEquals("Accolti")
function op:or-filter-in-mem() {
    $op:DATA2//tei:persName[tei:surname or tei:forename]
        /tei:surname/string()
};

declare 
    %test:assertEmpty
function op:and-filter-in-mem-false() {
    $op:DATA2//tei:persName[tei:surname and tei:forename1]
        /tei:surname/string()
};

declare 
    %test:assertEmpty
function op:or-filter-in-mem-false() {
    $op:DATA2//tei:persName[tei:surname1 or tei:forename1]
        /tei:surname/string()
};

declare 
    %test:assertEquals("Accolti")
function op:or-filter-boolean-in-mem() {
    $op:DATA2//tei:persName[boolean(tei:surname or tei:forename)]
        /tei:surname/string()
};

declare 
    %test:assertEmpty
function op:or-filter-boolean-in-mem-false() {
    $op:DATA2//tei:persName[boolean(tei:surname1 or tei:forename1)]
        /tei:surname/string()
};

declare 
    %test:assertEquals("Accolti")
function op:and-filter-boolean-in-mem() {
    $op:DATA2//tei:persName[boolean(tei:surname and tei:forename)]
        /tei:surname/string()
};

declare 
    %test:assertEmpty
function op:and-filter-boolean-in-mem-false() {
    $op:DATA2//tei:persName[boolean(tei:surname and tei:forename1)]
        /tei:surname/string()
};

declare 
    %test:assertEquals("Accolti")
function op:and-filter-if-in-mem() {
    $op:DATA2
        //tei:persName[if (tei:surname and tei:forename) then true() else false()]
        /tei:surname/string()
};

declare 
    %test:assertEquals("Accolti")
function op:and-filter-var-in-mem() {
    let $name := $op:DATA2//tei:persName
    return
        $name[tei:surname and tei:forename]/tei:surname/string()
};

declare 
    %test:assertEquals("Accolti")
function op:or-filter-var-in-mem() {
    let $name := $op:DATA2//tei:persName
    return
        $name[tei:surname or tei:forename]/tei:surname/string()
};