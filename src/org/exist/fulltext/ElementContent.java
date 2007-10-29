package org.exist.fulltext;

import org.apache.log4j.Logger;
import org.exist.dom.QName;
import org.exist.util.XMLString;

/**
 *
 */
public class ElementContent {

    private static final Logger LOG = Logger.getLogger(ElementContent.class);

    public static class TextSpan {
        XMLString content;
        TextSpan next = null;

        TextSpan(XMLString content) {
            this.content = new XMLString(content);
        }

        public XMLString getContent() {
            return content;
        }

        public TextSpan getNext() {
            return next;
        }
    }

    private QName nodeName;
    private boolean mixedContent;
    private TextSpan first = null;
    private TextSpan last = null;

    public ElementContent(QName nodeName, boolean mixedContent) {
        this.nodeName = nodeName;
        this.mixedContent = mixedContent;
    }

    public void append(XMLString string) {
        if (mixedContent)
            appendString(string);
        else
            appendSpan(string);
    }
    
    public void appendSpan(XMLString string) {
        if (first == null) {
            first = new TextSpan(string);
            last = first;
        } else {
            TextSpan span = new TextSpan(string);
            last.next = span;
            last = span;
        }
    }

    public void appendString(XMLString string) {
        if (first == null) {
            first = new TextSpan(string);
            last = first;
        } else {
            last.content.append(string);
        }
    }

    public QName getNodeName() {
        return nodeName;
    }

    public void setMixedContent(boolean mixedContent) {
        this.mixedContent = mixedContent;
    }

    public TextSpan getFirst() {
        return first;
    }

    public String toString() {
        StringBuffer buf = new StringBuffer();
        TextSpan span = getFirst();
        while (span != null) {
            buf.append(span.getContent()).append('|');
            span = span.getNext();
        }
        return buf.toString();
    }
}
