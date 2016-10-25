package org.exist.indexing.range.config;

import org.exist.dom.QName;
import org.exist.dom.persistent.AttrImpl;
import org.exist.storage.ElementValue;
import org.exist.storage.NodePath;
import org.exist.util.DatabaseConfigurationException;
import org.exist.xquery.value.Type;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.XMLConstants;
import java.util.Map;

/**
 * Created by aretter on 04/05/2015.
 */
public class IdIndexConfigElement extends AbstractIdIndexConfigElement {

    public final static QName ID_QN = new QName("id", XMLConstants.XML_NS_URI, ElementValue.ATTRIBUTE);

    public IdIndexConfigElement(final Element node, final Map<String, String> namespaces) throws DatabaseConfigurationException {
        super(node, namespaces);
    }

    @Override
    public QName getQName() {
        return ID_QN;
    }

    @Override
    public int getType() {
        return Type.ID;
    }

    @Override
    public boolean match(final NodePath otherPath, final Node other) {
        if(other instanceof AttrImpl) {
            final AttrImpl attr = ((AttrImpl)other);
            return attr.getType() == AttrImpl.ID;
        }

        return false;
    }
}
