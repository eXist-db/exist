module namespace seq="http://exist-db.org/xquery/sequences";

import module namespace util="http://exist-db.org/xquery/util";

(: 	Applies $func element-wise to the elements
	of two sequences and returns the results as 
	a new sequence. Both input sequences should
	have the same length.
:)
declare function seq:map($func as function, $seqA as item()*, $seqB as item()*) 
as item()* {
	if(count($seqA) != count($seqB)) then
		error("The sequences passed to seq:map should have equal length.")
	else
    	for $a at $i in $seqA
    	let $b := $seqB[$i]
    	return
        	util:call($func, $a, $b)
};

(:	Calls $func for all items in the sequence. For example, if
	the sequence is (1, 2, 3), $func will be called with arguments
	(1, 2) and (3, 3).
:)
declare function seq:fold($func as function, $seq as item()*, $start as item()) {
	if(empty($seq)) then
		$start
	else
		seq:fold($func, remove($seq, 1), util:call($func, $start, $seq[1]))
};

(:	Filters the passed sequence by calling $func for every
	item. All items for which $func returns true are copied
	to the output sequence, items for which it returns false
	are skipped.
:)
declare function seq:filter($func as function, $seq as item()*) 
as item()* {
	for $i in $seq
	return
		if(util:call($func, $i)) then
			$i
		else
			()
};