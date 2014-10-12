package org.exist.dom;

import org.exist.xquery.Constants;

import java.util.Comparator;

public class TypedQNameComparator implements Comparator<QName> {

    @Override
    public int compare(final QName q1, final QName q2) {
        if(q1.getNameType() != q2.getNameType()) {
        return q1.getNameType() < q2.getNameType() ? Constants.INFERIOR : Constants.SUPERIOR;
        }
        final int c = q1.getNamespaceURI().compareTo(q2.getNamespaceURI());
        return c == Constants.EQUAL ? q1.getLocalPart().compareTo(q2.getLocalPart()) : c;
    }
}
