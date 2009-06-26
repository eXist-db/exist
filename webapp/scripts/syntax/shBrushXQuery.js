SyntaxHighlighter.brushes.XQuery = function()
{	
	var funcs = "QName abs adjust-date-to-timezone adjust-dateTime-to-timezone adjust-time-to-timezone avg base-uri boolean ceiling" +"codepoint-equal codepoints-to-string collection compare concat contains count xslt:current current-date" +"current-dateTime xslt:current-group xslt:current-grouping-key current-time data dateTime day-from-date" +"day-from-dateTime days-from-duration deep-equal default-collation distinct-values doc doc-available xslt:document" +"document-uri xslt:element-available empty encode-for-uri ends-with error escape-html-uri exactly-one exists false" +"floor xslt:format-date xslt:format-dateTime xslt:format-number xslt:format-time xslt:function-available" +"xslt:generate-id hours-from-dateTime hours-from-duration hours-from-time id idref implicit-timezone in-scope-prefixes"
+"index-of insert-before iri-to-uri xslt:key lang last local-name local-name-from-QName lower-case matches max min" +"minutes-from-dateTime minutes-from-duration minutes-from-time month-from-date month-from-dateTime months-from-duration"
+"name namespace-uri namespace-uri-for-prefix namespace-uri-from-QName nilled node-name normalize-space" +"normalize-unicode not number one-or-more position prefix-from-QName xslt:regex-group remove replace resolve-QName" +"resolve-uri reverse root round round-half-to-even seconds-from-dateTime seconds-from-duration seconds-from-time" +"starts-with static-base-uri string string-join string-length string-to-codepoints subsequence substring" +"substring-after substring-before sum xslt:system-property timezone-from-date timezone-from-dateTime timezone-from-time"
+"tokenize trace translate true xslt:type-available unordered xslt:unparsed-entity-public-id xslt:unparsed-entity-uri" +"xslt:unparsed-text xslt:unparsed-text-available upper-case year-from-date year-from-dateTime years-from-duration" +"zero-or-one";
    
	var keywords =  
		'element to div mod text or and child parent self attribute comment document document-node'
			+ ' collection ancestor descendant descendant-or-self ancestor-or-self preceding-sibling following-sibling following'
			+ ' preceding item empty version xquery variable namespace if then else for let default function external as union'
			+ ' intersect except order by some every is isnot module import at cast return instance of declare collation'
			+ ' boundary-space preserve strip ordering construction ordered unordered typeswitch encoding base-uri'
			+ ' update replace delete value insert with into rename option case validate schema'
			+ 'treat no-preserve inherit no-inherit';
    
	this.regexList = [
		{ regex: new RegExp('\\(:\\s*.*\\s*?:\\)', 'gm'),		css: 'comments' },
		{ regex: new RegExp('<!--\\s*.*\\s*?-->', 'gm'),		css: 'comments' },		
		{ regex: new RegExp('<\\!\\[[\\w\\s]*?\\[(.|\\s)*?\\]\\]>', 'gm'),		css: 'cdata' },		

		{ regex: new RegExp('^\\s*#!.*$', 'gm'),				css: 'preprocessor' }, // shebang
		{ regex: SyntaxHighlighter.regexLib.doubleQuotedString,	css: 'string' },
		{ regex: SyntaxHighlighter.regexLib.singleQuotedString,	css: 'string' },
		{ regex: new RegExp('(\\$|@|%)\\w+', 'g'),				css: 'variable' },
		{ regex: new RegExp(this.getKeywords(funcs), 'gmi'),	css: 'functions' },
		{ regex: new RegExp(this.getKeywords(keywords), 'gm'),	css: 'keyword' }
	    ];

	this.forHtmlScript(SyntaxHighlighter.regexLib.phpScriptTags);
}

SyntaxHighlighter.brushes.XQuery.prototype	= new SyntaxHighlighter.Highlighter();
SyntaxHighlighter.brushes.XQuery.aliases		= ['xquery'];