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
package org.exist.indexing.range;

import org.exist.indexing.range.conversion.TypeConverter;
import org.exist.storage.NodePath;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.XMLString;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Type;
import org.w3c.dom.Element;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

/**
 * Handles configuration of a field within an index definition:
 *
 * <pre>
 * &lt;create match="//parent"&gt;
 *   &lt;field name="field-name" match="@xml:id" type="xs:string"/&gt;
 * </pre>
 */
public class RangeIndexConfigField {

    private String name;
    private NodePath path = null;
    private NodePath relPath = null;
    private int type = Type.STRING;
    private TypeConverter typeConverter = null;
    protected boolean includeNested = false;
    protected int wsTreatment = XMLString.SUPPRESS_NONE;
    protected boolean caseSensitive = true;

    public RangeIndexConfigField(NodePath parentPath, Element elem, Map<String, String> namespaces) throws DatabaseConfigurationException {
        name = elem.getAttribute("name");
        path = parentPath;
        if (name.isEmpty()) {
            throw new DatabaseConfigurationException("Range index module: field element requires a name attribute");
        }
        String match = elem.getAttribute("match");
        if (!match.isEmpty()) {
            try {
                relPath = new NodePath(namespaces, match);
                if (relPath.length() == 0)
                    throw new DatabaseConfigurationException("Range index module: Invalid match path in collection config: " + match);
                path = new NodePath(parentPath);
                path.append(relPath);
            } catch (IllegalArgumentException e) {
                throw new DatabaseConfigurationException("Range index module: invalid qname in configuration: " + e.getMessage());
            }
        } else {
            path = parentPath;
        }
        String typeStr = elem.getAttribute("type");
        if (!typeStr.isEmpty()) {
            try {
                this.type = Type.getType(typeStr);
            } catch (XPathException e) {
                throw new DatabaseConfigurationException("Invalid type declared for range index on " + match + ": " + typeStr);
            }
        }
        final String custom = elem.getAttribute("converter");
        if (!custom.isEmpty()) {
            try {
                final Class<?> customClass = Class.forName(custom);
                typeConverter = (TypeConverter) customClass.getDeclaredConstructor().newInstance();
            } catch (ClassNotFoundException e) {
                RangeIndex.LOG.warn("Class for custom-type not found: {}", custom);
            } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
                RangeIndex.LOG.warn("Failed to initialize custom-type: {}", custom, e);
            }
        }
        final String nested = elem.getAttribute("nested");
        includeNested = (nested.isEmpty() || "yes".equalsIgnoreCase(nested));
        path.setIncludeDescendants(includeNested);

        // normalize whitespace if whitespace="normalize"
        final String whitespace = elem.getAttribute("whitespace");
        if (!whitespace.isEmpty()) {
            if ("trim".equalsIgnoreCase(whitespace)) {
                wsTreatment = XMLString.SUPPRESS_BOTH;
            } else if ("normalize".equalsIgnoreCase(whitespace)) {
                wsTreatment = XMLString.NORMALIZE;
            }
        }

        String caseStr = elem.getAttribute("case");
        if (!caseStr.isEmpty()) {
            caseSensitive = "yes".equalsIgnoreCase(caseStr);
        }
    }

    public String getName() {
        return name;
    }

    public NodePath getPath() {
        return path;
    }

    public int getType() {
        return type;
    }

    public TypeConverter getTypeConverter() {
        return typeConverter;
    }

    public boolean match(NodePath other) {
        return path.match(other);
    }

    public boolean match(NodePath parentPath, NodePath other) {
        if (relPath == null) {
            return parentPath.match(other);
        } else {
            NodePath absPath = new NodePath(parentPath);
            absPath.append(relPath);
            return absPath.match(other);
        }
    }

    public int whitespaceTreatment() {
        return wsTreatment;
    }

    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    public boolean includeNested() {
        return includeNested;
    }
}
