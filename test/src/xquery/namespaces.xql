xquery version "3.0";

module namespace nt="http://exist-db.org/xquery/test/namespaces";

declare namespace z="http://www.zorba-xquery.com/";
declare namespace ok="http://place-on-interwebz.com/a-ok";

import module namespace test="http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";

declare
    %test:assertEquals("<test xmlns:foo='http://foo.com'/>")
function nt:dynamicNSConstr1() {
    <test>
    { namespace foo { "http://foo.com" } }
    </test>
};

declare
    %test:assertEquals("<test xmlns:foo='http://foo.com'>bla</test>")
function nt:dynamicNSConstr2() {
    element { "test" } {
        namespace foo { "http://foo.com" },
        "bla"
    }
};

declare
    %test:assertEquals("<test xmlns:foo1='http://foo.com'/>")
function nt:dynamicNSConstr3() {
    <test>
    { namespace { "foo" || 1 } { "http://foo.com" } }
    </test>
};

declare
    %test:assertEquals("<html xmlns:ev='http://www.w3.org/2001/xml-events'></html>")
function nt:dynamicNSConstr4() {
    let $xml :=
        <html xmlns:ev="http://www.w3.org/2001/xml-events"></html>
    return
        element { node-name($xml) } {
            nt:copy-ns($xml)
        }
};

declare %private function nt:copy-ns($node) {
    for $prefix in in-scope-prefixes($node)
    return
        namespace { $prefix } { namespace-uri-for-prefix($prefix, $node) }
};

declare
    %test:assertEquals('<h:html xmlns="http://www.w3.org/1999/xhtml" xmlns:h="http://www.w3.org/1999/xhtml"/>')
function nt:dynamicNSConstrEmptyNS() {
    <h:html xmlns:h="http://www.w3.org/1999/xhtml">
        { namespace { "" } { "http://www.w3.org/1999/xhtml" } }
    </h:html>
};

declare
    %test:assertEquals("<age xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' xsi:type='xs:integer'>23</age>")
function nt:dynamicNSConstrAttrib1() {
    <age xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"> {
      namespace xs {"http://www.w3.org/2001/XMLSchema"},
      attribute xsi:type {"xs:integer"},
      23
    }</age>
};

declare
    %test:assertEquals("<age xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' xsi:type='xs:integer'>23</age>")
function nt:dynamicNSConstrAttrib2() {
    element age {
        namespace xsi { "http://www.w3.org/2001/XMLSchema-instance" },
        namespace xs {"http://www.w3.org/2001/XMLSchema"},
        attribute xsi:type {"xs:integer"},
        23
    }
};

declare
    %test:assertEquals('<e xmlns:saxon="http://saxon.sf.net/" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" a="23"/>')
function nt:nscons-001() {
    let $s := "http://saxon.sf.net/"
    let $xsl := "http://www.w3.org/1999/XSL/Transform"
    return
        <e>{ namespace saxon {$s}, attribute a {23}, namespace xsl {$xsl} }</e>
};

declare
    %test:assertEquals('<out><t:e xmlns:t="http://www.example.com/" xmlns="http://saxon.sf.net/" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" a="23"><f xmlns=""/></t:e></out>')
function nt:nscons-002() {
    let $s := "saxon"
    let $xsl := "xsl"
    return
        <out>
            <t:e xmlns:t="http://www.example.com/">{
            namespace {""} {"http://saxon.sf.net/"},
            attribute a {23},
            namespace {$xsl} {"http://www.w3.org/1999/XSL/Transform"}, <f/>
            }</t:e>
        </out>
};

declare
    %test:assertEquals('<out><t:e xmlns:t="http://www.example.com/" xmlns="http://saxon.sf.net/" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" a="23"><f xmlns=""/></t:e></out>')
function nt:nscons-003() {
    let $s := "saxon"
    let $xsl := "xsl"
    return
        <out>
            <t:e xmlns:t="http://www.example.com/">{
            namespace {""} {"http://saxon.sf.net/"},
            attribute a {23},
            namespace {$xsl} {"http://www.w3.org/1999/XSL/Transform"}, <f/> }</t:e>
        </out>
};

declare
    %test:assertEquals('<out><t:e xmlns:t="http://www.example.com/" xml:space="preserve" a="23"><f/></t:e></out>')
function nt:nscons-004() {
    let $s := "saxon"
    let $xml := "http://www.w3.org/XML/1998/namespace"
    return
        <out> <t:e xmlns:t="http://www.example.com/" xml:space="preserve">{
            namespace xml {"http://www.w3.org/XML/1998/namespace"},
            attribute a {23}, <f/> }</t:e> </out>
};

declare
    %test:assertEquals('<saxon:extension xmlns:saxon="http://saxon.sf.net/" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" a="23"><f>42</f></saxon:extension>')
function nt:nscons-005() {
    let $s := "http://saxon.sf.net/"
    let $xsl := "http://www.w3.org/1999/XSL/Transform"
    return
        element {QName("http://saxon.sf.net/", "saxon:extension")} { namespace saxon {$s}, attribute a {23}, namespace xsl {$xsl}, element f {42} }
};

declare
    %test:assertEquals('<saxon:extension xmlns:saxon="http://saxon.sf.net/" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" a="23"><f>42</f></saxon:extension>')
function nt:nscons-006() {
    let $s := "http://saxon.sf.net/"
    let $xsl := "http://www.w3.org/1999/XSL/Transform"
    return
        element {QName("http://saxon.sf.net/", "saxon:extension")}
        { namespace saxon {$s}, attribute a {23}, namespace xsl {$xsl}, namespace saxon {$s}, element f {42} }
};

declare
    %test:assertError("XQDY0101")
function nt:nscons-007() {
    let $s := "http://saxon.sf.net/"
    let $xsl := "http://www.w3.org/1999/XSL/Transform"
    let $xmlns := "xmlns"
    return
        <e> { namespace saxon {$s}, attribute a {23}, namespace xsl {$xsl}, namespace xmlns {$s}, element f {42} }</e>
};

declare
    %test:assertError("XQDY0101")
function nt:nscons-008() {
    let $s := "http://saxon.sf.net/"
    let $xsl := "http://www.w3.org/1999/XSL/Transform"
    let $xmlns := "xml"
    return
        <e> { namespace saxon {$s}, attribute a {23}, namespace xsl {$xsl}, namespace {$xmlns} {$s}, element f {42} }</e>
};

declare function nt:nscons-015() {
    <z:e>{ namespace { <a/>/* } { "http://www.w3.org/" } }</z:e>
};

declare
    %test:assertError
function nt:dynamicNSConstrError() {
    <test>
    { namespace { (1, 2) } { "http://foo.com" } }
    </test>
};

declare
    %test:assertEquals('<e xmlns:z="http://www.zorba-xquery.com/"/>')
function nt:ns-cons013() {
    let $pre := <prefix>z</prefix>,
        $uri := "http://www.zorba-xquery.com/"
    return
      <e>{ namespace { $pre } { $uri } }</e>
};

declare
    %test:assertEquals('<e xmlns:z="http://www.zorba-xquery.com/"/>')
function nt:ns-cons014() {
    let $pre := "z",
        $uri := "http://www.zorba-xquery.com/"
    return
      <e>{ namespace { $pre } { $uri } }</e>
};

declare
    %test:assertError("XQDY0074")
function nt:ns-cons016() {
    let $pre := <prefix>z:z</prefix>,
        $uri := "http://www.zorba-xquery.com/"
    return
      <e>{ namespace { $pre } { $uri } }</e>
};

declare
    %test:assertError("XQDY0074")
function nt:ns-cons17() {
    let $pre := "z z",
        $uri := "http://www.zorba-xquery.com/"
    return
      <e>{ namespace { $pre } { $uri } }</e>
};

declare
    %test:assertError("XPTY0004")
function nt:ns-cons18() {
    let $pre := 1,
        $uri := "http://www.zorba-xquery.com/"
    return
      <e>{ namespace { $pre } { $uri } }</e>
};

declare
    %test:assertEquals('<test xmlns:foo="http://foo.com"></test>')
function nt:dynamicNSNodeFromFunc() {
    <test>
    { nt:getNSNode() }
    </test>
};

declare function nt:getNSNode() {
    namespace { "foo" } { "http://foo.com" }
};

declare
    %test:assertError("XQDY0102")
function nt:cannot-override-no-ns() {
    element root {
        namespace {""} {"http://also-on-interwebz.com/problem"},
        namespace ok {"http://place-on-interwebz.com/a-ok"},
        for $n in 1 to 3
        return
            element stuff {$n}
    }
};

declare
    %test:assertEquals(3)
function nt:ns-default-constructor() {
    count(
        element ok:root {
            namespace {""} {"http://also-on-interwebz.com/problem"},
            namespace ok {"http://place-on-interwebz.com/a-ok"},
            for $n in 1 to 3
            return
                element stuff {$n}
        }/stuff
    )
};

declare
    %test:assertError("XPTY0004")
function nt:qname-rhs-compare() {
    'test' eq xs:QName('test')
};

declare
    %test:assertError("XPTY0004")
function nt:qname-lhs-compare() {
    xs:QName('test') eq 'test'
};

declare
    %test:assertError("XPTY0004")
function nt:int-string-compare() {
    5 eq 'five'
};

declare
    %test:assertError("XPTY0004")
function nt:string-int-compare() {
    'five' eq 5
};

declare
    %test:assertError("XPTY0004")
function nt:uri-int-compare() {
    xs:anyURI('5') eq 5
};

declare
    %test:assertError("XPTY0004")
function nt:int-uri-compare() {
    5 eq xs:anyURI('5')
};

declare
    %test:assertEquals("false")
function nt:right_empty_sequence-date() {
    current-date() = ()
};

declare
    %test:assertEquals("false")
function nt:right_empty_sequence-boolean() {
    false() = ()
};
