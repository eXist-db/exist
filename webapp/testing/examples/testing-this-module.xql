module namespace ex = "http://example.org/ns/my";

declare function ex:square($n as xs:integer) as xs:integer{
$n * $n
};
