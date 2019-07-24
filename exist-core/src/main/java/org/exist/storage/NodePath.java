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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.util.FastStringBuffer;

import javax.xml.XMLConstants;


/**
 * @author wolf
 */
public class NodePath implements Comparable<NodePath> {

    private final static Logger LOG = LogManager.getLogger(NodePath.class);

    /**
     * (Illegal) QNames used as a marker for arbitrary path steps.
     */
    public final static QName SKIP = new QName("//", "");
    public final static QName WILDCARD = new QName("*", "");

    private QName[] components = new QName[5];
    private int pos = 0;
    private boolean includeDescendants = true;

    public NodePath() {
        //Nothing to do
    }

    public NodePath(final NodePath other) {
        this(other, other.includeDescendants);
    }

    public NodePath(final NodePath other, final boolean includeDescendants) {
        components = new QName[other.components.length];
        System.arraycopy(other.components, 0, components, 0, other.components.length);
        pos = other.pos;
        this.includeDescendants = includeDescendants;
    }

    public NodePath(final Map<String, String> namespaces, final String path) {
        init(namespaces, path);
    }

    public NodePath(final Map<String, String> namespaces, final String path, final boolean includeDescendants) {
        this.includeDescendants = includeDescendants;
        init(namespaces, path);
    }

    public NodePath(final QName qname) {
    	addComponent(qname);
    }

    public void setIncludeDescendants(final boolean includeDescendants) {
        this.includeDescendants = includeDescendants;
    }

    public boolean includeDescendants() {
        return includeDescendants;
    }

    public void append(final NodePath other) {
        final QName[] newComponents = new QName[length() + other.length()];
        System.arraycopy(components, 0, newComponents, 0, pos);
        System.arraycopy(other.components, 0, newComponents, pos, other.length());
        pos = newComponents.length;
        components = newComponents;
    }

    public void addComponent(final QName component) {
        if (pos == components.length) {
            final QName[] t = new QName[pos + 1];
            System.arraycopy(components, 0, t, 0, pos);
            components = t;
        }
        components[pos++] = component;
    }

    protected void reverseComponents() {
        for (int i = 0; i < pos / 2; ++i) {
            QName tmp = components[i];
            components[i] = components[pos - 1 - i];
            components[pos - 1 - i] = tmp;
        }
    }

    public void removeLastComponent() {
        if (pos > 0) {
            components[--pos] = null;
        }
    }

    public int length() {
        return pos;
    }

    public QName getComponent(final int at) {
        if (at < 0 || at >= pos) {
            throw new ArrayIndexOutOfBoundsException(at);
        }
        return components[at];
    }

    public QName getLastComponent() {
        if (pos > 0) {
            return components[pos - 1];
        }
        return null;
    }

    public boolean hasWildcard() {
        for (int i = 0; i < pos; i++) {
            if (components[i] == WILDCARD) {
                return true;
            }
        }
        return false;
    }

    public boolean match(final QName qname) {
        if (pos > 0) {
            return components[pos - 1].equals(qname);
        }
        return false;
    }

    public final boolean match(final NodePath other) {
    	return match(other, 0);
    }

    public final boolean match(final NodePath other, int j) {
        boolean skip = false;
        int i = 0;
        for( ; j < other.pos; j++) {
            if(i == pos) {
                if(includeDescendants) {
                    return true;
                }
                return j == other.pos;
            }
            if(components[i] == SKIP) {
                ++i;
                skip = true;
            }
            if((components[i] == WILDCARD || other.components[j].compareTo(components[i]) == 0) &&
                    (!skip || j + 1 == other.pos || other.components[j + 1].compareTo(components[i]) != 0)) {
                ++i;
                skip = false;
            } else if(skip) {
                continue;
            } else {
                return false;
            }
        }
        if(i == pos) {
            if(includeDescendants) {
                return true;
            }
            return j == other.pos;
        }
        return false;
    }

    public void reset() {
        for(int i = 0;  i < pos; i++) {
            components[i] = null;
        }
        pos = 0;
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        for(int i = 0; i < pos; i++) {
        	buf.append("/");
            if (components[i].getNameType() == ElementValue.ATTRIBUTE)
            	{buf.append("@");}
            buf.append(components[i]);
        }
        return buf.toString();
    }

    public void addComponent(final Map<String, String> namespaces, final String origComponent) {
        String component = origComponent;
        boolean isAttribute = false;
        if (component.startsWith("@")) {
            isAttribute = true;
            component = component.substring(1);
        }
        try {
            String prefix = QName.extractPrefix(component);
            final String localName = QName.extractLocalName(component);
            String namespaceURI = null;

            //TODO simplify this code (AR)
            if (prefix != null) {
                namespaceURI = namespaces.get(prefix);
                if (namespaceURI == null) {
                    LOG.error("No namespace URI defined for prefix: " + prefix);
                    //TODO : throw exception ? -pb
                    prefix = null;
                    namespaceURI = "";
                }
            } else if (namespaces != null) {
                namespaceURI = namespaces.get(XMLConstants.DEFAULT_NS_PREFIX);
            }

            final QName qn;
            if (isAttribute) {
                qn = new QName(localName, namespaceURI, prefix, ElementValue.ATTRIBUTE);
            } else {
                qn = new QName(localName, namespaceURI, prefix);
            }
            LOG.debug("URI = " + qn.getNamespaceURI());
            addComponent(qn);
        } catch (final QName.IllegalQNameException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private void init(final Map<String, String> namespaces, final String path) {
        //TODO : compute better length
        final FastStringBuffer token = new FastStringBuffer(path.length());
        int pos = 0;
        while (pos < path.length()) {
            final char ch = path.charAt(pos);
            switch (ch) {
            case '*':
                addComponent(WILDCARD);
                token.setLength(0);
                pos++;
                break;
            case '/':
                final String next = token.toString();
                token.setLength(0);
                if (next.length() > 0) {
                    addComponent(namespaces, next);
                }
                if (path.charAt(++pos ) == '/') {
                    addComponent(SKIP);
                }
                break;
            default:
                token.append(ch);
                pos++;
            }
        }
        if (token.length() > 0) {
            addComponent(namespaces, token.toString());
        }
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj != null && obj instanceof NodePath) {
            final NodePath nodePath = (NodePath) obj;
            if (nodePath.pos == pos) {
                for (int i = 0; i < pos; i++) {
                    if (!nodePath.components[i].equals(components[i])) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        int h = 0;
        for(int i = 0; i < pos; i++) {
            h = 31*h + components[i].hashCode();
        }
        return h;
    }

    @Override
    public int compareTo(final NodePath other) {
        return toString().compareTo(other.toString()); //TODO: optimize
    }
}
