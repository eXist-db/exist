xquery version "1.0" encoding "UTF-8";

(: Module Imports :)
import module namespace test = "http://xproc.net/test"
                        at "../../test/xquery/test.xqm";
import module namespace comp = "http://xproc.net/xproc/comp"
                        at "../../src/xquery/comp.xqm";

(: :)
declare variable $source := document{*};



(: -------------------------------------------------------------------------- :)

<testsuite title="XProc Component XQuery Unit Tests" desc="Test the XProc.xq components XQuery functions">

<test>
    <name>run p:episode</name>
    <result>
    {comp:episode()}
    </result>
    <expected>A23afe23r2q34fq</expected>
</test>

<test>
    <name>run p:product-name</name>
    <result>
    {comp:product-name()}
    </result>
    <expected>xproc.xq</expected>
</test>

<test>
    <name>run p:product-version</name>
    <result>
    {comp:product-version()}
    </result>
    <expected>0.5</expected>
</test>

<test>
    <name>run p:vendor</name>
    <result>
    {comp:vendor()}
    </result>
    <expected>James Fuller</expected>
</test>

<test>
    <name>run p:vendor-uri</name>
    <result>
    {comp:vendor-uri()}
    </result>
    <expected>http://www.xproc.net/xproc.xq</expected>
</test>

<test>
    <name>run p:version</name>
    <result>
    {comp:version()}
    </result>
    <expected>0.5</expected>
</test>

<test>
    <name>run p:xpath-version</name>
    <result>
    {comp:xpath-version()}
    </result>
    <expected>2.0</expected>
</test>

</testsuite>

(: -------------------------------------------------------------------------- :)