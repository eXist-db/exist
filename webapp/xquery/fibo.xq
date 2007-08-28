xquery version "1.0";
(: $Id$ :)
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

		<p>
			<small>View <a href="source/fibo.xq">source code</a>
			</small>
		</p>
	</body>
</html>
