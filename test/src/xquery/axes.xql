xquery version "3.0";

module namespace axes="http://exist-db.org/xquery/test/axes";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $axes:NESTED_DIVS :=
    <body>
        <div>
            <head>1</head>
            <div>
                <head>2</head>
                <div>
                    <head>3</head>
                </div>
            </div>
            <div>
                <head>4</head>
                <div>
                    <head>5</head>
                </div>
                <div>
                    <head>6</head>
                    <div>
                        <head>7</head>
                    </div>
                </div>
            </div>
        </div>
    </body>;

declare 
    %test:setUp
function axes:setup() {
    xmldb:create-collection("/db", "axes-test"),
    xmldb:store("/db/axes-test", "test.xml", $axes:NESTED_DIVS)
};

declare 
    %test:tearDown
function axes:cleanup() {
    xmldb:remove("/db/axes-test")
};

(: ---------------------------------------------------------------
 : Descendant axis: check if nested elements are handled properly.
 : Wrong evaluation may lead to duplicate nodes being returned.
   --------------------------------------------------------------- :)

declare 
    %test:assertEquals(6, 6)
function axes:descendant-axis-nested() {
    let $node := doc("/db/axes-test/test.xml")/body
    return (
        count($node/descendant::div/descendant::div),
        count($node//div//div)
    )
};

declare 
    %test:assertEquals("<head>1</head>")
function axes:descendant-axis-except-nested1() {
    let $node := doc("/db/axes-test/test.xml")/body
    return
        ($node//div except $node//div//div)/head
};

declare 
    %test:assertEquals("<head>2</head>", "<head>4</head>")
function axes:descendant-axis-except-nested2() {
    let $node := doc("/db/axes-test/test.xml")/body/div
    return
        ($node//div except $node//div//div)/head
};