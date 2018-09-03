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
 * Defines occurrence indicators (*,?,+).
 * 
 * @author wolf
 */
public class Cardinality {

	public final static int ZERO = 1;	
	public final static int ONE = 2;	
	public final static int MANY = 4;	
	public final static int EMPTY = ZERO;
	public final static int EXACTLY_ONE = ONE;
	/** indicator '+' **/
	public final static int ONE_OR_MORE = ONE | MANY;
	/** indicator '*' **/
	public final static int ZERO_OR_MORE = ZERO | ONE | MANY;
	/** indicator '?' **/
	public final static int ZERO_OR_ONE = ZERO | ONE;
    
    public final static String toString(int cardinality) {
        switch(cardinality) {
            case EMPTY:
                return "empty-sequence()";

            case EXACTLY_ONE:
                return ""; 

            case MANY:
            case ONE_OR_MORE:
                return "+";

            case ZERO_OR_MORE:
                return "*";

            case ZERO_OR_ONE:
                return "?";

            default:
                // impossible
                throw new IllegalArgumentException("unknown cardinality: " + cardinality);
        }
    }
    
    public final static String getDescription(int cardinality) {
        switch(cardinality) {
            case EMPTY:
                return "empty";
            case EXACTLY_ONE:
                return "exactly one"; 
            case ONE_OR_MORE:
                return "one or more";
            case ZERO_OR_MORE:
                return "zero or more";
            case ZERO_OR_ONE:
                return "zero or one";
            case MANY:
                return "many";
            default:
                // impossible
                throw new IllegalArgumentException("unknown cardinality: " + cardinality);
        }
    }
    
    public final static boolean checkCardinality(int required, int cardinality) {
        return ((required & cardinality) == cardinality);
    }
}
