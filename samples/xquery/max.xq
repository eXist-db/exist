declare namespace f="http://foo.org/f";

(: Implements the standard library function max in XQuery :)
declare function max($srcval as item()+) as item() {
let $ordered :=
	for $val in $srcval order by $val
	return $val
return $ordered[last()]
};

<result>{max((1, 8, 2, 3, 0))}</result>
