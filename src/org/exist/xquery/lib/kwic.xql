(:~
    KWIC module: formats query results to display <em>keywords in context</em> (KWIC). A configurable
    amount of text is displayed to the left and right of a matching keyword (or phrase).
    
    The module works with all indexes that support match highlighting (matches are tagged
    with an &lt;exist:match&gt; element or exist:matches attribute). This includes the old 
    full text index, the new Lucene-based full text index, as well as the NGram index.
    
    The <b>kwic:summarize()</b> function represents the main entry point into the module.
    To have more control over the text extraction context, you can also call 
    <b>kwic:get-summary()</b> instead. For example, the following snippet will only print the
    first match within a given set of context nodes ($ancestor):
    
    <pre>
    let $matches := kwic:get-matches($hit)<br/>
    for $ancestor in $matches/ancestor::para | $matches/ancestor::title | $matches/ancestor::td<br/>
    return<br/>
        kwic:get-summary($ancestor, ($ancestor//exist:match, $ancestor//*[@exist:matches])[1], $config)
    </pre>
    
    The configuration element used by some functions has the following structure:
    
    <config
        width="character width"
        table="yes|no"
        link="URL to which the match is linked"
        whitespace-text-nodes="collapse|drop|leave"
        debug-text-nodes="yes|no"/>
:)
xquery version "3.0";

module namespace kwic = "http://exist-db.org/xquery/kwic";

import module namespace util = "http://exist-db.org/xquery/util";

declare namespace exist = "http://exist.sourceforge.net/NS/exist";

declare variable $kwic:MODE_BEFORE := "before";
declare variable $kwic:MODE_AFTER := "after";

declare %private variable $kwic:CHARS_SUMMARY := 120;
declare %private variable $kwic:CHARS_KWIC := 40;


(:~
	Like fn:substring, but takes a node argument. If the node is an element,
	a new element is created with the same node-name as the old one and the
	shortened text content.
:)
declare
    %private
function kwic:substring($node as text(), $start as xs:integer, $count as xs:integer) as node() {
	let $str := substring($node, $start, $count)
	return
		if ($node instance of element()) then
			element { node-name($node) } { $str }
		else
			text { $str }
};

declare
    %private
function kwic:display-text($text as text()?) as node()? {
    if ($text/parent::exist:match) then
    	<span class="hi">{$text}</span>
    else
        $text
};

declare
    %private
function kwic:callback($callback as (function(text(), xs:string) as text()?)?, $node as text(), $mode as xs:string) as text()? {
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
declare
    %private
function kwic:truncate-previous($root as node(), $node as node()?, $truncated as item()*, 
	   $max as xs:integer, $chars as xs:integer, $callback as (function(text(), xs:string) as text()?)?) {
	if ($node) then
		let $next := $node/preceding::text()[1] intersect $root//text()
		return
			if (empty($next)) then
				$truncated
			else if ($chars + string-length($next) gt $max) then
			    let $text-node := kwic:callback($callback, $next, $kwic:MODE_BEFORE)
			    return
    			    if (exists($text-node)) then
        				let $remaining := $max - $chars
        				return (
        				    text { "..." },
        				    kwic:substring($text-node, string-length($text-node) - $remaining + 1, $remaining),
        				    $truncated
        			    )
        			else
        			    kwic:truncate-previous($root, $next, $truncated, $max, $chars, $callback)
			else
				kwic:truncate-previous($root, $next, 
				    (kwic:callback($callback, $next, $kwic:MODE_BEFORE), $truncated),
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
declare
    %private
function kwic:truncate-following($root as node(), $node as node()?, $truncated as item()*, 
	$max as xs:integer, $chars as xs:integer, $callback as (function(text(), xs:string) as text()?)?) {
	if ($node) then
		let $next := $node/following::text()[1] intersect $root//text()
		return
			if (empty($next)) then
				$truncated
			else if ($chars + string-length($next) gt $max) then
			    let $text-node := kwic:callback($callback, $next, $kwic:MODE_AFTER)
			    return
    			    if (exists($text-node)) then
        				let $remaining := $max - $chars
        				return (
                            $truncated,
                            kwic:substring($text-node, 1, $remaining),
                            text { "..." }
        			    )
        			else
        			    kwic:truncate-following($root, $next, $truncated, $max, $chars, $callback)
			else
				kwic:truncate-following($root, $next, 
				    ($truncated, kwic:callback($callback, $next, $kwic:MODE_AFTER)),
					$max, $chars + string-length($next), $callback)
	else
		$truncated
};

(:~
	Computes the total string length of the nodes in the argument sequence
:)
declare
    %private
function kwic:string-length($nodes as item()*) as xs:integer {
	if (exists($nodes)) then
		sum($nodes ! string-length(.))
	else
		0
};

declare
    %private
function kwic:collapse-whitespace-fn($callback as (function(text(), xs:string) as text()?)?) as (function(text(), xs:string) as text()) {
    function($text as text(), $mode as xs:string) as text()? {
        let $text :=
            if (matches($text, "\s+")) then
                text { " " }
            else
                $text
        return
            if (exists($callback)) then
                $callback($text, $mode)
            else
                $text
    }
};

declare
    %private
function kwic:drop-whitespace-fn($callback as (function(text(), xs:string) as text()?)?) as (function(text(), xs:string) as text()?) {
    function($text as text(), $mode as xs:string) as text()? {
        let $text :=
            if (matches($text, "\s+")) then
                ()
            else
                $text
        return
            if (exists($text) and exists($callback)) then
                $callback($text, $mode)
            else
                $text
    }
};

declare
    %private
function kwic:output-text($debug-text-nodes as xs:boolean, $text-nodes as text()*) as node()* {
    if($debug-text-nodes) then
        $text-nodes ! <text-node>{.}</text-node>
    else
        $text-nodes
};

declare function kwic:get-summary($root as node(), $node as element(), 
	$config as element(config)?) as element() {
	kwic:get-summary($root, $node, $config, ())
};

(:~
	Print a summary of the match in $node. Output a predefined amount of text to
	the left and the right of the match.

	@param $root root element which should be used as context for the match. It defines the
	    boundaries for the text extraction. Text will be taken from this context. 
	@param $node the exist:match element or the element with @exist:macthes attribute to process.
	@param $config configuration element which determines the behaviour of the function
	@param $callback (optional) reference to a callback function which will be called
	once for every text node before it is appended to the displayed text. The function
	should accept 2 parameters: 1) a single text node, 2) a string indicating the
	current direction in which text is appended, i.e. $kwic:MODE_BEFORE or $kwic:MODE_AFTER.
	The function may return the empty sequence if the current node should be ignore
	(e.g. if it belongs to a "footnote" which should not be displayed). Otherwise it should
	return a single string.
:)
declare function kwic:get-summary($root as node(), $node as element(), 
	$config as element(config)?, 
    $callback as (function(text(), xs:string) as text()?)?
) as element() {
	let $chars := xs:integer(($config/@width, $kwic:CHARS_KWIC)[1])
	let $table := $config/@table = ('yes', 'true')
	let $whitespace-text-nodes := string($config/@whitespace-text-nodes) 
	let $debug-text-nodes := $config/@debug-text-nodes = ('yes', 'true')

	let $callback :=
	   if ($whitespace-text-nodes eq "collapse") then
	       kwic:collapse-whitespace-fn($callback)
	   else if ($whitespace-text-nodes eq "drop") then
	       kwic:drop-whitespace-fn($callback)
	   else
	       $callback
	
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
				<span class="previous">{kwic:output-text($debug-text-nodes, $prevTrunc)}</span>
				{
					if ($config/@link) then
						<a class="hi" href="{$config/@link}">{kwic:output-text($debug-text-nodes, $node/text())}</a>
					else
						<span class="hi">{kwic:output-text($debug-text-nodes, $node/text())}</span>
				}
				<span class="following">{kwic:output-text($debug-text-nodes, $followingTrunc)}</span>
			</p>
		else
			<tr>
				<td class="previous">{kwic:output-text($debug-text-nodes, $prevTrunc)}</td>
				<td class="hi">
				{
					if ($config/@link) then
						<a href="{$config/@link}">{kwic:output-text($debug-text-nodes, $node/text())}</a>
					else
						kwic:output-text($debug-text-nodes, $node/text())
				}
				</td>
				<td class="following">{kwic:output-text($debug-text-nodes, $followingTrunc)}</td>
			</tr>
};

(:~
    Expand the element in $hit. Creates an in-memory copy of the element and marks
    all element matches with an exist:match tag and attribute matches with an 
    exist:matches tag, which will be used by all other functions in
    this module. You need to call kwic:expand before kwic:get-summary. 
    kwic:summarize will call it automatically.
:)
declare function kwic:expand($hit as element()) as element() {
    util:expand($hit, "expand-xincludes=no highlight-matches=both")
};

(:~
    Return all matches within the specified element, $hit. Matches are returned as
    exist:match elements or elements with exist:matches attribute. The returned nodes 
    are part of a new document whose root element is a copy of the specified $hit element.
    
    @param $hit an arbitrary XML element which has been selected by one of the full text
		operations or an ngram search.
:)
declare function kwic:get-matches($hit as element()) as element()* {
    let $expanded := kwic:expand($hit)
	return ($expanded//exist:match, $expanded//*[@exist:matches])
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
    $callback as (function(text(), xs:string) as text()?)?) as element()* {
    let $expanded := util:expand($hit, "expand-xincludes=no highlight-matches=both")
	for $match in ($expanded//exist:match, $expanded//*[@exist:matches])
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