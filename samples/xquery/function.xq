declare function local:test($a as xs:double, $b as xs:double) {
	let $c := $a + $b
	return $c
};

for $i in 1 to 10
return <result value="{$i}">{(local:test($i, 10), local:test($i, 20))}</result>
