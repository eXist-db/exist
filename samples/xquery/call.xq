declare namespace f="urn:my-functions";

declare function f:apply($func, $seq, $start) {
	if(empty($seq)) then
		$start
	else
		f:apply($func, remove($seq, 1), util:call($func, $start, $seq[1]))
};

declare function f:plus($a, $b) {
	$a + $b
};

declare function f:product($a, $b) {
	$a * $b
};

f:apply(util:function("f:product", 2), 1 to 4, 1)
