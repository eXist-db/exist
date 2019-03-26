/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
 *  http://exist-db.org
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
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
    String[] AXISSPECIFIERS = {
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
    int UNKNOWN_AXIS = -1;
    
    /** Reverse axes */
    int ANCESTOR_AXIS = 0;
    int ANCESTOR_SELF_AXIS = 1;
    int PARENT_AXIS =  2;
    int PRECEDING_AXIS = 3;
    int PRECEDING_SIBLING_AXIS = 4;
    
    /** Forward axes */
    int CHILD_AXIS = 5;
    int ATTRIBUTE_AXIS = 6;
    int DESCENDANT_AXIS = 7;
    int DESCENDANT_SELF_AXIS = 8;
    int FOLLOWING_AXIS = 9;
    int FOLLOWING_SIBLING_AXIS = 10;

    int SELF_AXIS = 12;
    //combines /descendant-or-self::node()/attribute:*
	int DESCENDANT_ATTRIBUTE_AXIS = 13;

    /**
     * Node types
     */
    int NODE_TYPE = 0;
    int ROOT_NODE = 1;
    int ELEMENT_NODE = 2;
    int TEXT_NODE = 3;
    int ATTRIBUTE_NODE = 4;
    int NAMESPACE_NODE = 5;
    int COMMENT_NODE = 6;
    int PROCESSING_NODE = 7;

    /**
     * Value and General Comparison operators
     */
    enum Comparison {
        LT("lt", "<"),
        GT("gt", ">"),
        GTEQ("ge", ">="),
        LTEQ("le", "<="),
        EQ("eq", "="),
        NEQ("ne", "!="),
        IN(null, "IN");

        public final String valueComparisonSymbol;
        public final String generalComparisonSymbol;

        Comparison(final String valueComparisonSymbol, final String generalComparisonSymbol) {
            this.valueComparisonSymbol = valueComparisonSymbol;
            this.generalComparisonSymbol = generalComparisonSymbol;
        }
    }

    /**
     * String truncation operators
     */
    enum StringTruncationOperator {
        NONE,
        RIGHT,
        LEFT,
        BOTH,
        EQUALS
    }

    /**
     * Arithmetic operators
     */
    enum ArithmeticOperator {
        ADDITION("+"),
        SUBTRACTION("-"),
        MULTIPLICATION("*"),
        DIVISION("div"),
        MODULUS("MOD"),
        DIVISION_INTEGER("idiv");

        public final String symbol;

        ArithmeticOperator(final String symbol) {
            this.symbol = symbol;
        }
    }

	/**
	 * Node Identity Comparison operators
	 */
    enum NodeComparisonOperator {
        IS("is"),
        BEFORE("<<"),
        AFTER(">>");

        public final String symbol;

        NodeComparisonOperator(final String symbol) {
            this.symbol = symbol;
        }
    }
    
    //TODO : move the following to an org.exist.utils.Constants.java file
    
    //The definitive missing constant in java.lang.String
    int STRING_NOT_FOUND = -1;
    //The definitive missing constants in java.lang.Comparable
    int INFERIOR = -1;
    int EQUAL = 0;
    int SUPERIOR = 1;

    int NO_SIZE_HINT = -1;
}
