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
 *  $Id: Pragma.java 4488 2006-10-05 17:40:20 +0200 (Thu, 05 Oct 2006) deliriumsky $
 */
package org.exist.xquery;

import org.exist.dom.QName;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;

public abstract class Pragma {

    private QName qname;
    private String contents;
    
    public Pragma(QName qname, String contents) throws XPathException {
        this.qname = qname;
        this.contents = contents;
    }

    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
    }

    public Sequence eval(Sequence contextSequence, Item contextItem)
    throws XPathException {
        return null;
    }
    
    public abstract void before(XQueryContext context, Expression expression, Sequence contextSequence) throws XPathException;
    
    public abstract void after(XQueryContext context, Expression expression) throws XPathException;

    protected String getContents() {
        return contents;
    }

    protected QName getQName() {
        return qname;
    }

    public void resetState(boolean postOptimization) {    
    }

    public String toString() {
        return "(# " + qname + ' ' + contents + "#)";
    }
}