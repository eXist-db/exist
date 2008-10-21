module namespace kwic="http://exist-db.org/xquery/kwic";

declare variable $kwic:CHARS_SUMMARY := 120;
declare variable $kwic:CHARS_KWIC := 40;

(:~
	Retrieve the following and preceding text chunks for a given match.

	@param $match the text node containing the match
	@param $mode the selection mode: either "previous" or "following"
:)
declare function kwic:get-context($match as element(exist:match), $mode as xs:string) as node()* {
	let $sibs := 
		if ($mode eq 'previous') then 
			$match/preceding::text()
		else
			$match/text()/following::text()
	for $sib in $sibs
	return
		if ($sib/parent::exist:match) then
			<span class="hi">{$sib}</span>
		else
			$sib
};

(:~
	Like fn:substring, but takes a node argument. If the node is an element,
	a new element is created with the same node-name as the old one and the
	shortened text content.
:)
declare function kwic:substring($node as node(), $start as xs:int, $count as xs:int) as item()? {
	let $str := substring($node, $start, $count)
	return
		if ($node instance of element()) then
			element { node-name($node) } { $str }
		else
			$str
};

(:~
	Generate the left-hand context of the match. Returns a sequence of nodes
	and strings, whose total string length is less than or equal to $max characters.

	Note: this function calls itself recursively until $nodes is empty or
	the returned sequence has the desired total string length.
:)
declare function kwic:truncate-previous($nodes as node()*, $truncated as item()*, 
	$max as xs:int, $chars as xs:int) {
	if ($nodes) then
		let $next := $nodes[last()]
		return
			if ($chars + string-length($next) gt $max) then
				let $remaining := $max - $chars
				return
					("...", kwic:substring($next, string-length($next) - $remaining, $remaining), $truncated)
			else
				kwic:truncate-previous(subsequence($nodes, 1, count($nodes) - 1), ($next, $truncated),
					$max, $chars + string-length($next))
	else
		$truncated
};

(:~
	Generate the right-hand context of the match. Returns a sequence of nodes
	and strings, whose total string length is less than or equal to $max characters.
	
	Note: this function calls itself recursively until $nodes is empty or
	the returned sequence has the desired total string length.
:)
declare function kwic:truncate-following($nodes as node()*, $truncated as item()*, 
	$max as xs:int, $chars as xs:int) {
	if ($nodes) then
		let $next := $nodes[1]
		return
			if ($chars + string-length($next) gt $max) then
				let $remaining := $max - $chars
				return
					($truncated, kwic:substring($next, 1, $remaining), "...")
			else
				kwic:truncate-following(subsequence($nodes, 2), ($truncated, $next),
					$max, $chars + string-length($next))
	else
		$truncated
};

(:~
	Computes the total string length of the nodes in the argument sequence
:)
declare function kwic:string-length($nodes as item()*) as xs:int {
	if (exists($nodes)) then
		sum(for $n in $nodes return string-length($n))
	else
		0
};

(:~
	Print a summary of the match in $node. Output a predefined amount of text to
	the left and the right of the match.

	@param $root the root element containing the match. This is the original element
		   stored in the database.
	@param $node the exist:match element to process.
	@param $config configuration element which determines the behaviour of the function
:)
declare function kwic:get-summary($root as node(), $node as element(exist:match), 
	$config as element(config)) as element() {
	let $chars := xs:int($config/@width)
	let $table := $config/@table = ('yes', 'true')
	let $prev := kwic:get-context($node, 'previous')
	let $prevTrunc := kwic:truncate-previous($prev, (), $chars, 0)
	let $remain := 
		if (not($table)) then 
			$chars * 2 - kwic:string-length($prevTrunc)
		else
			$chars
	let $following := kwic:get-context($node, 'following')
	let $followingTrunc := kwic:truncate-following($following, (), $remain, 0)
	return
		if (not($table)) then
			<p xmlns="http://www.w3.org/1999/xhtml"> 
				<span class="previous">{$prevTrunc}</span>
				{
					if ($config/@link) then
						<a class="hi" href="{$config/@link}">{ $node/text() }</a>
					else
						<span class="hi">{ $node/text() }</span>
				}
				<span class="following">{$followingTrunc}</span>
			</p>
		else
			<tr xmlns="http://www.w3.org/1999/xhtml">
				<td class="previous">{$prevTrunc}</td>
				<td class="hi">
				{
					if ($config/@link) then
						<a href="{$config/@link}">{$node/text()}</a>
					else
						$node/text()
				}
				</td>
				<td class="following">{$followingTrunc}</td>
			</tr>
};

(:~
	Main function of the KWIC module: takes the passed element and returns an 
	XHTML fragment containing a chunk of text before and after the first full text
	match in the node.

	The optional config parameter is used to configure the behaviour of the function:
	
	&lt;config width="character width" table="yes|no" link="URL to which the match is linked"/&gt;

	By default, kwic:summarize returns an XHTML fragment with the following structure:

	&lt;p xmlns="http://www.w3.org/1999/xhtml"&gt;
		&lt;span class="previous"&gt;Text before match&lt;/span&gt;
		&lt;a href="passed URL if any" class="hi"&gt;The highlighted term&lt;/a&gt;
		&lt;span class="following"&gt;Text after match&lt;/span&gt;
	&lt;/p&gt;

	If table=yes is passed with the config element, a tr table row will be returned instead
	of a span (using the same class names).

	@param $hit an arbitrary XML element which has been selected by one of the full text
		operations or an ngram search.
	@param $config configuration element to configure the behaviour of the function
:)
declare function kwic:summarize($hit as element(), $config as element(config))
as element()* {
	let $expanded := util:expand($hit)
	for $match in $expanded//exist:match[1]
	return
		kwic:get-summary($hit, $match, $config)
};
