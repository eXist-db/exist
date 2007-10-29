package org.exist.storage.analysis;


/**
 *  This is the default class used by the fulltext indexer for
 * tokenizing a string into words. Known token types are defined
 * by class Token.
 *
 *@author     Wolfgang Meier
 */
public class SimpleTokenizer implements Tokenizer {
	private int pos = 0;
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
		final int current = pos + i;
		return current > len ? (char) -1 : text.charAt(current - 1);
	}

	protected TextToken alpha(TextToken token, boolean allowWildcards) {
		if (token == null)
			token = new TextToken(TextToken.ALPHA, text, pos);
		else
			token.setType(TextToken.ALPHA);
		// consume letters
		char ch = LA(1);
		int count = 0;
		while (ch != (char) -1) {
			if (ch == '\\' && isWildcard(LA(2))) {
				break;
			} else if (ch > '\u2E80' && singleCharToken(ch)) {
				// if this is a single char token and first in the sequence,
				// consume it
				if(count == 0) {
					token.consumeNext();
					consume();
					ch = LA(1);
				}
				break;
			} else if (Character.isLetter(ch) || is_mark(ch) || nonBreakingChar(ch) || (allowWildcards && isWildcard(ch))) {
				token.consumeNext();
				consume();
				ch = LA(1);
				count++;
			} else {
				break;
			}
		}
		if (Character.isDigit(ch)) {
			// found non-letter character
			// call alphanum()
			return alphanum(token, allowWildcards);
		}
		return token;
	}

	private final static boolean isWildcard(char ch) {
		if (ch == '?' || ch == '*')
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
		if (Character.isLetter(ch) || is_mark(ch) || nonBreakingChar(ch)
			|| singleCharToken(ch) 
			|| (wildcards && isWildcard(ch))) {
			token = alpha(null, wildcards);
		}
		if (token == null
			&& (Character.isLetterOrDigit(ch)
				|| (wildcards && isWildcard(ch))))
			token = alphanum(null, wildcards);

		if (token == null)
			switch (ch) {
				case '\\':
					if(isWildcard(LA(2))) {
						consume();
					}
				case '*' :
				case ',' :
				case '-' :
				case '_' :
				case ':' :
				case '.' :
				case '@' :
                case '/' :
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
		    while (true) {
		        TextToken token = nextTerminalToken(wildcards);
		        TextToken next;
		        int oldPos = pos;
		        char LA1 = LA(1);
                
		        switch (token.getType()) {
		            case TextToken.EOF :
		                return null;
		            case TextToken.ALPHA :
		                switch (LA1) {
		                    // text with apostrophe like Peter's
                            case '\'' :
                                consume();
                                next = nextTerminalToken(wildcards);
                                if (next != null
                                        && next.getType() == TextToken.ALPHA) {
                                    return new TextToken(TextToken.ALPHA, text, token.startOffset(), next.endOffset());
                                }
                                pos = oldPos;
                                break;
                            // text with some alphanumeric sequence attached
                            // handles URL's, email addresses, dates or general sequences like
                            // like Q/22/A4.5 or 12/09/1989
		                    case '_' :
		                    case ':' :
		                    case '.' :
		                    case '/' :
                            case '@' :
		                        if (LA(2) == (char) - 1
		                                || Character.isWhitespace(LA(2))) {
		                            consume();
		                            break;
		                        }
		                        TextToken last = null;
		                        while ((next = nextTerminalToken(wildcards))
		                                != null) {
		                            if (next.getType() == TextToken.EOF
		                                    || next.getType() == TextToken.WS)
		                                break;
		                            if(next.getType() == TextToken.P &&
		                                    (LA(2) == (char)-1 || Character.isWhitespace(LA(2))))
		                                break;
		                            last = next;
		                        }
		                        if (last != null)
		                            token =
		                                new TextToken(
		                                        TextToken.ALPHANUM,
		                                        text, token.startOffset(), last.endOffset());
		                        else
		                            pos = oldPos;
		                }
		                return token;
		            case TextToken.ALPHANUM :
		                switch (LA1) {
		                    case '/' :
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
		                        TextToken last = null;
		                        while ((next = nextTerminalToken(wildcards)) != null) {
		                            if (next.getType() == TextToken.EOF
		                                    || next.getType() == TextToken.WS)
		                                break;
		                            last = next;
		                        }
		                        if (last != null)
		                            token = new TextToken(TextToken.ALPHANUM, text, token.startOffset(), last.endOffset());
		                        else
		                            token = new TextToken(TextToken.ALPHANUM, text, token.startOffset(), pos);
		                }
		                return token;
		            default :
		                // fall through to start of while loop
		        }
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

    public void setText(CharSequence text, int offset) {
		pos = offset;
		len = text.length();
		this.text = text;
	}

    protected TextToken whitespace() {
		consume();
		return TextToken.WS_TOKEN;
	}
	
	/**
	 * The code ranges defined here should be interpreted as 1-char
	 * tokens.
	 */
	private static final boolean singleCharToken(char ch) {
		return
			// CJK Radicals Supplement 
			(ch >= '\u2E80' && ch <= '\u2EFF') ||
			// KangXi Radicals
			(ch >= '\u2F00' && ch <= '\u2FDF') ||
			// Ideographic Description Characters
			(ch >= '\u2FF0' && ch <= '\u2FFF') ||
			// Enclosed CJK Letters and Months
			(ch >= '\u3200' && ch <= '\u32FF') ||
			// CJK Compatibility
			(ch >= '\u3300' && ch <= '\u33FF') ||
			// CJK Unified Ideographs Extension A
			(ch >= '\u3400' && ch <= '\u4DB5') ||
			// Yijing Hexagram Symbols
			(ch >= '\u4DC0' && ch <= '\u4DFF') ||
			// CJK Unified Ideographs
			(ch >= '\u4E00' && ch <= '\u9FFF') ||
			// CJK Compatibility Ideographs
			(ch >= '\uF900' && ch <= '\uFAFF') ||
			// CJK Compatibility Forms
			(ch >= '\uFE30' && ch <= '\uFE4F');
	}
	
	/**
	 * These codepoints should not be broken into tokens.
	 */
	private final static boolean nonBreakingChar(char ch) {
		return
			// Hiragana
			(ch >= '\u3040' && ch <= '\u309F') ||
			// Katakana
			(ch >= '\u30A0' && ch <= '\u30FF') ||
			// Bopomofo
			(ch >= '\u3100' && ch <= '\u312F') ||
			// Hangul Compatibility Jamo
			(ch >= '\u3130' && ch <= '\u318F') ||
			// Kanbun
			(ch >= '\u3190' && ch <= '\u319F') ||
			// Bopomofo Extended
			(ch >= '\u31A0' && ch <= '\u31BF') ||
			// Katakana Phonetic Extensions
			(ch >= '\u31F0' && ch <= '\u31FF') ||
			// Hangul Syllables
			(ch >= '\uAC00' && ch <= '\uD7A3');
	}
	
	private final boolean is_mark(char ch) {
        return (ch > '\u093d' && ch < '\u094c');
    }
	
	public static void main(String args[]) {
        String t1 = "\u4ED6\u4E3A\u8FD9\u9879\u5DE5\u7A0B\u6295\u5165\u4E86\u5341\u4E09\u5E74\u65F6\u95F4\u3002";
		SimpleTokenizer tokenizer = new SimpleTokenizer();
		tokenizer.setText(t1);
		TextToken token = tokenizer.nextToken(false);
		while(token != null && token.getType() != TextToken.EOF) {
			System.out.println(token.getText());
			token = tokenizer.nextToken(false);
		}
	}
}
