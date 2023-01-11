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

module namespace fixtures="http://exist-db.org/xquery/test/util/fixtures";

(: simple xml :)

declare variable $fixtures:XML := document {<foo><bar/></foo>};

declare variable $fixtures:SIMPLE_XML_INDENTED :=
"<foo>" || $fixtures:NL ||
"    <bar/>" || $fixtures:NL ||
"</foo>"
;

declare variable $fixtures:SIMPLE_XML_UNINDENTED := "<foo><bar/></foo>";

(: more complex xml :)

declare variable $fixtures:COMPLEX_XML :=
    <root>
        <item>This is a very long line. Certainly longer than eighty characters. It is here to see what happens to lines longer than a certain limit. Let's see.</item>
        <empty-item></empty-item>
        <unary />
        <item>

            MIXED CONTENT&#10;
            <nested
                attr = "with lots of whitespace"
            />
            The next word <hi>is</hi> highlighted.
        </item>
        <p/>
    </root>
;

(: FIXME(JL) cannot use StringConstructor here because that will cause all comparisons to fail on Windows :)
(: see https://github.com/eXist-db/exist/issues/4301 :)
declare variable $fixtures:COMPLEX_XML_INDENTED :=
"<root>" || $fixtures:NL ||
"    <item>This is a very long line. Certainly longer than eighty characters. It is here to see what happens to lines longer than a certain limit. Let's see.</item>"  || $fixtures:NL ||
"    <empty-item/>" || $fixtures:NL ||
"    <unary/>" || $fixtures:NL ||
"    <item>" || $fixtures:NL ||
$fixtures:NL ||
"            MIXED CONTENT" || $fixtures:NL ||
$fixtures:NL ||
"            <nested attr=""with lots of whitespace""/>" || $fixtures:NL ||
"            The next word <hi>is</hi> highlighted." || $fixtures:NL ||
"        </item>" || $fixtures:NL ||
"    <p/>" || $fixtures:NL ||
"</root>"
;

declare variable $fixtures:COMPLEX_XML_UNINDENTED :=
"<root>" ||
"<item>This is a very long line. Certainly longer than eighty characters. It is here to see what happens to lines longer than a certain limit. Let's see.</item>" ||
"<empty-item/>" ||
"<unary/>" ||
"<item>" || $fixtures:NL ||
$fixtures:NL ||
"            MIXED CONTENT" || $fixtures:NL ||
$fixtures:NL ||
"            <nested attr=""with lots of whitespace""/>" || $fixtures:NL ||
"            The next word <hi>is</hi> highlighted." || $fixtures:NL ||
"        </item>" ||
"<p/>" ||
"</root>"
;


declare variable $fixtures:TXT :=
``[12 12
This is just a Text
]``
;

declare variable $fixtures:XQY := "xquery version ""3.1""; 0 to 9";
declare variable $fixtures:BIN := "To bin or not to bin...";

(: other constants :)

declare variable $fixtures:XML_DECLARATION := '<?xml version="1.0" encoding="UTF-8"?>';
declare variable $fixtures:NL := "&#10;";

(: modification dates :)

declare variable $fixtures:now := current-dateTime();
declare variable $fixtures:mod-date := $fixtures:now;
declare variable $fixtures:mod-date-2 := $fixtures:now + xs:dayTimeDuration('PT2H');

(: collections :)

declare variable $fixtures:collection-name := "file-module-test";
declare variable $fixtures:child-collection-name := "data";
declare variable $fixtures:collection := "/db/" || $fixtures:collection-name;
declare variable $fixtures:child-collection := $fixtures:collection || "/" || $fixtures:child-collection-name;

(: file sync results :)

declare variable $fixtures:ALL-UPDATED := ("test-text.txt", "test-query.xq", "bin", "test-data.xml");

declare variable $fixtures:ROOT-FS := ("bin", "test-text.txt", "test-query.xq", "data");

declare variable $fixtures:EXTRA-DATA := ("test", ".env");

declare variable $fixtures:ROOT-FS-EXTRA := ("test", "bin", ".env", "test-text.txt", "test-query.xq", "data");
