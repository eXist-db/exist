(:
 : eXist-db Open Source Native XML Database
 : Copyright (C) 2001 The eXist-db Authors
 :
 : info@exist-db.org
 : http://www.exist-db.org
 :
 : This library is free software; you can redistribute it and/or
 : modify it under the terms of the GNU Lesser General Public
 : License as published by the Free Software Foundation; either
 : version 2.1 of the License, or (at your option) any later version.
 :
 : This library is distributed in the hope that it will be useful,
 : but WITHOUT ANY WARRANTY; without even the implied warranty of
 : MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 : Lesser General Public License for more details.
 :
 : You should have received a copy of the GNU Lesser General Public
 : License along with this library; if not, write to the Free Software
 : Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 :)
xquery version "3.1";

module namespace testTransform="http://exist-db.org/xquery/test/function_transform";
import module namespace xmldb="http://exist-db.org/xquery/xmldb";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $testTransform:doc := document {
<TEI xmlns="http://www.tei-c.org/ns/1.0" xml:id="output">
    <teiHeader>
        <fileDesc>
            <titleStmt>
                <title/>
            </titleStmt>
            <publicationStmt>
                <p>Test document</p>
            </publicationStmt>
            <sourceDesc>
                <p>born digital</p>
            </sourceDesc>
        </fileDesc>
    </teiHeader>
    <text>
        <front>
            <div xml:id="pressrelease" type="section" subtype="press-release">
                <head>Press Release</head>
            </div>
        </front>
        <body>
            <div type="compilation" xml:id="comp">
                <head>Main Book</head>
                <div type="chapter" xml:id="ch1">
                    <head>Chapter One</head>
                    <div type="document" n="1" xml:id="d1"/>
                    <div type="document" n="2" xml:id="d2"/>
                    <div type="document" n="3" xml:id="d3"/>
                    <div type="document" n="4" xml:id="d4"/>
                    <div type="document" n="5" xml:id="d5"/>
                    <div type="document" n="6" xml:id="d6"/>
                    <div type="document" n="7" xml:id="d7"/>
                </div>
                <div type="chapter" xml:id="ch2">
                    <head>Chapter Two</head>
                    <div type="document" n="8" xml:id="d8"/>
                    <div type="document" n="9" xml:id="d9"/>
                    <div type="document" n="10" xml:id="d10"/>
                    <div type="document" n="11" xml:id="d11"/>
                    <div type="document" n="12" xml:id="d12"/>
                    <div type="document" n="13" xml:id="d13"/>
                    <div type="document" n="14" xml:id="d14"/>
                    <div type="document" n="15" xml:id="d15"/>
                </div>
                <div type="chapter" xml:id="ch3">
                    <head>Chapter Three</head>
                    <div type="document" n="16" xml:id="d16"/>
                    <div type="document" n="17" xml:id="d17"/>
                    <div type="document" n="18" xml:id="d18"/>
                    <div type="document" n="19" xml:id="d19"/>
                    <div type="document" n="20" xml:id="d20"/>
                    <div type="document" n="21" xml:id="d21"/>
                </div>
                <div type="chapter" xml:id="ch4">
                    <head>Chapter Four</head>
                    <div type="document" n="22" xml:id="d22"/>
                    <div type="document" n="23" xml:id="d23"/>
                    <div type="document" n="24" xml:id="d24"/>
                    <div type="document" n="25" xml:id="d25"/>
                    <div type="document" n="26" xml:id="d26"/>
                    <div type="document" n="27" xml:id="d27"/>
                    <div type="document" n="28" xml:id="d28"/>
                </div>
            </div>
        </body>
    </text>
</TEI>
};

declare
    %test:assertEquals("<div class=""toc""><div class=""toc__header""><h4 class=""title"">Contents</h4></div><nav aria-label=""Side navigation,,,"" class=""toc__chapters""><ul class=""chapters js-smoothscroll""><li data-tei-id=""pressrelease""><a href=""/output/pressrelease"">Press Release</a></li><li data-tei-id=""comp""><a href=""/output/comp"">Main Book</a> (Documents 1 - 28)<ul class=""chapters__nested""><li data-tei-id=""ch1"" data-tei-documents=""d1 d2 d3 d4 d5 d6 d7""><a href=""/output/ch1"">Chapter One</a> (Documents 1 - 7)</li><li data-tei-id=""ch2"" data-tei-documents=""d8 d9 d10 d11 d12 d13 d14 d15""><a href=""/output/ch2"">Chapter Two</a> (Documents 8 - 15)</li><li data-tei-id=""ch3"" data-tei-documents=""d16 d17 d18 d19 d20 d21""><a href=""/output/ch3"">Chapter Three</a> (Documents 16 - 21)</li><li data-tei-id=""ch4"" data-tei-documents=""d22 d23 d24 d25 d26 d27 d28""><a href=""/output/ch4"">Chapter Four</a> (Documents 22 - 28)</li></ul></li></ul></nav></div>")
function testTransform:issue-4609() {
    let $create-collection := xmldb:create-collection("/db", "fn_transform_issue_4609")
    let $doc-store := xmldb:store("/db/fn_transform_issue_4609", "input.xml", $testTransform:doc)

    (: this works :)
    let $result := ( fn:transform(map{
        "stylesheet-location": 'resource:/org/exist/xquery/tei-toc.xsl',
        "source-node":      doc('/db/fn_transform_issue_4609/input.xml')
    }))
    return $result?output
};
