package org.exist.storage.analysis;


/**
 *  This is the default class used by the fulltext indexer for
 * tokenizing a string into words. Known token types are defined
 * by class Token.
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@created    July 30, 2002
 */
public class SimpleTokenizer implements Tokenizer {
	private int pos = 0;
	private int previous = 0;
	private boolean stem = false;
	private CharSequence text;
	private int len = 0;
	private final TextToken temp = new TextToken();
	
	public SimpleTokenizer() {
	}

	public SimpleTokenizer(boolean stem) {
		this.stem = stem;
	}

	public void setStemming(boolean stem) {
		this.stem = stem;
	}

	private final char LA(int i) {
		final int current = pos + i - 1;
		if (current >= len)
			return (char) - 1;
		return text.charAt(current);
	}

	protected TextToken alpha(TextToken token, boolean allowWildcards) {
		if (token == null)
			token = new TextToken(TextToken.ALPHA, text, pos);
		else
			token.setType(TextToken.ALPHA);
		int oldPos = pos;
		// consume letters
		char ch = LA(1);
		while (ch != (char) - 1) {
			if (Character.isLetter(ch) || (allowWildcards && isWildcard(ch))) {
				token.consumeNext();
				consume();
				ch = LA(1);
			} else
				break;
		}
		if (Character.isDigit(ch)) {
			// found non-letter character
			// call alphanum()
			//pos = oldPos;
			return alphanum(token, allowWildcards);
		}
		return token;
	}

	private final static boolean isWildcard(char ch) {
		if (ch == '?' || ch == '*' || ch == '[' || ch == ']' || ch == '\\')
			return true;
		return false;
	}

	protected TextToken alphanum(TextToken token, boolean allowWildcards) {
		if (token == null)
			token = new TextToken(TextToken.ALPHANUM, text, pos);
		else
			token.setType(TextToken.ALPHANUM);
		while (LA(1) != (char) - 1) {
			if (Character.isLetterOrDigit(LA(1))) {
				token.consumeNext();
				consume();
			} else if (allowWildcards && isWildcard(LA(1))) {
				token.consumeNext();
				consume();
				continue;
			} else
				break;
		}
		return token;
	}

	protected void consume() {
		pos++;
	}

	protected TextToken eof() {
		consume();
		return TextToken.EOF_TOKEN;
	}

	public int getLength() {
		return len;
	}

	public String getText() {
		return text.toString();
	}

	protected TextToken nextTerminalToken(boolean wildcards) {
		TextToken token = null;
		char ch = LA(1);
		if (ch == (char) - 1)
			return eof();
		if (Character.isLetter(ch) || (wildcards && isWildcard(ch)))
			token = alpha(null, wildcards);

		if (token == null
			&& (Character.isLetterOrDigit(ch)
				|| (wildcards && isWildcard(ch))))
			token = alphanum(null, wildcards);

		if (token == null)
			switch (ch) {
				case '*' :
				case ',' :
				case '-' :
				case '_' :
				case ':' :
				case '.' :
				case '@' :
					token = p();
					break;
				default :
					token = whitespace();
					break;
			}

		return token;
	}

	public TextToken nextToken() {
		return nextToken(false);
	}

	public TextToken nextToken(boolean wildcards) {
		try {
			TextToken token = nextTerminalToken(wildcards);
			TextToken next;
			StringBuffer buf;
			int oldPos = pos;
			boolean found;
			char LA1 = LA(1);
			switch (token.getType()) {
				case TextToken.EOF :
					return null;
				case TextToken.ALPHA :
					found = false;
					// text with apostrophe like Peter's
					if (LA1 == '\'') {
						consume();
						buf = new StringBuffer(token.getText());
						next = nextTerminalToken(wildcards);
						if (next != null
							&& next.getType() == TextToken.ALPHA) {
							buf.append('\'');
							buf.append(next.getText());
							return new TextToken(
								TextToken.ALPHA,
								buf.toString());
						}
						pos = oldPos;
					}
					// text with some alphanumeric sequence attached 
					// like Q/22/A4.5 or 12/09/1989
					switch (LA1) {
						case '_' :
						case ':' :
						case '.' :
						case '/' :
							if (LA(2) == (char) - 1
								|| Character.isWhitespace(LA(2))) {
								consume();
								break;
							}
							found = false;
							buf = new StringBuffer(token.getText());
							while ((next = nextTerminalToken(wildcards))
								!= null) {
								if (next.getType() == TextToken.EOF
									|| next.getType() == TextToken.WS)
									break;
								if(next.getType() == TextToken.P &&
								   (LA(1) == (char)-1 || Character.isWhitespace(LA(1))))
								   break;
								if(next.getType() == TextToken.ALPHANUM)
									found = true;
								buf.append(next.getText());
							}
							if (found)
								token =
									new TextToken(
										TextToken.ALPHANUM,
										buf.toString());
							else
								pos = oldPos;
					}
					return token;
				case TextToken.ALPHANUM :
					switch (LA1) {
						case '*' :
						case ',' :
						case '-' :
						case '_' :
						case ':' :
						case '.' :
						case '@' :
							if (LA(2) == (char) - 1
								|| Character.isWhitespace(LA(2))) {
								consume();
								break;
							}
							buf = new StringBuffer(token.getText());
							while ((next = nextTerminalToken(wildcards))
								!= null) {
								if (next.getType() == TextToken.EOF
									|| next.getType() == TextToken.WS)
									break;
								buf.append(next.getText());
							}
							token =
								new TextToken(
									TextToken.ALPHANUM,
									buf.toString());
					}
					return token;
				default :
					return nextToken(wildcards);
			}
		} catch (Exception e) {
			System.out.println("text: " + text);
			e.printStackTrace();
			return null;
		}
	}

	protected TextToken number() {
		TextToken token = new TextToken(TextToken.NUMBER, text, pos);
		int oldPos = pos;
		while (LA(1) != (char) - 1 && Character.isDigit(LA(1))) {
			token.consumeNext();
			consume();
		}
		if (Character.isLetter(LA(1))) {
			pos = oldPos;
			return null;
		}
		return token;
	}

	protected TextToken p() {
		temp.set(TextToken.P, text, pos);
		temp.consumeNext();
		consume();
		return temp;
	}

	public void setText(CharSequence text) {
		pos = 0;
		len = text.length();
		this.text = text;
	}

	protected TextToken whitespace() {
		consume();
		return TextToken.WS_TOKEN;
	}
}
