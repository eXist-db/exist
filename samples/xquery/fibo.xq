xquery version "1.0";

(: this script computes the first 20 fibonacci numbers :)

declare namespace f="http://exist-db.org/NS/fibo";

declare function f:fibo($n as xs:integer) as item() {
	if ($n = 0)
	then 0
	else if ($n = 1)
	then 1
	else (f:fibo($n - 1) + f:fibo($n - 2))
};

for $n in 1 to 10
return
	<fibo n="{$n}">{ f:fibo($n) }</fibo>
