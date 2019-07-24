/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.util;

/**
 * Class used to pass an error context to subordinate expressions.
 * Most XQuery classes delegate dynamic type and cardinality checks 
 * to classes like {@link org.exist.xquery.DynamicCardinalityCheck} or
 * {@link org.exist.xquery.DynamicTypeCheck}. Those classes don't know
 * the context in which they were called and thus can't produce meaningful
 * error messages. Class Error can be used to pass runtime error descriptions 
 * from the top-level object to the lower-level objects.
 * 
 * This class also defines some static constants for various error messages.
 * The final formatting of the message is done by class 
 * {@link org.exist.xquery.util.Messages}.
 * 
 * @author wolf
 */
public class Error {

    public final static String FUNC_RETURN_CARDINALITY = "D01";
    
    public final static String FUNC_PARAM_CARDINALITY = "D02";
    
    public final static String FUNC_RETURN_TYPE = "D03";
    
    public final static String TYPE_MISMATCH = "D04";
    
    public final static String NODE_COMP_TYPE_MISMATCH = "D05";
    
    public final static String FUNC_PARAM_TYPE = "D06";
    
    public final static String VAR_TYPE_MISMATCH = "D07";
	
	public final static String UPDATE_SELECT_TYPE = "D08";
	
	public final static String UPDATE_EMPTY_CONTENT = "D09";
	
	public final static String UPDATE_REPLACE_ELEM_TYPE = "D10";
    
    public final static String FUNC_EMPTY_SEQ_DISALLOWED = "S01";
    
    public final static String FUNC_PARAM_TYPE_STATIC = "S02";
	
	public final static String FUNC_NOT_FOUND = "S03";
     
    private final String errCode;
    private Object[] args = null;
    
    /**
     * Creates a new error with the specified message id.
     * 
     * @param errCode the message id for this error.
     */
    public Error(String errCode) {
        this.errCode = errCode;
    }
    
    /**
     * Creates a new error with the specified message id
     * and adds a single argument to the argument list for the
     * error message.
     * 
     * @param errCode the message id for this error
     * @param arg1 additional argument
     */
    public Error(String errCode, Object arg1) {
        this.errCode = errCode;
        args = new Object[] { arg1 };
    }
    
    public Error(String errCode, Object arg1, Object arg2) {
        this.errCode = errCode;
        args = new Object[] { arg1, arg2 };
    }
    
    /**
     * Add an argument to the argument list for the error message.
     * 
     * @param arg the argument to add
     */
    public void addArg(Object arg) {
        addArgs(new Object[] { arg });
    }
    
    public void addArgs(Object arg1, Object arg2) {
        addArgs(new Object[] { arg1, arg2 });
    }
    
    public void addArgs(Object arg1, Object arg2, Object arg3) {
        addArgs(new Object[] { arg1, arg2, arg3 });
    }
  
    public void addArgs(Object[] nargs) {
        if (args == null) {
            args = nargs;
        } else {
            Object[] a = new Object[args.length + nargs.length];
            System.arraycopy(args, 0, a, 0, args.length);
            System.arraycopy(nargs, 0, a, args.length, nargs.length);
            args = a;
        }
    }
    
    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    public String toString() {
        return Messages.formatMessage(errCode, args);
    }
    
    public String getErrorCode() {
        return errCode;
    }
    
    public Object[] getArgs() {
        return args;
    }
}