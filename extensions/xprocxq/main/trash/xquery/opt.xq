xquery version "1.0" encoding "UTF-8";

(: Module Imports :)
import module namespace test = "http://xproc.net/test"
                        at "../../test/xquery/test.xqm";
import module namespace opt = "http://xproc.net/xproc/opt"
                        at "../../src/xquery/opt.xqm";

(: :)
declare variable $source := document{*};


(: -------------------------------------------------------------------------- :)

<testsuite title="XProc Optional Step XQuery Unit Tests" desc="Test the XProc.xq optional step XQuery functions">

<test>
    <name>run successful test</name>
    <result>
    {true()}
    </result>
    <expected>true</expected>
</test>

</testsuite>

(: -------------------------------------------------------------------------- :)