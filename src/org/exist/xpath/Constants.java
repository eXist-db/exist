
package org.exist.xpath;


public interface Constants {

    public final static String[] AXISSPECIFIERS = {
						"ancestor",
					    "ancestor-or-self",
					    "attribute",
					    "child",
					    "descendant",
					    "descendant-or-self",
					    "following",
					    "following-sibling",
					    "namespace",
					    "parent",
					    "preceding",
					    "preceding-sibling",
					    "self",
					    "attribute"
	};
					    
    public final static int ANCESTOR_AXIS = 0;
    public final static int ANCESTOR_SELF_AXIS = 1;
    public final static int ATTRIBUTE_AXIS = 2;
    public final static int CHILD_AXIS = 3;
    public final static int DESCENDANT_AXIS = 4;
    public final static int DESCENDANT_SELF_AXIS = 5;
    public final static int FOLLOWING_AXIS = 6;
    public final static int FOLLOWING_SIBLING_AXIS = 7;
    public final static int NAMESPACE_AXIS = 8;
    public final static int PARENT_AXIS =  9;
    public final static int PRECEDING_AXIS = 10;
    public final static int PRECEDING_SIBLING_AXIS = 11;
    public final static int SELF_AXIS = 12;
	public final static int DESCENDANT_ATTRIBUTE_AXIS = 13;
	
    /**
     * These constants represent the
     * different node types in <i>XPath</i>.
     */
    public final static String[] NODETYPES = {"node",
				       "root",
				       "*",
				       "text",
				       "attribute",
				       "namespace",
				       "comment",
				       "processing-instruction"};

    public final static short TYPE_UNKNOWN = -1;

    public final static int NODE_TYPE = 0;
    public final static int ROOT_NODE = 1;
    public final static int ELEMENT_NODE = 2;
    public final static int TEXT_NODE = 3;
    public final static int ATTRIBUTE_NODE = 4;
    public final static int NAMESPACE_NODE = 5;
    public final static int COMMENT_NODE = 6;
    public final static int PROCESSING_NODE = 7;

    public final static int LT  = 0;
    public final static int GT  = 1;
    public final static int GTEQ = 2;
    public final static int LTEQ = 3;
    public final static int EQ  = 4;
    public final static int NEQ = 5;
    public final static int IN = 6;
    public final static int REGEXP = 7;

    public final static int TRUNC_NONE = -1;
    public final static int TRUNC_RIGHT = 0;
    public final static int TRUNC_LEFT = 1;
    public final static int TRUNC_BOTH = 2;

	public final static int PLUS = 8;
	public final static int MINUS = 9;
	public final static int MULT = 10;
	public final static int DIV = 11;
	public final static int MOD = 12;
	public final static int IDIV = 13;

	public final static int IS = 14;
	public final static int ISNOT = 15;
	public final static int BEFORE = 16;
	public final static int AFTER = 17;
	
	public final static String[] OPS = 
	{ "<", ">", ">=", "<=", "=", "!=", "IN" , "=~", "+", 
	  "-", "*", "div", "mod", "idiv", "is", "isnot", "<<", ">>" };

    public final static int KEEP_UNION = 0;
    public final static int KEEP_INTER = 1;
    public final static int KEEP_AFTER = 2;
    public final static int KEEP_BEFORE = 3;

    public final static int TYPE_ANY      = 0;
    public final static int TYPE_NODELIST = 1;
    public final static int TYPE_NODE     = 2;
    public final static int TYPE_STRING   = 3;
    public final static int TYPE_NUM      = 4;
    public final static int TYPE_BOOL     = 5;

    public final static int FULLTEXT_OR = 0;
    public final static int FULLTEXT_AND = 1;
}








