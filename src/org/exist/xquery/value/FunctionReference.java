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
package org.exist.xquery.value;

import java.text.Collator;

import org.exist.xquery.FunctionCall;
import org.exist.xquery.XPathException;

/**
 * Represents a reference to a function created by util:function that can be
 * used with util:call.
 * 
 * @author wolf
 */
public class FunctionReference extends AtomicValue {

    private FunctionCall functionCall;
    
    public FunctionReference(FunctionCall fcall) {
        this.functionCall = fcall;
    }
    
    public FunctionCall getFunctionCall() {
        return functionCall;
    }
    
    /* (non-Javadoc)
     * @see org.exist.xquery.value.AtomicValue#getType()
     */
    public int getType() {
        return Type.FUNCTION_REFERENCE;
    }
    
    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#getStringValue()
     */
    public String getStringValue() throws XPathException {
        return "";
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#convertTo(int)
     */
    public AtomicValue convertTo(int requiredType) throws XPathException {
        if (requiredType == Type.FUNCTION_REFERENCE)
            return this;
        throw new XPathException("cannot convert function reference to " + Type.getTypeName(requiredType));
    }
    
	public boolean effectiveBooleanValue() throws XPathException {
		throw new XPathException("Called effectiveBooleanValue() on FunctionReference");
	}    

    /* (non-Javadoc)
     * @see org.exist.xquery.value.AtomicValue#compareTo(java.text.Collator, int, org.exist.xquery.value.AtomicValue)
     */
    public boolean compareTo(Collator collator, int operator, AtomicValue other)
            throws XPathException {
        throw new XPathException("cannot compare function reference to " + Type.getTypeName(other.getType()));
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.AtomicValue#compareTo(java.text.Collator, org.exist.xquery.value.AtomicValue)
     */
    public int compareTo(Collator collator, AtomicValue other)
            throws XPathException {
        throw new XPathException("cannot compare function reference to " + Type.getTypeName(other.getType()));
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.AtomicValue#max(java.text.Collator, org.exist.xquery.value.AtomicValue)
     */
    public AtomicValue max(Collator collator, AtomicValue other)
            throws XPathException {
        throw new XPathException("Invalid argument to aggregate function: cannot compare function references");
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.AtomicValue#min(java.text.Collator, org.exist.xquery.value.AtomicValue)
     */
    public AtomicValue min(Collator collator, AtomicValue other)
            throws XPathException {
        throw new XPathException("Invalid argument to aggregate function: cannot compare function references");
    }

}
