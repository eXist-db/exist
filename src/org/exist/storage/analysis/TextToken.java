
package org.exist.storage.analysis;

public class TextToken {
	
    public final static int ALPHA = 1;
    public final static int ALPHANUM = 2;
    public final static int DIGIT = 3;
    public final static int EOF = -1;
    public final static int FLOAT = 4;
    public final static int LETTER = 5;
    public final static int NUMBER = 6;
    public final static int P = 7;
    public final static int WS = 8;
    public final static int HOST = 9;
    public final static int EMAIL = 10;
    public final static int ACRONYM = 11;

    public final static TextToken WS_TOKEN = new TextToken(WS);
    public final static TextToken EOF_TOKEN = new TextToken(EOF);
	
    public final static String[] types = {"letter", "digit",
        "whitespace", "number",
        "alpha", "alphanum",
        "p", "float"};
    private int end = 0;
    private int start = 0;
    private CharSequence tokenText;
    private int tokenType = EOF;


	public TextToken() {
	}
	
    /**
     *  Constructor for the Token object
     *
     *@param  type  Description of the Parameter
     *@param  text  Description of the Parameter
     */
    public TextToken( int type, CharSequence text ) {
        this( type, text, 0, text.length() );
    }


    /**
     *  Constructor for the Token object
     *
     *@param  type   Description of the Parameter
     *@param  text   Description of the Parameter
     *@param  start  Description of the Parameter
     */
    public TextToken( int type, CharSequence text, int start ) {
        this( type, text, start, start );
    }


    /**
     *  Constructor for the Token object
     *
     *@param  type   Description of the Parameter
     *@param  text   Description of the Parameter
     *@param  start  Description of the Parameter
     *@param  end    Description of the Parameter
     */
    public TextToken( int type, CharSequence text, int start, int end ) {
        tokenType = type;
        tokenText = text;
        this.start = start;
        this.end = end;
    }


    /**
     *  Constructor for the Token object
     *
     *@param  type  Description of the Parameter
     */
    public TextToken( int type ) {
        tokenType = type;
    }

	public void set(int type, CharSequence text, int start) {
		tokenType = type;
		tokenText = text;
		this.start = start;
		this.end = start;
	}

	public boolean isAlpha() {
		return tokenType == ALPHA;
	}
	
    /**
     * Consume the next character in the current buffer by incrementing
     * the end offset.
     */
    public void consumeNext() {
        end++;
    }

    public char getChar() {
        return tokenText.charAt( start );
    }

    public String getText() {
        if(start >= tokenText.length() || end > tokenText.length())
            throw new StringIndexOutOfBoundsException("start: " + start +
                "; end=" + end + "; text=" + tokenText);
        return tokenText.subSequence( start, end ).toString();
    }

    public int getType() {
        return tokenType;
    }

	public void setType( int type ) {
		tokenType = type;
	}
	
    public void setText( String text ) {
        tokenText = text;
    }
}

