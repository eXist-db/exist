/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xquery;

/**
 * Declares various constants and flags used by the query engine:
 * axis specifiers, operators.
 * 
 * @author wolf
 */
public interface Constants {

    //TODO : move this to a dedicated Axis class
    
	/** Axis names */
    public final static String[] AXISSPECIFIERS = {
						"ancestor",
					    "ancestor-or-self",
					    "parent",
					    "preceding",
					    "preceding-sibling",
					    "child",
					    "attribute",
					    "descendant",
					    "descendant-or-self",
					    "following",
					    "following-sibling",
					    "namespace",
					    "self",
					    "attribute-descendant"
	};    
    
    /**
     * XPath axis constants:
     */
    public final static int UNKNOWN_AXIS = -1;
    
    /** Reverse axes */
    public final static int ANCESTOR_AXIS = 0;
    public final static int ANCESTOR_SELF_AXIS = 1;
    public final static int PARENT_AXIS =  2;
    public final static int PRECEDING_AXIS = 3;
    public final static int PRECEDING_SIBLING_AXIS = 4;
    
    /** Forward axes */
    public final static int CHILD_AXIS = 5;
    public final static int ATTRIBUTE_AXIS = 6;
    public final static int DESCENDANT_AXIS = 7;
    public final static int DESCENDANT_SELF_AXIS = 8;
    public final static int FOLLOWING_AXIS = 9;
    public final static int FOLLOWING_SIBLING_AXIS = 10;
    public final static int NAMESPACE_AXIS = 11;
    public final static int SELF_AXIS = 12;
    //combines /descendant-or-self::node()/attribute:*
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

    /**
     * Node types
     */
    
    public final static int NODE_TYPE = 0;
    public final static int ROOT_NODE = 1;
    public final static int ELEMENT_NODE = 2;
    public final static int TEXT_NODE = 3;
    public final static int ATTRIBUTE_NODE = 4;
    public final static int NAMESPACE_NODE = 5;
    public final static int COMMENT_NODE = 6;
    public final static int PROCESSING_NODE = 7;

    /**
     * Comparison operators
     */
    public final static int LT  = 0;
    public final static int GT  = 1;
    public final static int GTEQ = 2;
    public final static int LTEQ = 3;
    public final static int EQ  = 4;
    public final static int NEQ = 5;
    public final static int IN = 6;
    public final static int REGEXP = 7;

    /**
     * String truncation operators
     */
    public final static int TRUNC_NONE = -1;
    public final static int TRUNC_RIGHT = 0;
    public final static int TRUNC_LEFT = 1;
    public final static int TRUNC_BOTH = 2;

    /**
     * Arithmetic operators
     */
	public final static int PLUS = 8;
	public final static int MINUS = 9;
	public final static int MULT = 10;
	public final static int DIV = 11;
	public final static int MOD = 12;
	public final static int IDIV = 13;

	/**
	 * Identity operators
	 */
	public final static int IS = 14;
	public final static int ISNOT = 15;
	public final static int BEFORE = 16;
	public final static int AFTER = 17;
	
    public final static String[] OPS = 
    { "<", ">", ">=", "<=", "=", "!=", "IN" , "=~", "+", 
      "-", "*", "div", "mod", "idiv", "is", "isnot", "<<", ">>" };
    
    public final static String[] VOPS = 
    { "lt", "gt", "ge", "lt", "eq", "ne" };    

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
    
    //TODO : move the following to an org.exist.utils.Constants.java file
    
    //The definitive missing constant in java.lang.String
    public final static int STRING_NOT_FOUND = -1;
    //The definitive missing constants in java.lang.Comparable
    public final static int INFERIOR = -1;
    public final static int EQUAL = 0;
    public final static int SUPERIOR = 1;
    //
    public final static int NO_SIZE_HINT = -1;
    
}