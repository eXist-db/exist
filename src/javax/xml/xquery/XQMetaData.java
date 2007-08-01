package javax.xml.xquery;


/**
 * XQJ interfaces reconstructed from version 0.5 documentation
 */
public interface XQMetaData {

    int getMaxExpressionLength() throws XQException;

    int getMaxUserNameLength() throws XQException;

    int getProductMajorVersion() throws XQException;

    int getProductMinorVersion() throws XQException;

    java.lang.String getProductName() throws XQException;

    java.lang.String getProductVersion() throws XQException;

    java.lang.String getUserName() throws XQException;

    int getXQJMajorVersion() throws XQException;

    int getXQJMinorVersion() throws XQException;

    java.lang.String getXQJVersion() throws XQException;

    boolean isCollectionNestingSupported() throws XQException;

    boolean isFullAxisFeatureSupported() throws XQException;

    boolean isModuleFeatureSupported() throws XQException;

    boolean isReadOnly() throws XQException;

    boolean isSchemaImportFeatureSupported() throws XQException;

    boolean isSchemaValidationFeatureSupported() throws XQException;

    boolean isSerializationFeatureSupported() throws XQException;

    boolean isStaticTypingExtensionsSupported() throws XQException;

    boolean isStaticTypingFeatureSupported() throws XQException;

    boolean isTransactionSupported() throws XQException;

    boolean isXQueryXSupported() throws XQException;

    boolean wasCreatedFromJDBCConnection() throws XQException;
}
