/**
 * QueryServiceLocator.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis WSDL2Java emitter.
 */

package org.exist.soap;

public class QueryServiceLocator extends org.apache.axis.client.Service implements org.exist.soap.QueryService {

    // Use to get a proxy class for Query
    private final java.lang.String Query_address = "http://localhost:8080/exist/services/Query";

    public java.lang.String getQueryAddress() {
        return Query_address;
    }

    // The WSDD service name defaults to the port name.
    private java.lang.String QueryWSDDServiceName = "Query";

    public java.lang.String getQueryWSDDServiceName() {
        return QueryWSDDServiceName;
    }

    public void setQueryWSDDServiceName(java.lang.String name) {
        QueryWSDDServiceName = name;
    }

    public org.exist.soap.Query getQuery() throws javax.xml.rpc.ServiceException {
       java.net.URL endpoint;
        try {
            endpoint = new java.net.URL(Query_address);
        }
        catch (java.net.MalformedURLException e) {
            throw new javax.xml.rpc.ServiceException(e);
        }
        return getQuery(endpoint);
    }

    public org.exist.soap.Query getQuery(java.net.URL portAddress) throws javax.xml.rpc.ServiceException {
        try {
            org.exist.soap.QuerySoapBindingStub _stub = new org.exist.soap.QuerySoapBindingStub(portAddress, this);
            _stub.setPortName(getQueryWSDDServiceName());
            return _stub;
        }
        catch (org.apache.axis.AxisFault e) {
            return null;
        }
    }

    /**
     * For the given interface, get the stub implementation.
     * If this service has no port for the given interface,
     * then ServiceException is thrown.
     */
    public java.rmi.Remote getPort(Class serviceEndpointInterface) throws javax.xml.rpc.ServiceException {
        try {
            if (org.exist.soap.Query.class.isAssignableFrom(serviceEndpointInterface)) {
                org.exist.soap.QuerySoapBindingStub _stub = new org.exist.soap.QuerySoapBindingStub(new java.net.URL(Query_address), this);
                _stub.setPortName(getQueryWSDDServiceName());
                return _stub;
            }
        }
        catch (java.lang.Throwable t) {
            throw new javax.xml.rpc.ServiceException(t);
        }
        throw new javax.xml.rpc.ServiceException("There is no stub implementation for the interface:  " + (serviceEndpointInterface == null ? "null" : serviceEndpointInterface.getName()));
    }

    /**
     * For the given interface, get the stub implementation.
     * If this service has no port for the given interface,
     * then ServiceException is thrown.
     */
    public java.rmi.Remote getPort(javax.xml.namespace.QName portName, Class serviceEndpointInterface) throws javax.xml.rpc.ServiceException {
        if (portName == null) {
            return getPort(serviceEndpointInterface);
        }
        String inputPortName = portName.getLocalPart();
        if ("Query".equals(inputPortName)) {
            return getQuery();
        }
        else  {
            java.rmi.Remote _stub = getPort(serviceEndpointInterface);
            ((org.apache.axis.client.Stub) _stub).setPortName(portName);
            return _stub;
        }
    }

    public javax.xml.namespace.QName getServiceName() {
        return new javax.xml.namespace.QName("urn:exist", "QueryService");
    }

    private java.util.HashSet ports = null;

    public java.util.Iterator getPorts() {
        if (ports == null) {
            ports = new java.util.HashSet();
            ports.add(new javax.xml.namespace.QName("Query"));
        }
        return ports.iterator();
    }

}
