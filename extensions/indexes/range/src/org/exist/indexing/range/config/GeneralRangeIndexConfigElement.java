package org.exist.indexing.range.config;

import org.exist.dom.QName;
import org.exist.indexing.lucene.LuceneIndexConfig;
import org.exist.indexing.range.SimpleTextCollector;
import org.exist.indexing.range.TextCollector;
import org.exist.storage.NodePath;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.XMLString;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.*;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Map;

public class GeneralRangeIndexConfigElement extends AbstractRangeIndexConfigElement {

    protected NodePath path = null;
    private int type = Type.STRING;
    protected boolean isQNameIndex = false;
    protected boolean includeNested = false;
    protected int wsTreatment = XMLString.SUPPRESS_NONE;

    public GeneralRangeIndexConfigElement(final Element node, final Map<String, String> namespaces) throws DatabaseConfigurationException {
        super(node, namespaces);
        String match = node.getAttribute("match");
        if (match != null && match.length() > 0) {
            try {
                path = new NodePath(namespaces, match);
                if (path.length() == 0)
                    throw new DatabaseConfigurationException("Range index module: Invalid match path in collection config: " + match);
            } catch (IllegalArgumentException e) {
                throw new DatabaseConfigurationException("Range index module: invalid qname in configuration: " + e.getMessage());
            }
        } else if (node.hasAttribute("qname")) {
            QName qname = LuceneIndexConfig.parseQName(node, namespaces);
            path = new NodePath(NodePath.SKIP);
            path.addComponent(qname);
            isQNameIndex = true;
        }
        String typeStr = node.getAttribute("type");
        if (typeStr != null && typeStr.length() > 0) {
            try {
                this.type = Type.getType(typeStr);
            } catch (XPathException e) {
                throw new DatabaseConfigurationException("Invalid type declared for range index on " + match + ": " + typeStr);
            }
        }

        String nested = node.getAttribute("nested");
        includeNested = (nested == null || nested.equalsIgnoreCase("yes"));

        // normalize whitespace if whitespace="normalize"
        String whitespace = node.getAttribute("whitespace");
        if (whitespace != null) {
            if ("trim".equalsIgnoreCase(whitespace)) {
                wsTreatment = XMLString.SUPPRESS_BOTH;
            } else if ("normalize".equalsIgnoreCase(whitespace)) {
                wsTreatment = XMLString.NORMALIZE;
            }
        }

        String caseStr = node.getAttribute("case");
        if (caseStr != null && caseStr.length() > 0) {
            caseSensitive = caseStr.equalsIgnoreCase("yes");
        }
    }

    @Override
    public QName getQName() {
        return getNodePath().getLastComponent();
    }

    @Override
    public TextCollector getCollector(NodePath path) {
        return new SimpleTextCollector(this, includeNested, wsTreatment, caseSensitive);
    }

    @Override
    public int getType(String fieldName) {
        // no fields: return type
        return type;
    }

    @Override
    public int getType() {
        return type;
    }

    public NodePath getNodePath() {
        return path;
    }

    @Override
    public boolean match(final NodePath otherPath) {
        if (isQNameIndex) {
            final QName qn1 = path.getLastComponent();
            final QName qn2 = otherPath.getLastComponent();
            return qn1.getNameType() == qn2.getNameType() && qn2.equals(qn1);
        } else {
            return otherPath.match(path);
        }
    }

    @Override
    public boolean match(final NodePath otherPath, final Node other) {
        return match(otherPath);
    }
}
