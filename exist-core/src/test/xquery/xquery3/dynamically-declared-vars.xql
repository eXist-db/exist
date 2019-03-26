xquery version "3.1";

module namespace devar="http://exist-db.org/xquery/test/declared-variables";
declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $devar:static := 'I am static.';
declare variable $devar:dynamic := replace($devar:static, 'static', 'dynamic');
declare variable $devar:function := (function ($a) {
    replace($a, 'static', 'the return of a function expression')
})($devar:static);

declare variable $devar:arrow := $devar:static
=> replace('static', 'the return of an arrow expression')
;

declare
%test:assertEquals('I am static.')
function devar:read-static () {
    $devar:static
};

declare
%test:assertEquals('I am dynamic.')
function devar:read-dynamic () {
    $devar:dynamic
};

declare
%test:assertEquals('I am the return of a function expression.')
function devar:read-function () {
    $devar:function
};

declare
%test:assertEquals('I am the return of an arrow expression.')
function devar:read-arrow () {
    $devar:arrow
};