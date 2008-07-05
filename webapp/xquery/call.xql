xquery version "1.0";
(: $Id$ :)
(:  This example demonstrates the use of higher-order functions.
    It uses the library module sequences.xq, which is found in 
    src/org/exist/xquery/lib and is loaded through the Java class 
    loader.
    
    Higher-order functions are functions which take other functions
    as arguments. Three examples are shown here: fold, map and filter.
    
    These functions are the (basic) XQuery equivalents of functions known from 
    the standard library of the Scheme programming language.
:)

import module namespace seq="http://exist-db.org/xquery/sequences" at "resource:org/exist/xquery/lib/sequences.xq";

declare namespace f="urn:my-functions";

declare function f:plus($a as xs:integer, $b as xs:integer) {
	$a + $b
};

declare function f:product($a as xs:integer, $b as xs:integer) {
	$a * $b
};

declare function f:remove($item as xs:integer) as xs:boolean {
	$item gt 10
};

declare function f:table-row($a, $b) {
	<tr>
		<td>{$a}</td>
		<td>{$b}</td>
	</tr>
};

<html>
    <head>
        <title>Higher-order Functions</title>
    </head>
    <body>
        <h3>Calculate the sum of numbers 1 to 4:</h3>
        <pre>seq:fold(util:function(xs:QName("f:plus"), 2), 1 to 4, 0)</pre>
        <p>Result:</p>
    	<p>{seq:fold(util:function(xs:QName("f:plus"), 2), 1 to 4, 0)}</p>
    
        <h3>Calculate the product of numbers 1 to 4:</h3>
        <pre>seq:fold(util:function(xs:QName("f:product"), 2), 1 to 4, 1)</pre>
    	<p>Result:</p>
    	<p>{seq:fold(util:function(xs:QName("f:product"), 2), 1 to 4, 1)}</p>
    
        <h3>Adding the elements of sequences (4, 7, 6) and (10, 15, 8) element-wise:</h3>
        <pre>seq:map(util:function(xs:QName("f:plus"), 2), (4, 7, 6), (10, 15, 8))</pre>
    	<p>Result:</p> 
    	<p>{seq:map(util:function(xs:QName("f:plus"), 2), (4, 7, 6), (10, 15, 8))}</p>
    
    	<h3>Generating a table from the elements of sequences: (1, 2, 3) and
    	("Franz", "Klaus", "Erwin"):</h3>
    	<pre>seq:map(util:function(xs:QName("f:table-row"), 2), 1 to 3, ("Franz", "Klaus", "Erwin"))</pre>
        <p>Result:</p>
    	<table>
    		{seq:map(util:function(xs:QName("f:table-row"), 2), 1 to 3, ("Franz", "Klaus", "Erwin"))}
    	</table>
    
    	<h3>Filter out all items greater than 10:</h3>
    	<pre>seq:filter(util:function(xs:QName("f:remove"), 1), (8, 15, 7, 19, 22, 5))</pre>
    	<p>Result:</p>
    	<p>{seq:filter(util:function(xs:QName("f:remove"), 1), (8, 15, 7, 19, 22, 5))}</p>
    	<br/>
    	<p>
    		<small>View <a href="call.xql?_source=yes">source code</a></small>
		</p>
    </body>
</html>