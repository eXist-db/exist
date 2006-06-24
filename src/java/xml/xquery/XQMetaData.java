package javax.xml.xquery;

/**
 * XQJ interfaces reconstructed from version 0.5 documentation
 */
public interface XQMetaData {

    int getMaxExpressionLength();

    int getMaxUserNameLength();

    int getProductMajorVersion();

    int getProductMinorVersion();

    java.lang.String getProductName();

    java.lang.String getProductVersion();

    java.lang.String getUserName();

    int getXQJMajorVersion();

    int getXQJMinorVersion();

    java.lang.String getXQJVersion();

    boolean isCollectionNestingSupported();

    boolean isFullAxisFeatureSupported();

    boolean isModuleFeatureSupported();

    boolean isReadOnly();

    boolean isSchemaImportFeatureSupported();

    boolean isSchemaValidationFeatureSupported();

    boolean isSerializationFeatureSupported();

    boolean isStaticTypingExtensionsSupported();

    boolean isStaticTypingFeatureSupported();

    boolean isTransactionSupported();

    boolean isXQueryXSupported();

    boolean wasCreatedFromJDBCConnection();
}
