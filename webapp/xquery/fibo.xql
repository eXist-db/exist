xquery version "1.0";
(: $Id: fibo.xq 6434 2007-08-28 18:59:23Z ellefj $ :)
(: computes the first 10 fibonacci numbers :)

declare namespace f="http://exist-db.org/NS/fibo";

declare function f:fibo($n as xs:integer) as item() {
	if ($n = 0)
	then 0
	else if ($n = 1)
	then 1
	else (f:fibo($n - 1) + f:fibo($n -2))
};

<html>
	<head><title>Fibonacci Numbers</title></head>
	<body>
		<table cellpadding="5" cellspacing="0">
			{
			for $n in 1 to 10
			return
				<tr>
					<td bgcolor="#F0F0F0">{$n}</td>
					<td bgcolor="#ACACAC" align="right">{f:fibo($n)}</td>
				</tr>
			}
		</table>
	</body>
</html>
