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

import java.util.Map;
import java.util.StringTokenizer;

import org.apache.log4j.Logger;
import org.exist.dom.QName;
import org.exist.util.FastStringBuffer;


/**
 * @author wolf
 */
public class NodePath {

    private final static Logger LOG = Logger.getLogger(NodePath.class);
    
    /**
     * (Illegal) QName used as a marker for arbitrary path steps.
     */
    public final static QName WILDCARD = new QName("*", "");
    
    private QName[] components = new QName[5];
    private int pos = 0;
    private boolean includeDescendants = true;
    
    public NodePath() {
    }
        
    /**
     * 
     */
    public NodePath(Map namespaces, String path) {
        init(namespaces, path);
    }

    public NodePath(Map namespaces, String path, boolean includeDescendants) {
        this.includeDescendants = includeDescendants;
        init(namespaces, path);
    }
    
    public void addComponent(QName component) {
        if(pos == components.length) {
            QName[] t = new QName[pos + 1];
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
        int i = 0, j = 0;
        for( ; j < other.pos; j++) {
            if(i == pos) {
                if(includeDescendants)
                    return true;
                return j == other.pos ? true : false;
            }
            if(components[i] == WILDCARD) {
                ++i;
                skip = true;
            }
            if(other.components[j].compareTo(components[i]) == 0) {
                ++i;
                skip = false;
            } else if(skip) {
                continue;
            } else
                return false;
        }
        if(i == pos) {
            if(includeDescendants)
                return true;
            return j == other.pos ? true : false;
        }
        return false;
    }
    
    public void reset() {
        for(int i = 0;  i < pos; i++)
            components[i] = null;
        pos = 0;
    }
    
    public String toString() {
        StringBuffer buf = new StringBuffer();
        for(int i = 0; i < pos; i++) {
            buf.append('/');
            buf.append(components[i]);
        }
        return buf.toString();
    }
    
    private void addComponent(Map namespaces, String component) {
        String prefix = QName.extractPrefix(component);
        if(prefix == null) {
            addComponent(new QName(component, ""));
            return;
        }
        String namespaceURI = (String) namespaces.get(prefix);
        String localName = QName.extractLocalName(component);
        if(namespaceURI == null) {
            LOG.error("No namespace URI defined for prefix: " + prefix);
            addComponent(new QName(localName, ""));
        }
        addComponent(new QName(localName, namespaceURI, prefix));
    }
    
    private void init( Map namespaces, String path ) {
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
                    addComponent( namespaces, next );
                if ( path.charAt( ++pos ) == '/' )
                    addComponent( WILDCARD );
                break;

            default:
                token.append(ch);
                pos++;
            }
        }
        if ( token.length() > 0 )
            addComponent( namespaces, token.toString() );
    }
}
