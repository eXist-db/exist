/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010 The eXist Project
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
 *
 *  $Id$
 */
package org.exist.xquery;

import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.QName;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;

/**
 * An XQuery/XPath variable, consisting of a QName and a value.
 * 
 * @author wolf
 */
public interface Variable {

	public void setValue(Sequence val);

    public Sequence getValue();
	
	public QName getQName();
	
    public int getType();
    
    public void setSequenceType(SequenceType type) throws XPathException;
    
    public SequenceType getSequenceType();

    public void setStaticType(int type);

    public int getStaticType();
    
    public boolean isInitialized();
    
    public void setIsInitialized(boolean initialized);
    
	//public String toString();
	
	public int getDependencies(XQueryContext context);
	
	public int getCardinality();
	
	public void setStackPosition(int position);
	
	public DocumentSet getContextDocs();
	
	public void setContextDocs(DocumentSet docs);
    
    public void checkType() throws XPathException;
    
    //private Sequence convert(Sequence seq) throws XPathException;
}
