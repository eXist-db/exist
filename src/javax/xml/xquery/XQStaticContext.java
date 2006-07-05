package javax.xml.xquery;

import javax.xml.namespace.QName;

/**
 * XQJ interfaces reconstructed from version 0.5 documentation
 */
public interface XQStaticContext {
    java.lang.String getBaseURI() throws XQException;

    int getBoundarySpacePolicy() throws XQException;

    int getConstructionMode() throws XQException;

    int getCopyNamespacesModeInherit() throws XQException;

    int getCopyNamespacesModePreserve() throws XQException;

    java.lang.String getDefaultCollation() throws XQException;

    java.lang.String getDefaultElementTypeNamespace() throws XQException;

    java.lang.String getDefaultFunctionNamespace() throws XQException;

    int getDefaultOrderForEmptySequences() throws XQException;

    java.lang.String[] getInScopeNamespacePrefixes() throws XQException;

    java.lang.String getNamespaceURI(java.lang.String prefix) throws XQException;

    int getOrderingMode() throws XQException;

    QName[] getStaticInScopeVariableNames() throws XQException;

    XQSequenceType getStaticInScopeVariableType(QName varname) throws XQException;
}
