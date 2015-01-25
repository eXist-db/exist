(:~
    KWIC module: formats query results to display <em>keywords in context</em> (KWIC). A configurable
    amount of text is displayed to the left and right of a matching keyword (or phrase).
    
    The module works with all indexes that support match highlighting (matches are tagged
    with an &lt;exist:match&gt; element). This includes the old full text index, the new 
    Lucene-based full text index, as well as the NGram index.
    
    The <b>kwic:summarize()</b> function represents the main entry point into the module.
    To have more control over the text extraction context, you can also call 
    <b>kwic:get-summary()</b> instead. For example, the following snippet will only print the
    first match within a given set of context nodes ($ancestor):
    
    <pre>
    let $matches := kwic:get-matches($hit)<br/>
    for $ancestor in $matches/ancestor::para | $matches/ancestor::title | $matches/ancestor::td<br/>
    return<br/>
        kwic:get-summary($ancestor, ($ancestor//exist:match)[1], $config)
    </pre>
:)  
module namespace kwic="http://exist-db.org/xquery/kwic";

declare variable $kwic:CHARS_SUMMARY := 120;
declare variable $kwic:CHARS_KWIC := 40;

(:~
	Like fn:substring, but takes a node argument. If the node is an element,
	a new element is created with the same node-name as the old one and the
	shortened text content.
:)
declare function kwic:substring($node as item(), $start as xs:int, $count as xs:int) as item()? {
	let $str := substring($node, $start, $count)
	return
		if ($node instance of element()) then
			element { node-name($node) } { $str }
		else
			$str
};

declare function kwic:display-text($text as text()?) as node()? {
    if ($text/parent::exist:match) then
    	<span class="hi">{$text}</span>
    else
        $text
};

declare function kwic:callback($callback as function(*)?, $node as node(), $mode as xs:string) as xs:string? {
    if (exists($callback)) then
        $callback($node, $mode)
    else
        $node
};

(:~
	Generate the left-hand context of the match. Returns a sequence of nodes
	and strings, whose total string length is less than or equal to $max characters.

	Note: this function calls itself recursively until $nodes is empty or
	the returned sequence has the desired total string length.
:)
declare function kwic:truncate-previous($root as node(), $node as node()?, $truncated as item()*, 
	$max as xs:int, $chars as xs:int, $callback as (function(node(), xs:string) as xs:string?)?) {
	if ($node) then
		let $next := $node/preceding::text()[1] intersect $root//text()
		return
			if (empty($next)) then
				$truncated
			else if ($chars + string-length($next) gt $max) then
			    let $str := kwic:callback($callback, $next, "before")
			    return
    			    if (exists($str)) then
        				let $remaining := $max - $chars
        				return (
        				    "...",
        				    kwic:substring($str, string-length($str) - $remaining + 1, $remaining),
        				    $truncated
        			    )
        			else
        			    kwic:truncate-previous($root, $next, $truncated, $max, $chars, $callback)
			else
				kwic:truncate-previous($root, $next, 
				    (kwic:callback($callback, $next, "before"), $truncated),
					$max, $chars + string-length($next), $callback)
	else
		$truncated
};

(:~
	Generate the right-hand context of the match. Returns a sequence of nodes
	and strings, whose total string length is less than or equal to $max characters.
	
	Note: this function calls itself recursively until $nodes is empty or
	the returned sequence has the desired total string length.
:)
declare function kwic:truncate-following($root as node(), $node as node()?, $truncated as item()*, 
	$max as xs:int, $chars as xs:int, $callback as (function(node(), xs:string) as xs:string?)?) {
	if ($node) then
		let $next := $node/following::text()[1] intersect $root//text()
		return
			if (empty($next)) then
				$truncated
			else if ($chars + string-length($next) gt $max) then
			    let $str := kwic:callback($callback, $next, "after")
			    return
    			    if (exists($str)) then
        				let $remaining := $max - $chars
        				return
        				    ($truncated, kwic:substring($next, 1, $remaining), "...")
        			else
        			    kwic:truncate-following($root, $next, $truncated, $max, $chars, $callback)
			else
				kwic:truncate-following($root, $next, 
				    ($truncated, kwic:callback($callback, $next, "after")),
					$max, $chars + string-length($next), $callback)
	else
		$truncated
};

(:~
	Computes the total string length of the nodes in the argument sequence
:)
declare function kwic:string-length($nodes as item()*) as xs:integer {
	if (exists($nodes)) then
		sum(for $n in $nodes return string-length($n))
	else
		0
};

declare function kwic:get-summary($root as node(), $node as element(exist:match), 
	$config as element(config)?) as element() {
	kwic:get-summary($root, $node, $config, ())
};

(:~
	Print a summary of the match in $node. Output a predefined amount of text to
	the left and the right of the match.

	@param $root root element which should be used as context for the match. It defines the
	    boundaries for the text extraction. Text will be taken from this context. 
	@param $node the exist:match element to process.
	@param $config configuration element which determines the behaviour of the function
	@param $callback (optional) reference to a callback function which will be called
	once for every text node before it is appended to the displayed text. The function
	should accept 2 parameters: 1) a single text node, 2) a string indicating the
	current direction in which text is appended, i.e. "before" or "after". The function
	may return the empty sequence if the current node should be ignore (e.g. if it belongs
	to a "footnote" which should not be displayed). Otherwise it should return a single
	string.
:)
declare function kwic:get-summary($root as node(), $node as element(exist:match), 
	$config as element(config)?, 
    $callback as (function(node(), xs:string) as xs:string?)?
) as element() {
	let $chars := xs:int(($config/@width, $kwic:CHARS_KWIC)[1])
	let $table := $config/@table = ('yes', 'true')
	let $prevTrunc := kwic:truncate-previous($root, $node, (), $chars, 0, $callback)
	let $remain := 
		if (not($table)) then 
			$chars * 2 - kwic:string-length($prevTrunc)
		else
			$chars
	let $followingTrunc := kwic:truncate-following($root, $node, (), $remain, 0, $callback)
	return
		if (not($table)) then
			<p>
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
			<tr>
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
    Expand the element in $hit. Creates an in-memory copy of the element and marks
    all matches with an exist:match tag, which will be used by all other functions in
    this module. You need to call kwic:expand before kwic:get-summary. 
    kwic:summarize will call it automatically.
:)
declare function kwic:expand($hit as element()) as element() {
    util:expand($hit, "expand-xincludes=no")
};

(:~
    Return all matches within the specified element, $hit. Matches are returned as
    exist:match elements. The returned nodes are part of a new document whose
    root element is a copy of the specified $hit element.
    
    @param $hit an arbitrary XML element which has been selected by one of the full text
		operations or an ngram search.
:)
declare function kwic:get-matches($hit as element()) as element(exist:match)* {
    let $expanded := kwic:expand($hit)
	return $expanded//exist:match
};

declare function kwic:summarize($hit as element(), $config as element(config)?) as element()* {
    kwic:summarize($hit, $config, ())
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
declare function kwic:summarize($hit as element(), $config as element(config)?, 
    $callback as (function(node(), xs:string) as xs:string?)?) as element()* {
    let $expanded := util:expand($hit, "expand-xincludes=no")
	for $match in $expanded//exist:match
	return
		kwic:get-summary($expanded, $match, $config, $callback)
};

(:
declare function kwic:summarize-all($hits as element()*, $config as element(config),
    $wrap as function(node(), function(node()) as element()) as element()) {
    let $hits := util:expand($hits, "expand-xincludes=no")
    for $hit in $hits
    return
        $wrap($hit, function($node) {
            for $match in $node//exist:match
            return
        		kwic:get-summary($node, $match, $config, ())
        })
};
:)