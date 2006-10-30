package javax.xml.xquery;

import javax.xml.namespace.QName;
import java.io.Serializable;

/**
 * XQJ interfaces reconstructed from version 0.5 documentation
 */
public class XQStackTraceElement implements Serializable {
    String moduleURI;
    int lineNumber;
    int position;
    QName function;
    XQStackTraceVariable[] variables;

    public XQStackTraceElement(java.lang.String moduleURI, int lineNumber, int position,
                               QName function, XQStackTraceVariable[] variables) {
        this.moduleURI = moduleURI;
        this.lineNumber = lineNumber;
        this.position = position;
        this.function = function;
        this.variables = variables;
    }

    public QName getFunctionQName() {
        return function;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public java.lang.String getModuleURI() {
        return moduleURI;
    }

    public int getPosition() {
        return position;
    }

    public XQStackTraceVariable[] getVariables() {
        return variables;
    }

}
