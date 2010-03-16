xquery version "1.0";

declare namespace xspec = "http://www.jenitennison.com/xslt/xspec";

declare function xspec:generate-xquery-tests($xspec){
(: apply xslt :)
};

declare function xspec:invoke-xquery($xquery){
(: run xquery and returns xml :)
};

declare function xspec:format-result($result){
(: run xquery and returns xml :)
};

declare function xspec:run-test($xspec){
    let $xquery := xspec:generate-xquery-test($xspec)
    let $result : = $xquery
    return
        xspec:format-result($result)
};