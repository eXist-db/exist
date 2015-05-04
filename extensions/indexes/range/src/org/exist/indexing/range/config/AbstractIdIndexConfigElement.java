package org.exist.indexing.range.config;

import org.exist.dom.QName;
import org.exist.indexing.range.SimpleTextCollector;
import org.exist.indexing.range.TextCollector;
import org.exist.storage.ElementValue;
import org.exist.storage.NodePath;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.XMLString;
import org.w3c.dom.Element;

import java.util.Map;

/**
 * Created by aretter on 04/05/2015.
 */
public abstract class AbstractIdIndexConfigElement  extends AbstractRangeIndexConfigElement {

    public AbstractIdIndexConfigElement(final Element node, final Map<String, String> namespaces) throws DatabaseConfigurationException {
        super(node, namespaces);
    }

    @Override
    public int getType(final String fieldName) {
        return getType();
    }

    @Override
    public boolean match(final NodePath otherPath) {
        final QName otherQn = otherPath.getLastComponent();
        return otherQn.getNameType() == ElementValue.ATTRIBUTE && otherQn.equals(getQName());
    }

    @Override
    public TextCollector getCollector(final NodePath path) {
        return new SimpleTextCollector(this, false, XMLString.SUPPRESS_NONE, caseSensitive);
    }
}
