xquery version "1.0";

import module namespace xdb="http://exist-db.org/xquery/xmldb";

declare namespace t="http://exist-db.org/xquery/testing";

declare function t:setup-action($action) {
    typeswitch ($action)
        case element(create-collection) return
            xdb:create-collection($action/@parent, $action/@name)
        case element(store) return
            xdb:store($action/@collection, $action/@name, $action/*[1], "text/xml")
        case element(remove-collection) return
            xdb:remove($action/@collection)
        default return
            ()
};

declare function t:setup($setup as element(setup)) {
    for $action in $setup/*
    return
        t:setup-action($action)
};

declare function t:tearDown($tearDown as element(tearDown)) {
    for $action in $tearDown/*
    return
        t:setup-action($action)
};

declare function t:run-test($test as element(test), $count as xs:integer) {
    let $output := util:catch("*", util:eval($test/code/string()), <error>Compilation error: {$util:exception-message}</error>)
    let $expected := 
        if ($test/@output eq 'text') then 
            data($test/expected)
        else $test/expected/node()
    let $OK :=
        if ($test/@output eq 'text') then
            normalize-space(string-join(for $x in $output return string($x),' ')) eq normalize-space($expected)
        else
            deep-equal($output, $expected)
    return
        <test n="{$count}" pass="{$OK}">
        {
            if (not($OK)) then
                ($test/task, $test/expected, <result>{$output}</result>)
            else ()
        }
        </test>
};

declare function t:run-testSet($set as element(TestSet)) {
    let $null := t:setup($set/setup)
    let $result :=
        <TestSet>
        {
            for $test at $p in $set/test
            return
                t:run-test($test, $p)
        }
        </TestSet>
    let $null := t:tearDown($set/tearDown)
    return $result
};

t:run-testSet(doc("/db/test/axes.xml")/TestSet)
