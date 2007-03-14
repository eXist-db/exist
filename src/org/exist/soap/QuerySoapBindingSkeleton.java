/**
 * QuerySoapBindingSkeleton.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.2.1 Jun 14, 2005 (09:15:57 EDT) WSDL2Java emitter.
 */

package org.exist.soap;

import org.exist.Namespaces;

public class QuerySoapBindingSkeleton implements org.exist.soap.Query, org.apache.axis.wsdl.Skeleton {
    private org.exist.soap.Query impl;
    private static java.util.Map _myOperations = new java.util.Hashtable();
    private static java.util.Collection _myOperationsList = new java.util.ArrayList();

    /**
    * Returns List of OperationDesc objects with this name
    */
    public static java.util.List getOperationDescByName(java.lang.String methodName) {
        return (java.util.List)_myOperations.get(methodName);
    }

    /**
    * Returns Collection of OperationDescs
    */
    public static java.util.Collection getOperationDescs() {
        return _myOperationsList;
    }

    static {
        org.apache.axis.description.OperationDesc _oper;
        org.apache.axis.description.FaultDesc _fault;
        org.apache.axis.description.ParameterDesc [] _params;
        _params = new org.apache.axis.description.ParameterDesc [] {
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "sessionId"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "path"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "indent"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "boolean"), boolean.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "xinclude"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "boolean"), boolean.class, false, false), 
        };
        _oper = new org.apache.axis.description.OperationDesc("getResource", _params, new javax.xml.namespace.QName("urn:exist", "getResourceReturn"));
        _oper.setReturnType(new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"));
        _oper.setElementQName(new javax.xml.namespace.QName("urn:exist", "getResource"));
        _oper.setSoapAction("");
        _myOperationsList.add(_oper);
        if (_myOperations.get("getResource") == null) {
            _myOperations.put("getResource", new java.util.ArrayList());
        }
        ((java.util.List)_myOperations.get("getResource")).add(_oper);
        _params = new org.apache.axis.description.ParameterDesc [] {
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "sessionId"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "xpath"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
        };
        _oper = new org.apache.axis.description.OperationDesc("query", _params, new javax.xml.namespace.QName("urn:exist", "queryReturn"));
        _oper.setReturnType(new javax.xml.namespace.QName("urn:exist", "QueryResponse"));
        _oper.setElementQName(new javax.xml.namespace.QName("urn:exist", "query"));
        _oper.setSoapAction("");
        _myOperationsList.add(_oper);
        if (_myOperations.get("query") == null) {
            _myOperations.put("query", new java.util.ArrayList());
        }
        ((java.util.List)_myOperations.get("query")).add(_oper);
        _params = new org.apache.axis.description.ParameterDesc [] {
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "userId"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "password"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
        };
        _oper = new org.apache.axis.description.OperationDesc("connect", _params, new javax.xml.namespace.QName("urn:exist", "connectReturn"));
        _oper.setReturnType(new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"));
        _oper.setElementQName(new javax.xml.namespace.QName("urn:exist", "connect"));
        _oper.setSoapAction("");
        _myOperationsList.add(_oper);
        if (_myOperations.get("connect") == null) {
            _myOperations.put("connect", new java.util.ArrayList());
        }
        ((java.util.List)_myOperations.get("connect")).add(_oper);
        _params = new org.apache.axis.description.ParameterDesc [] {
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "sessionId"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "start"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "int"), int.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "howmany"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "int"), int.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "indent"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "boolean"), boolean.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "xinclude"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "boolean"), boolean.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "highlight"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
        };
        _oper = new org.apache.axis.description.OperationDesc("retrieve", _params, new javax.xml.namespace.QName("urn:exist", "retrieveReturn"));
        _oper.setReturnType(new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"));
        _oper.setElementQName(new javax.xml.namespace.QName("urn:exist", "retrieve"));
        _oper.setSoapAction("");
        _myOperationsList.add(_oper);
        if (_myOperations.get("retrieve") == null) {
            _myOperations.put("retrieve", new java.util.ArrayList());
        }
        ((java.util.List)_myOperations.get("retrieve")).add(_oper);
        _params = new org.apache.axis.description.ParameterDesc [] {
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "sessionId"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
        };
        _oper = new org.apache.axis.description.OperationDesc("disconnect", _params, null);
        _oper.setElementQName(new javax.xml.namespace.QName("urn:exist", "disconnect"));
        _oper.setSoapAction("");
        _myOperationsList.add(_oper);
        if (_myOperations.get("disconnect") == null) {
            _myOperations.put("disconnect", new java.util.ArrayList());
        }
        ((java.util.List)_myOperations.get("disconnect")).add(_oper);
        _params = new org.apache.axis.description.ParameterDesc [] {
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "sessionId"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "path"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "indent"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "boolean"), boolean.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "xinclude"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "boolean"), boolean.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "processXSLPI"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "boolean"), boolean.class, false, false), 
        };
        _oper = new org.apache.axis.description.OperationDesc("getResourceData", _params, new javax.xml.namespace.QName("urn:exist", "getResourceDataReturn"));
        _oper.setReturnType(new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "base64Binary"));
        _oper.setElementQName(new javax.xml.namespace.QName("urn:exist", "getResourceData"));
        _oper.setSoapAction("");
        _myOperationsList.add(_oper);
        if (_myOperations.get("getResourceData") == null) {
            _myOperations.put("getResourceData", new java.util.ArrayList());
        }
        ((java.util.List)_myOperations.get("getResourceData")).add(_oper);
        _params = new org.apache.axis.description.ParameterDesc [] {
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "sessionId"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "xquery"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "base64Binary"), byte[].class, false, false), 
        };
        _oper = new org.apache.axis.description.OperationDesc("xquery", _params, new javax.xml.namespace.QName("urn:exist", "xqueryReturn"));
        _oper.setReturnType(new javax.xml.namespace.QName("urn:exist", "QueryResponse"));
        _oper.setElementQName(new javax.xml.namespace.QName("urn:exist", "xquery"));
        _oper.setSoapAction("");
        _myOperationsList.add(_oper);
        if (_myOperations.get("xquery") == null) {
            _myOperations.put("xquery", new java.util.ArrayList());
        }
        ((java.util.List)_myOperations.get("xquery")).add(_oper);
        _params = new org.apache.axis.description.ParameterDesc [] {
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "sessionId"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "start"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "int"), int.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "howmany"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "int"), int.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "indent"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "boolean"), boolean.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "xinclude"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "boolean"), boolean.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "highlight"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
        };
        _oper = new org.apache.axis.description.OperationDesc("retrieveData", _params, new javax.xml.namespace.QName("urn:exist", "retrieveDataReturn"));
        _oper.setReturnType(new javax.xml.namespace.QName("urn:exist", "Base64BinaryArray"));
        _oper.setElementQName(new javax.xml.namespace.QName("urn:exist", "retrieveData"));
        _oper.setSoapAction("");
        _myOperationsList.add(_oper);
        if (_myOperations.get("retrieveData") == null) {
            _myOperations.put("retrieveData", new java.util.ArrayList());
        }
        ((java.util.List)_myOperations.get("retrieveData")).add(_oper);
        _params = new org.apache.axis.description.ParameterDesc [] {
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "sessionId"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "start"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "int"), int.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "howmany"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "int"), int.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "path"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "indent"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "boolean"), boolean.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "xinclude"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "boolean"), boolean.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "highlight"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
        };
        _oper = new org.apache.axis.description.OperationDesc("retrieveByDocument", _params, new javax.xml.namespace.QName("urn:exist", "retrieveByDocumentReturn"));
        _oper.setReturnType(new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"));
        _oper.setElementQName(new javax.xml.namespace.QName("urn:exist", "retrieveByDocument"));
        _oper.setSoapAction("");
        _myOperationsList.add(_oper);
        if (_myOperations.get("retrieveByDocument") == null) {
            _myOperations.put("retrieveByDocument", new java.util.ArrayList());
        }
        ((java.util.List)_myOperations.get("retrieveByDocument")).add(_oper);
        _params = new org.apache.axis.description.ParameterDesc [] {
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "sessionId"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "path"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
        };
        _oper = new org.apache.axis.description.OperationDesc("listCollection", _params, new javax.xml.namespace.QName("urn:exist", "listCollectionReturn"));
        _oper.setReturnType(new javax.xml.namespace.QName("urn:exist", "Collection"));
        _oper.setElementQName(new javax.xml.namespace.QName("urn:exist", "listCollection"));
        _oper.setSoapAction("");
        _myOperationsList.add(_oper);
        if (_myOperations.get("listCollection") == null) {
            _myOperations.put("listCollection", new java.util.ArrayList());
        }
        ((java.util.List)_myOperations.get("listCollection")).add(_oper);
    }

    public QuerySoapBindingSkeleton() {
        this.impl = new org.exist.soap.QuerySoapBindingImpl();
    }

    public QuerySoapBindingSkeleton(org.exist.soap.Query impl) {
        this.impl = impl;
    }
    public java.lang.String getResource(java.lang.String sessionId, java.lang.String path, boolean indent, boolean xinclude) throws java.rmi.RemoteException
    {
        java.lang.String ret = impl.getResource(sessionId, path, indent, xinclude);
        return ret;
    }

    public org.exist.soap.QueryResponse query(java.lang.String sessionId, java.lang.String xpath) throws java.rmi.RemoteException
    {
        org.exist.soap.QueryResponse ret = impl.query(sessionId, xpath);
        return ret;
    }

    public java.lang.String connect(java.lang.String userId, java.lang.String password) throws java.rmi.RemoteException
    {
        java.lang.String ret = impl.connect(userId, password);
        return ret;
    }

    public java.lang.String[] retrieve(java.lang.String sessionId, int start, int howmany, boolean indent, boolean xinclude, java.lang.String highlight) throws java.rmi.RemoteException
    {
        java.lang.String[] ret = impl.retrieve(sessionId, start, howmany, indent, xinclude, highlight);
        return ret;
    }

    public void disconnect(java.lang.String sessionId) throws java.rmi.RemoteException
    {
        impl.disconnect(sessionId);
    }

    public byte[] getResourceData(java.lang.String sessionId, java.lang.String path, boolean indent, boolean xinclude, boolean processXSLPI) throws java.rmi.RemoteException
    {
        byte[] ret = impl.getResourceData(sessionId, path, indent, xinclude, processXSLPI);
        return ret;
    }

    public org.exist.soap.QueryResponse xquery(java.lang.String sessionId, byte[] xquery) throws java.rmi.RemoteException
    {
        org.exist.soap.QueryResponse ret = impl.xquery(sessionId, xquery);
        return ret;
    }

    public org.exist.soap.Base64BinaryArray retrieveData(java.lang.String sessionId, int start, int howmany, boolean indent, boolean xinclude, java.lang.String highlight) throws java.rmi.RemoteException
    {
        org.exist.soap.Base64BinaryArray ret = impl.retrieveData(sessionId, start, howmany, indent, xinclude, highlight);
        return ret;
    }

    public java.lang.String[] retrieveByDocument(java.lang.String sessionId, int start, int howmany, java.lang.String path, boolean indent, boolean xinclude, java.lang.String highlight) throws java.rmi.RemoteException
    {
        java.lang.String[] ret = impl.retrieveByDocument(sessionId, start, howmany, path, indent, xinclude, highlight);
        return ret;
    }

    public org.exist.soap.Collection listCollection(java.lang.String sessionId, java.lang.String path) throws java.rmi.RemoteException
    {
        org.exist.soap.Collection ret = impl.listCollection(sessionId, path);
        return ret;
    }

}
