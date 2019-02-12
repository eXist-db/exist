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
package org.exist.xquery;

import org.exist.dom.persistent.NodeSet;
import org.exist.xquery.value.Sequence;

/**
 *
 */
public interface Optimizable extends Expression {

    boolean canOptimize(Sequence contextSequence);

    boolean optimizeOnSelf();

    boolean optimizeOnChild();
    
    NodeSet preSelect(Sequence contextSequence, boolean useContext) throws XPathException;

    int getOptimizeAxis();
}
