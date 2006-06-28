package javax.xml.xquery;

import javax.xml.namespace.QName;
import java.io.Serializable;

/**
 * XQJ interfaces reconstructed from version 0.5 documentation
 */
public class XQStackTraceVariable implements Serializable {
    private QName qname;
    private String value;

    XQStackTraceVariable(QName qname, java.lang.String value) {
        this.qname = qname;
        this.value = value;
    }

    public QName getQName() {
        return qname;
    }

    public String getValue() {
        return value;
    }
}
