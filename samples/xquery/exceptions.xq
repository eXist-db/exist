xquery version "1.0";

util:catch("org.exist.xquery.XPathException",
    util:eval("$undef"),
    <error>An error occurred: {$util:exception-message}.</error>
)
