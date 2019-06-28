/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2007 The eXist Project
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
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 * 
 *  $Id$
 *  
 *  @author <a href="mailto:pierrick.brihaye@free.fr">Pierrick Brihaye</a>
 */
package org.exist.xquery;

/** Expressions that use an index to speed-up their evaluation may implement this interface.
 * Tt will help index-related pragmas like (# exist:force-index-use #) { foo }
 * 
 *  Probably to be merged with org.exist.xquery.Optimizable in the future.
 *  
 * @author brihaye
 *
 */
public interface IndexUseReporter {
	
	public boolean hasUsedIndex();

}
