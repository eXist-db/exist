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

module namespace xtj="http://exist-db.org/xquery/test/xml-to-json";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare
    %test:assertEmpty
function xtj:xml-to-json-empty-sequence() {
    let $node := ()
    return fn:xml-to-json($node)
};

declare
    %test:arg('arg1', '')
    %test:assertEquals('null')
    %test:arg('arg1', ' ')
    %test:assertError('FOJS0006')
function xtj:xml-to-json-null($arg1) {
    let $node := <null>{$arg1}</null>
    return fn:xml-to-json($node)
};

declare
    %test:arg('arg1','')
    %test:assertEquals('false')
    %test:arg('arg1','0')
    %test:assertEquals('false')
    %test:arg('arg1','1')
    %test:assertEquals('true')
function xtj:xml-to-json-boolean($arg1) {
    let $node := <boolean>{$arg1}</boolean>
    return fn:xml-to-json($node)
};

declare
    %test:arg('arg1', '')
    %test:assertError('FOJS0006')
    %test:arg('arg1', '0')
    %test:assertEquals('0.0')
    %test:arg('arg1', '1')
    %test:assertEquals('1.0')
    %test:arg('arg1', '-1')
    %test:assertEquals('-1.0')
    %test:arg('arg1', '01')
    %test:assertEquals('1.0') (: should error out for leading zeros according to spec :)
    %test:arg('arg1', '08')
    %test:assertEquals('8.0') (: should error out for leading zeros according to spec :)
    %test:arg('arg1', '3.1415')
    %test:assertEquals('3.1415')
    %test:arg('arg1', '0.31415e+1')
    %test:assertEquals('3.1415')
    %test:arg('arg1', '0.31415e1')
    %test:assertEquals('3.1415')
    %test:arg('arg1', '31.415e-1')
    %test:assertEquals('3.1415')
function xtj:xml-to-json-number($arg1) {
    let $node := <number>{$arg1}</number>
    return lower-case(fn:xml-to-json($node))
};

declare
    %test:arg('arg1', '')
    %test:assertEquals('""')
    %test:arg('arg1', ' ')
    %test:assertEquals('" "')
    %test:arg('arg1', 'a')
    %test:assertEquals('"a"')
    %test:arg('arg1', 'ab')
    %test:assertEquals('"ab"')
    %test:arg('arg1', '\')
    %test:assertEquals('"\\"')
    %test:arg('arg1', '"')
    %test:assertEquals('"\""')
    %test:arg('arg1', '&#10;')
    %test:assertEquals('"\n"')
    %test:arg('arg1', '/')
    %test:assertEquals('"/"')
(: TODO: needs implementation
    %test:arg('arg1', '&#127;')
    %test:assertEquals('"\u007F"')
:)
function xtj:xml-to-json-string-unescaped($arg1) {
    let $node := <string>{$arg1}</string>
    return fn:xml-to-json($node)
};

declare
    %test:arg('arg1', '')
    %test:assertEquals('""')
    %test:arg('arg1', ' ')
    %test:assertEquals('" "')
    %test:arg('arg1', 'a')
    %test:assertEquals('"a"')
    %test:arg('arg1', 'ab')
    %test:assertEquals('"ab"')
    %test:arg('arg1', '\n')
    %test:assertEquals('"\n"')
    %test:arg('arg1', '"')
    %test:assertError('FOJS0007')
    %test:arg('arg1', '&#10;')
    %test:assertError('FOJS0007')
(: TODO: needs implementation
    %test:arg('arg1', '/')
    %test:assertEquals('"\/"')
    %test:arg('arg1', ' /')
    %test:assertEquals('" \/"')
    %test:arg('arg1', '\/')
    %test:assertEquals('"\/"')
    %test:arg('arg1', '&#127;')
    %test:assertEquals('"\u007F"')
    %test:arg('arg1', '""')
    %test:assertEquals('"\"\""')
:)
function xtj:xml-to-json-string-escaped($arg1) {
    let $node := <string escaped="true">{$arg1}</string>
    return fn:xml-to-json($node)
};

declare
    %test:arg('arg1', '')
    %test:assertEquals('[]')
    %test:arg('arg1', ' ')
    %test:assertEquals('[]')
function xtj:xml-to-json-array($arg1) {
    let $node := <array>{$arg1}</array>
    return fn:xml-to-json($node)
};

declare
    %test:arg('arg1', '')
    %test:assertEquals('[null]')
function xtj:xml-to-json-array-1-element($arg1) {
    let $node := <array><null>{$arg1}</null></array>
    return fn:xml-to-json($node)
};

declare
    %test:arg('arg1', '')
    %test:assertEquals('[null,null]')
function xtj:xml-to-json-array-2-elements($arg1) {
    let $node := <array><null>{$arg1}</null><null>{$arg1}</null></array>
    return fn:xml-to-json($node)
};

declare
    %test:arg('arg1', '')
    %test:assertEquals('[null,null,null]')
function xtj:xml-to-json-array-3-elements($arg1) {
    let $node := <array><null>{$arg1}</null><null>{$arg1}</null><null>{$arg1}</null></array>
    return fn:xml-to-json($node)
};

declare
    %test:arg('arg1', '')
    %test:assertEquals('{}')
    %test:arg('arg1', ' ')
    %test:assertEquals('{}')
function xtj:xml-to-json-map-empty($arg1) {
    let $node := <map>{$arg1}</map>
    return fn:xml-to-json($node)
};

declare
    %test:arg('arg1', '')
    %test:assertEquals('{"":null}')
function xtj:xml-to-json-map-emptyKey-element($arg1) {
    let $node := <map><null key="">{$arg1}</null></map>
    return fn:xml-to-json($node)
};

declare
    %test:arg('arg1', '')
    %test:assertError('FOJS0006')
function xtj:xml-to-json-map-nokey-element($arg1) {
    let $node := <map><null>{$arg1}</null></map>
    return fn:xml-to-json($node)
};

declare
    %test:arg('arg1', '')
    %test:assertEquals('{"key1":null}')
function xtj:xml-to-json-map-key-nullElement($arg1) {
    let $node := <map><null key="key1">{$arg1}</null></map>
    return fn:xml-to-json($node)
};

declare
    %test:arg('arg1', 'key1')
    %test:arg('arg2', 'false')
    %test:assertEquals('{"key1":null}')
    %test:arg('arg1', 'key1')
    %test:arg('arg2', 'true')
    %test:assertEquals('{"key1":null}')
    %test:arg('arg1', 'ke\y1')
    %test:arg('arg2', 'false')
    %test:assertEquals('{"ke\\y1":null}')
    %test:arg('arg1', 'ke\y1')
    %test:arg('arg2', 'true')
    %test:assertError('FOJS0007')
function xtj:xml-to-json-map-escapedKey-element($arg1, $arg2) {
    let $node := <map><null key="{$arg1}" escaped-key="{$arg2}"></null></map>
    return fn:xml-to-json($node)
};

declare
    %test:arg('arg1', '0')
    %test:assertEquals('{"key1":false}')
    %test:arg('arg1', '1')
    %test:assertEquals('{"key1":true}')
function xtj:xml-to-json-map-key-element($arg1) {
    let $node := <map><boolean key="key1">{$arg1}</boolean></map>
    return fn:xml-to-json($node)
};

declare
    %test:arg('arg1', 'key1')
    %test:arg('arg2', '0')
    %test:arg('arg3', 'key2')
    %test:arg('arg4', '1')
    %test:assertEquals('true')
    %test:arg('arg1', '0')
    %test:arg('arg2', '0')
    %test:arg('arg3', '1')
    %test:arg('arg4', '1')
    %test:assertEquals('true')
    %test:arg('arg1', 'key1')
    %test:arg('arg2', '0')
    %test:arg('arg3', 'key1')
    %test:arg('arg4', '1')
    %test:assertError('FOJS0006')
function xtj:xml-to-json-map-multipleKeys-multipleElements($arg1, $arg2, $arg3, $arg4) {
    let $node := <map><boolean key="{$arg1}">{$arg2}</boolean><boolean key="{$arg3}">{$arg4}</boolean></map>
    let $rc := fn:xml-to-json($node)
    return
        if (compare($rc, concat('{"', $arg1, '":false,"', $arg3, '":true}')) = 0) then (
            'true'
        ) else if (compare($rc, concat('{"', $arg3, '":true,"', $arg1, '":false}"')) = 0) then (
            'true'
        ) else (
            'false'
        )
};

declare
    %test:arg('arg1', '')
    %test:assertEquals('[[]]')
function xtj:xml-to-json-array-array($arg1) {
    let $node := <array><array>{$arg1}</array></array>
    return fn:xml-to-json($node)
};

declare
    %test:arg('arg1', '')
    %test:assertEquals('[{}]')
function xtj:xml-to-json-array-map($arg1) {
    let $node := <array><map>{$arg1}</map></array>
    return fn:xml-to-json($node)
};

declare
    %test:arg('arg1', '')
    %test:assertEquals('{"":[]}')
function xtj:xml-to-json-map-emptyKey-array($arg1) {
    let $node := <map><array key="">{$arg1}</array></map>
    return fn:xml-to-json($node)
};

declare
    %test:arg('arg1', '')
    %test:assertEquals('null')
function xtj:xml-to-json-toplevelElementKey($arg1) {
    let $node := <null key="key1"/>
    return fn:xml-to-json($node)
};

declare
    %test:assertEquals('"<test> \\ </test>"')
function xtj:xml-to-json-xmlInJsonString() {
    let $node := <string>&lt;test&gt; \ &lt;/test&gt;</string>
    return fn:xml-to-json($node)
};

declare
    %test:assertEquals('{"pcM9qSs":"YbFYeK10.e01xgS1DEJFaxxvm372Ru","wh5J8qAmnZx8WAHnHCeBpM":-1.270212191431E9,"ssEhB3U9zZhRNNH2Vm":["A","OIQwg4ICB9fkzihwpE.cQv1",false]}')
function xtj:xml-to-json-generatedFromSchema-1() {
    let $node :=
<map xmlns="http://www.w3.org/2005/xpath-functions"
 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
    <string key="pcM9qSs" escaped-key="false" escaped="false">YbFYeK10.e01xgS1DEJFaxxvm372Ru</string>
    <number key="wh5J8qAmnZx8WAHnHCeBpM" escaped-key="false">-1270212191.431</number>
    <array key="ssEhB3U9zZhRNNH2Vm" escaped-key="false">
        <string escaped="false">A</string>
        <string escaped="false">OIQwg4ICB9fkzihwpE.cQv1</string>
        <boolean>0</boolean>
    </array>
</map>
    return fn:xml-to-json($node)
};

declare
    %test:assertEquals('{"v-DhbQUwZO3zpW":[{"fRcP.5e9btnuR3dOnd":[false,"_aQ",null],"yVlXSsyg1pPatQ7ilEaSSA9":"DVbrO2wpIRJimrskkRk.7wg1Gvh","K9xGofqp":true,"PatQ7iK9xGof":false},1.1145450201E7,5.84608693252E8],"IU6lSWbLYTzc3QvIVAdmJ.CG":1.600374222048E9,"_o3UT5zEy":"WFUwRRW5Jc3rdwKCoO8iV3RYDu_5"}')
function xtj:xml-to-json-generatedFromSchema-2() {
    let $node :=
<map xmlns="http://www.w3.org/2005/xpath-functions"
 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
    <array key="v-DhbQUwZO3zpW" escaped-key="false">
        <map>
            <array key="fRcP.5e9btnuR3dOnd" escaped-key="false">
                <boolean>0</boolean>
                <string escaped="false">_aQ</string>
                <null/>
            </array>
            <string key="yVlXSsyg1pPatQ7ilEaSSA9" escaped-key="false" escaped="false">DVbrO2wpIRJimrskkRk.7wg1Gvh</string>
            <boolean key="K9xGofqp" escaped-key="false">true</boolean>
            <boolean key="PatQ7iK9xGof" escaped-key="false">false</boolean>
        </map>
        <number>11145450.201</number>
        <number>584608693.252</number>
    </array>
    <number key="IU6lSWbLYTzc3QvIVAdmJ.CG" escaped-key="false">1600374222.048</number>
    <string key="_o3UT5zEy" escaped-key="false" escaped="false">WFUwRRW5Jc3rdwKCoO8iV3RYDu_5</string>
</map>
    return fn:xml-to-json($node)
};

declare
    %test:assertError('FOJS0006')
function xtj:xml-to-json-unsupportedElement() {
    let $node :=
<map xmlns="http://www.w3.org/2005/xpath-functions"
 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
    <my-element key=""></my-element>
</map>
    return fn:xml-to-json($node)
};

declare
    %test:assertEquals('[[],""]')
function xtj:xml-to-json-clearTextnodeBufferForNewElement() {
    let $node := <array><array> </array><string/></array>
    return fn:xml-to-json($node)
};
