dp.sh.Brushes.XQuery = function()
{
	this.CssClass = 'dp-xquery';
}

dp.sh.Brushes.XQuery.prototype	= new dp.sh.Highlighter();
dp.sh.Brushes.XQuery.Aliases	= ['xquery'];

dp.sh.Brushes.XQuery.prototype.ProcessRegexList = function()
{
	var keywords = 'element to div mod text node or and child parent self attribute comment document document-node'
		+ ' collection ancestor descendant descendant-or-self ancestor-or-self preceding-sibling following-sibling following'
		+ ' preceding item empty version xquery variable namespace if then else for let default function external as union'
		+ ' intersect except order by some every is isnot module import at cast return instance of declare collation'
		+ ' boundary-space preserve strip ordering construction ordered unordered typeswitch encoding base-uri'
		+ ' update replace delete value insert with into rename option case validate schema'
		+ 'treat no-preserve inherit no-inherit';
	
	function push(array, value)
	{
		array[array.length] = value;
	}
	
	/* If only there was a way to get index of a group within a match, the whole XML
	   could be matched with the expression looking something like that:
	
	   (<!\[CDATA\[\s*.*\s*\]\]>)
	   | (<!--\s*.*\s*?-->)
	   | (<)*(\w+)*\s*(\w+)\s*=\s*(".*?"|'.*?'|\w+)(/*>)*
	   | (</?)(.*?)(/?>)
	*/
	var index	= 0;
	var match	= null;
	var regex	= null;

	this.GetMatches(new RegExp(this.GetKeywords(keywords), 'gm'), 'keyword');
	
	this.GetMatches(dp.sh.RegexLib.DoubleQuotedString, 'string');
	this.GetMatches(dp.sh.RegexLib.SingleQuotedString, 'string');
	
	this.GetMatches(new RegExp('\\$[\\w-_.]+', 'g'), 'variable');
	
	// Match CDATA in the following format <![ ... [ ... ]]>
	// <\!\[[\w\s]*?\[(.|\s)*?\]\]>
	this.GetMatches(new RegExp('<\\!\\[[\\w\\s]*?\\[(.|\\s)*?\\]\\]>', 'gm'), 'cdata');
	
	// Match comments
	// <!--\s*.*\s*?-->
	this.GetMatches(new RegExp('<!--\\s*.*\\s*?-->', 'gm'), 'comments');

	// Match comments
	// (:\s*.*\s*:)
	this.GetMatches(new RegExp('\\(:\\s*.*\\s*?:\\)', 'gm'), 'comments');
	
	// Match attributes and their values
	// (\w+)\s*=\s*(".*?"|\'.*?\'|\w+)*
	regex = new RegExp('([\\w-\.]+)\\s*=\\s*(".*?"|\'.*?\'|\\w+)*', 'gm');
	while((match = regex.exec(this.code)) != null)
	{
		push(this.matches, new dp.sh.Match(match[1], match.index, 'attribute'));
	
		// if xml is invalid and attribute has no property value, ignore it	
		if(match[2] != undefined)
		{
			push(this.matches, new dp.sh.Match(match[2], match.index + match[0].indexOf(match[2]), 'attribute-value'));
		}
	}

	// Match opening and closing tag brackets
	// </*\?*(?!\!)|/*\?*>
	this.GetMatches(new RegExp('</*\\?*(?!\\!)|/*\\?*>', 'gm'), 'tag');

	// Match tag names
	// </*\?*\s*(\w+)
	regex = new RegExp('</*\\?*\\s*([\\w-\.]+)', 'gm');
	while((match = regex.exec(this.code)) != null)
	{
		push(this.matches, new dp.sh.Match(match[1], match.index + match[0].indexOf(match[1]), 'tag-name'));
	}
}