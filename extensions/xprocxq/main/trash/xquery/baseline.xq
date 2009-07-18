xquery version "1.0" encoding "UTF-8";

(: Module Imports :)
import module namespace test = "http://xproc.net/test"
                        at "../../test/xquery/test.xqm";

(: :)
declare variable $source := document{*};


(: -------------------------------------------------------------------------- :)

<testsuite title="Test Unit Tests" desc="This test suite tests the actual Unit Testing package itself ;) e.g. XProc Unit Testing suite">


<test>
    <name>generate failed test using assertXMLEqual</name>
    <result>
    {test:assertXMLEqual(<test/>,<test/>)}
    </result>
    <expected>false</expected>
</test>

<test>
    <name>test successful assertXMLEqual</name>
    <result>
    {test:assertXMLEqual(<test/>,<test/>)}
    </result>
    <expected>true</expected>
</test>

<test>
    <name>test failed assertXMNotEqual</name>
    <result>
    {test:assertXMLNotEqual(<test id='1'/>,<test/>)}
    </result>
    <expected>true</expected>
</test>

<test>
    <name>test failed assertXMLNotEqual</name>
    <result>
    {test:assertXMLNotEqual(<test id='1'/>,<test/>)}
    </result>
    <expected>true</expected>
</test>

<test>
    <name>test assertStringEqual</name>
    <result>
    {test:assertStringEqual('a','a')}
    </result>
    <expected>true</expected>
</test>

<test>
    <name>test assertStringEqual</name>
    <result>
    {test:assertStringEqual('ab','a')}
    </result>
    <expected>false</expected>
</test>

<test>
    <name>test assertStringNotEqual</name>
    <result>
    {test:assertStringNotEqual('ab','a')}
    </result>
    <expected>true</expected>
</test>

<test>
    <name>test assertStringContain</name>
    <result>
    {test:assertStringContain('abcdef','a')}
    </result>
    <expected>true</expected>
</test>

<test>
    <name>test assertStringContain</name>
    <result>
    {test:assertStringContain('abcdef','g')}
    </result>
    <expected>false</expected>
</test>

<test>
    <name>test assertStringNotContain</name>
    <result>
    {test:assertStringNotContain('abcdef','g')}
    </result>
    <expected>true</expected>
</test>

<test>
    <name>test assertXpathExist</name>
    <result>
    {test:assertXpathExist(document{<test><a><b/></a></test>},"//b")}
    </result>
    <expected>true</expected>
</test>

<test>
    <name>test assertXpathNotExist</name>
    <result>
    {test:assertXpathNotExist(document{<test><a><b/></a></test>},"//c")}    
    </result>
    <expected>true</expected>
</test>


</testsuite>

(: -------------------------------------------------------------------------- :)