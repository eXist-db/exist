/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2013 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.indexing.range;

import org.exist.storage.NodePath;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.XMLString;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Type;
import org.w3c.dom.Element;

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
    private org.exist.indexing.range.conversion.TypeConverter typeConverter = null;
    protected boolean includeNested = false;
    protected int wsTreatment = XMLString.SUPPRESS_NONE;
    protected boolean caseSensitive = true;

    public RangeIndexConfigField(NodePath parentPath, Element elem, Map<String, String> namespaces) throws DatabaseConfigurationException {
        name = elem.getAttribute("name");
        path = parentPath;
        if (name == null || name.length() == 0) {
            throw new DatabaseConfigurationException("Range index module: field element requires a name attribute");
        }
        String match = elem.getAttribute("match");
        if (match != null && match.length() > 0) {
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
        if (typeStr != null && typeStr.length() > 0) {
            try {
                this.type = Type.getType(typeStr);
            } catch (XPathException e) {
                throw new DatabaseConfigurationException("Invalid type declared for range index on " + match + ": " + typeStr);
            }
        }
        String custom = elem.getAttribute("converter");
        if (custom != null && custom.length() > 0) {
            try {
                Class customClass = Class.forName(custom);
                typeConverter = (org.exist.indexing.range.conversion.TypeConverter) customClass.newInstance();
            } catch (ClassNotFoundException e) {
                RangeIndex.LOG.warn("Class for custom-type not found: " + custom);
            } catch (InstantiationException e) {
                RangeIndex.LOG.warn("Failed to initialize custom-type: " + custom, e);
            } catch (IllegalAccessException e) {
                RangeIndex.LOG.warn("Failed to initialize custom-type: " + custom, e);
            }
        }
        String nested = elem.getAttribute("nested");
        includeNested = (nested == null || nested.equalsIgnoreCase("yes"));
        path.setIncludeDescendants(includeNested);

        // normalize whitespace if whitespace="normalize"
        String whitespace = elem.getAttribute("whitespace");
        if (whitespace != null) {
            if ("trim".equalsIgnoreCase(whitespace)) {
                wsTreatment = XMLString.SUPPRESS_BOTH;
            } else if ("normalize".equalsIgnoreCase(whitespace)) {
                wsTreatment = XMLString.NORMALIZE;
            }
        }

        String caseStr = elem.getAttribute("case");
        if (caseStr != null && caseStr.length() > 0) {
            caseSensitive = caseStr.equalsIgnoreCase("yes");
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

    public org.exist.indexing.range.conversion.TypeConverter getTypeConverter() {
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
