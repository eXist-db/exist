xquery version "1.0";

import module namespace t="http://exist-db.org/xquery/testing";

let $test := <TestSet>
    <testName>Example test</testName>
    <description>
        <p>Testing the number of paragraphs</p>
        <author>James Fuller</author>
    </description>
    <setup>
        <create-collection parent="/db" name="test"/>
        <store collection="/db/test" name="data.xml">
            <root>
                <para>This is a paragraph</para>
            </root>
        </store>
    </setup>
    <tearDown>
        <remove-collection collection="/db/test"/>
    </tearDown>
    <test output="text">
        <task>Simple following axis test</task>
        <code>count(doc("/db/test/data.xml")//para)</code>
        <expected>1</expected>
    </test>

 </TestSet>
return
    t:run-testSet($test, ())