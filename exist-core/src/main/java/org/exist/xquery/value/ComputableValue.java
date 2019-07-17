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
package org.exist.xquery.value;

import org.exist.xquery.XPathException;

import com.ibm.icu.text.Collator;

/**
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public abstract class ComputableValue extends AtomicValue {

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#getStringValue()
     */
    public abstract String getStringValue() throws XPathException;

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#convertTo(int)
     */
    public abstract AtomicValue convertTo(int requiredType) throws XPathException;

    /* (non-Javadoc)
     * @see org.exist.xquery.value.AtomicValue#compareTo(org.exist.xquery.value.AtomicValue)
     */
    public abstract int compareTo(Collator collator, AtomicValue other) throws XPathException;

    /* (non-Javadoc)
     * @see org.exist.xquery.value.AtomicValue#max(org.exist.xquery.value.AtomicValue)
     */
    public abstract AtomicValue max(Collator collator, AtomicValue other) throws XPathException;

    /* (non-Javadoc)
     * @see org.exist.xquery.value.AtomicValue#min(org.exist.xquery.value.AtomicValue)
     */
    public abstract AtomicValue min(Collator collator, AtomicValue other) throws XPathException;

    public abstract ComputableValue minus(ComputableValue other) throws XPathException;

    public abstract ComputableValue plus(ComputableValue other) throws XPathException;

    public abstract ComputableValue mult(ComputableValue other) throws XPathException;

    public abstract ComputableValue div(ComputableValue other) throws XPathException;
}
