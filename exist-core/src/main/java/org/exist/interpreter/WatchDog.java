/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2011 The eXist Project
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
package org.exist.interpreter;

import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.xquery.Expression;
import org.exist.xquery.Option;
import org.exist.xquery.TerminatedException;
import org.exist.xquery.XPathException;

public interface WatchDog {

	public void setTimeoutFromOption(Option option) throws XPathException;

	public void setMaxNodes(int maxNodes);

	public void setMaxNodesFromOption(Option option) throws XPathException;

	public void proceed(Expression expr) throws TerminatedException;

	public void proceed(Expression expr, MemTreeBuilder builder) throws TerminatedException;

	public void cleanUp();

	public void kill(long waitTime);

	public Context getContext();

	public long getStartTime();

	public void reset();

	public boolean isTerminating();

}