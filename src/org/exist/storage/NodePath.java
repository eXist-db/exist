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
package org.exist.storage;

import java.util.StringTokenizer;

import org.exist.util.FastStringBuffer;
import org.exist.util.MutableStringTokenizer;


/**
 * @author wolf
 */
public class NodePath {

    public final static String WILDCARD = "*";
    
    private String[] components = new String[5];
    private int pos = 0;
    
    public NodePath() {  
    }
        
    /**
     * 
     */
    public NodePath(String path) {
        init(path);
    }

    public void addComponent(String component) {
        if(pos == components.length) {
            String[] t = new String[pos + 1];
            System.arraycopy(components, 0, t, 0, pos);
            components = t;
        }
        components[pos++] = component;
    }
    
    public void removeLastComponent() {
        components[--pos] = null;
    }
    
    public final boolean match(NodePath other) {
        boolean skip = false;
        int i = 0;
        for(int j = 0; j < other.pos; j++) {
            if(i == pos)
                return true;
            if(components[i].equals(WILDCARD)) {
                ++i;
                skip = true;
            }
            if(other.components[j].equals(components[i])) {
                ++i;
                skip = false;
            } else if(skip) {
                continue;
            } else
                return false;
        }
        return i == pos;
    }
    
    public final boolean match( CharSequence other) {
		int i = 0;
		boolean skip = false;
		MutableStringTokenizer tokenizer = new MutableStringTokenizer(other, "/");
		CharSequence next;
		while((next = tokenizer.nextToken()) != null) {
			if(i == pos)
				return true;
			if(components[i].equals(WILDCARD)) {
				++i;
				skip = true;
			}
			if(next.equals(components[i])) {
				++i;
				skip = false;
			} else if(skip) {
				continue;
			} else
				return false;
		}
		return i == pos;
	}
    
    public void reset() {
        for(int i = 0;  i < components.length; i++)
            components[i] = null;
        pos = 0;
    }
    
    private void init( String path ) {
        StringTokenizer tokenizer = new StringTokenizer(path, "/");
        FastStringBuffer token = new FastStringBuffer();
        String next;
        int pos = 0;
		char ch = 0;
		final int pathLen = path.length();
        while ( pos < pathLen ) {
			ch = path.charAt(pos);
            switch ( ch ) {
            case '/':
                next = token.toString();
                token.reset();
                if ( next.length(  ) > 0 )
                    addComponent( next );
                if ( path.charAt( ++pos ) == '/' )
                    addComponent( WILDCARD );
                break;

            default:
                token.append(ch);
                pos++;
            }
        }
        if ( token.length() > 0 )
            addComponent( token.toString() );
    }
}
