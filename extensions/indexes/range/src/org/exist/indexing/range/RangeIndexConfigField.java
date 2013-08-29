package org.exist.indexing.range;

import org.exist.dom.QName;
import org.exist.indexing.lucene.LuceneIndexConfig;
import org.exist.storage.NodePath;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.XMLString;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Type;
import org.w3c.dom.Element;

import java.util.Map;

public class RangeIndexConfigField {

    private String name;
    private NodePath path = null;
    private NodePath relPath = null;
    private int type = Type.STRING;
    protected boolean includeNested = false;
    protected int wsTreatment = XMLString.SUPPRESS_NONE;
    protected boolean isQNameIndex = false;
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
        } else if (elem.hasAttribute("qname")) {
            QName qname = LuceneIndexConfig.parseQName(elem, namespaces);
            path = new NodePath(qname);
            relPath = path;
            isQNameIndex = true;
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
