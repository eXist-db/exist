/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
 *  wolfgang@exist-db.org
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
package org.exist.source;

import java.io.IOException;
import java.io.Reader;


/**
 * @author wolf
 */
public interface Source {

    public final static int VALID = 1;
    public final static int INVALID = -1;
    public final static int UNKNOWN = 0;
    
    public Object getKey();
    
    public int isValid();
    
    public int isValid(Source other);
    
    public Reader getReader() throws IOException;
    
    public String getContent() throws IOException;
    
    public void setCacheTimestamp(long timestamp);
    
    public long getCacheTimestamp();
}