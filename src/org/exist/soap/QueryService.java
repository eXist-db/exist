/**
 * QueryService.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.2.1 Jun 14, 2005 (09:15:57 EDT) WSDL2Java emitter.
 */

package org.exist.soap;

public interface QueryService extends javax.xml.rpc.Service {
    public java.lang.String getQueryAddress();

    public org.exist.soap.Query getQuery() throws javax.xml.rpc.ServiceException;

    public org.exist.soap.Query getQuery(java.net.URL portAddress) throws javax.xml.rpc.ServiceException;
}
