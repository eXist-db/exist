xquery version "1.0";
(: $Id: table.xq 6434 2007-08-28 18:59:23Z ellefj $ :)
(: An example found in Saxon: creates a table with 10x10 cells :)

declare namespace f="http://my-namespaces.org";

declare function f:background-color($x as xs:double, $y as xs:integer)
as xs:string {	
	if($x mod 2 + $y mod 2 <= 0) then "lightgreen"
	else if($y mod 2 <= 0) then "yellow"
	else if($x mod 2 <= 0) then "lightblue"
	else "white"
};

<body>
	<table>{
	for $y in 1 to 10 return
		<tr>
		{
			for $x in 1 to 10 return
				let $bg := f:background-color($x, $y),
					$prod := $x * $y
				return
					<td bgcolor="{$bg}">
						{if ($y > 1 and $x > 1) then $prod else <b>{$prod}</b>}
					</td>
		}
		</tr>
	}</table>
</body>
