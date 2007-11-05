/*
 * CodePress regular expressions for XQuery syntax highlighting
 */
 
// XQuery
Language.syntax = [
	{ input : /\"(.*?)(\"|<br>|<\/P>)/g, output : '<s>"$1$2</s>'}, // strings double quote
	{ input : /\'(.*?)(\'|<br>|<\/P>)/g, output : '<s>\'$1$2</s>'}, // strings single quote
	{ input : /\b(if|then|else|for|let|in|where|return|declare|function|namespace|module|variable|as)\b/g, output : '<b>$1</b>'}, // reserved words
	{ input : /\$([\w:-_]+:[\w:-_]+|[\w:-_]+)/, output : '\$<span class="variable">$1</span>' },
	{ input : /([\w:-_]+:[\w:-_]+|[\w:-_]+)\(/, output : '<span class="funcall">$1</span>\(' },
    { input : /\(:(.*?):\)/g, output : '<i>(:$1:)</i>' }// comments /* */
]

Language.snippets = []

Language.complete = [
	{ input : '\'',output : '\'$0\'' },
	{ input : '"', output : '"$0"' },
	{ input : '(', output : '\($0\)' },
	{ input : '[', output : '\[$0\]' },
	{ input : '{', output : '{\n\t$0\n}' }		
]

Language.shortcuts = []
