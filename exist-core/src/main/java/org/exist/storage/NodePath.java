/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.storage;

import java.util.Arrays;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;

import javax.annotation.Nullable;
import javax.xml.XMLConstants;


/**
 * Represents a Node Path.
 *
 * Internally the node path is held as an array of {@link QName} components.
 * Upon construction the array of `components` is sized such that it will be exactly
 * what is required to represent the Node Path.
 * Mutation operations however will over-allocate the array as an optmisation, so that
 * we need not allocate/free memory on every mutation operation, but only
 * every {@link #DEFAULT_NODE_PATH_SIZE} operations.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 * @author wolf
 */
public class NodePath implements Comparable<NodePath> {

    static final int DEFAULT_NODE_PATH_SIZE = 4;
    static final int MAX_OVER_ALLOCATION_FACTOR = 2;

    private static final Logger LOG = LogManager.getLogger(NodePath.class);

    /**
     * (Illegal) QNames used as a marker for arbitrary path steps.
     */
    public static final QName SKIP = new QName("//", "");
    public static final QName WILDCARD = new QName("*", "");

    private QName[] components = new QName[DEFAULT_NODE_PATH_SIZE];
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

    public NodePath(@Nullable final Map<String, String> namespaces, final String path) {
        init(namespaces, path);
    }

    public NodePath(@Nullable final Map<String, String> namespaces, final String path, final boolean includeDescendants) {
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
        // do we have enough space to accommodate the components from `other`
        final int numOtherComponentsToAppend = other.length();
        allocateIfNeeded(numOtherComponentsToAppend);

        // at this point we have enough space, append the components from `other`
        System.arraycopy(other.components, 0, components, pos, numOtherComponentsToAppend);
        this.pos += numOtherComponentsToAppend;
    }

    public void addComponent(final QName component) {
        // do we have enough space to add the component
        allocateIfNeeded(1);

        // at this point we have enough space, add the component
        components[pos++] = component;
    }

    private void allocateIfNeeded(final int numOtherComponentsToAppend) {
        final int available = components.length - pos;
        if (available < numOtherComponentsToAppend) {
            // we need more space, allocate a multiple of DEFAULT_NODE_PATH_SIZE
            final int allocationFactor = (int) Math.ceil((numOtherComponentsToAppend - available + components.length) / ((float) DEFAULT_NODE_PATH_SIZE));
            final int newSize = allocationFactor * DEFAULT_NODE_PATH_SIZE;
            this.components = Arrays.copyOf(components, newSize);
        }
    }

    /**
     * Remove the Last Component from the NodePath.
     *
     * NOTE: this does not shrink the storage requirements
     * of the NodePath
     */
    public void removeLastComponent() {
        if (pos > 0) {
            components[--pos] = null;
        }
    }

    public void reset() {
        // when resetting if this object has more than twice the capacity of a new object, then set it back to the default capacity
        if (pos > DEFAULT_NODE_PATH_SIZE * MAX_OVER_ALLOCATION_FACTOR) {
            components = new QName[DEFAULT_NODE_PATH_SIZE];
        } else {
            Arrays.fill(components, null);
        }
        pos = 0;
    }

    public int length() {
        return pos;
    }

    /**
     * Return the size of the components array.
     *
     * This function is intentionally marked as package-private
     * so that it may only be called for testing purposes!
     *
     * @return the size of the components array.
     */
    int componentsSize() {
        return components.length;
    }

    protected void reverseComponents() {
        for (int i = 0; i < pos / 2; ++i) {
            QName tmp = components[i];
            components[i] = components[pos - 1 - i];
            components[pos - 1 - i] = tmp;
        }
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
        for ( ; j < other.pos; j++) {
            if (i == pos) {
                if (includeDescendants) {
                    return true;
                }
                return j == other.pos;
            }
            if (components[i] == SKIP) {
                ++i;
                skip = true;
            }
            if ((components[i] == WILDCARD || other.components[j].compareTo(components[i]) == 0) &&
                    (!skip || j + 1 == other.pos || other.components[j + 1].compareTo(components[i]) != 0)) {
                ++i;
                skip = false;
            } else if (skip) {
                continue;
            } else {
                return false;
            }
        }
        if (i == pos) {
            if (includeDescendants) {
                return true;
            }
            return j == other.pos;
        }
        return false;
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        for (int i = 0; i < pos; i++) {

            if (!(SKIP.equals(components[i]) || (i > 0 && SKIP.equals(components[i - 1])))) {
                buf.append("/");
            }

            if (components[i].getNameType() == ElementValue.ATTRIBUTE) {
                buf.append("@");
            }

            buf.append(components[i]);
        }
        return buf.toString();
    }

    public void addComponent(@Nullable final Map<String, String> namespaces, final String origComponent) {
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
                    LOG.error("No namespace URI defined for prefix: {}", prefix);
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
            LOG.debug("URI = {}", qn.getNamespaceURI());
            addComponent(qn);
        } catch (final QName.IllegalQNameException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private void init(@Nullable final Map<String, String> namespaces, final String path) {
        //TODO : compute better length
        final StringBuilder token = new StringBuilder(path.length());
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
        if (this == obj) {
            return true;
        }

        if (obj instanceof NodePath otherNodePath) {

            // NOTE(AR) we cannot use Array.equals on the components of the NodePaths as they may be over-allocated!
            if (pos == otherNodePath.pos) {
                for (int i = 0; i < pos; i++) {
                    if (!components[i].equals(otherNodePath.components[i])) {
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
        for (int i = 0; i < pos; i++) {
            h = 31 * h + components[i].hashCode();
        }
        return h;
    }

    @Override
    public int compareTo(final NodePath other) {
        return toString().compareTo(other.toString()); //TODO: optimize
    }
}
