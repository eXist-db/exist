/**
 * QuerySoapBindingSkeleton.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis WSDL2Java emitter.
 */

package org.exist.soap;

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
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("", "in0"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("", "in1"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("", "in2"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "boolean"), boolean.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("", "in3"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "boolean"), boolean.class, false, false), 
        };
        _oper = new org.apache.axis.description.OperationDesc("getResource", _params, new javax.xml.namespace.QName("", "getResourceReturn"));
        _oper.setReturnType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
        _oper.setElementQName(new javax.xml.namespace.QName("urn:exist", "getResource"));
        _oper.setSoapAction("");
        _myOperationsList.add(_oper);
        if (_myOperations.get("getResource") == null) {
            _myOperations.put("getResource", new java.util.ArrayList());
        }
        ((java.util.List)_myOperations.get("getResource")).add(_oper);
        _params = new org.apache.axis.description.ParameterDesc [] {
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("", "in0"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("", "in1"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"), java.lang.String.class, false, false), 
        };
        _oper = new org.apache.axis.description.OperationDesc("query", _params, new javax.xml.namespace.QName("", "queryReturn"));
        _oper.setReturnType(new javax.xml.namespace.QName("urn:exist", "QueryResponse"));
        _oper.setElementQName(new javax.xml.namespace.QName("urn:exist", "query"));
        _oper.setSoapAction("");
        _myOperationsList.add(_oper);
        if (_myOperations.get("query") == null) {
            _myOperations.put("query", new java.util.ArrayList());
        }
        ((java.util.List)_myOperations.get("query")).add(_oper);
        _params = new org.apache.axis.description.ParameterDesc [] {
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("", "in0"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("", "in1"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"), java.lang.String.class, false, false), 
        };
        _oper = new org.apache.axis.description.OperationDesc("connect", _params, new javax.xml.namespace.QName("", "connectReturn"));
        _oper.setReturnType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"));
        _oper.setElementQName(new javax.xml.namespace.QName("urn:exist", "connect"));
        _oper.setSoapAction("");
        _myOperationsList.add(_oper);
        if (_myOperations.get("connect") == null) {
            _myOperations.put("connect", new java.util.ArrayList());
        }
        ((java.util.List)_myOperations.get("connect")).add(_oper);
        _params = new org.apache.axis.description.ParameterDesc [] {
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("", "in0"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("", "in1"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "int"), int.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("", "in2"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "int"), int.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("", "in3"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "boolean"), boolean.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("", "in4"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "boolean"), boolean.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("", "in5"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"), java.lang.String.class, false, false), 
        };
        _oper = new org.apache.axis.description.OperationDesc("retrieve", _params, new javax.xml.namespace.QName("", "retrieveReturn"));
        _oper.setReturnType(new javax.xml.namespace.QName("urn:exist", "ArrayOf_xsd_string"));
        _oper.setElementQName(new javax.xml.namespace.QName("urn:exist", "retrieve"));
        _oper.setSoapAction("");
        _myOperationsList.add(_oper);
        if (_myOperations.get("retrieve") == null) {
            _myOperations.put("retrieve", new java.util.ArrayList());
        }
        ((java.util.List)_myOperations.get("retrieve")).add(_oper);
        _params = new org.apache.axis.description.ParameterDesc [] {
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("", "in0"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"), java.lang.String.class, false, false), 
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
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("", "in0"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("", "in1"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("", "in2"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "boolean"), boolean.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("", "in3"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "boolean"), boolean.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("", "in4"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "boolean"), boolean.class, false, false), 
        };
        _oper = new org.apache.axis.description.OperationDesc("getResourceData", _params, new javax.xml.namespace.QName("", "getResourceDataReturn"));
        _oper.setReturnType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "base64Binary"));
        _oper.setElementQName(new javax.xml.namespace.QName("urn:exist", "getResourceData"));
        _oper.setSoapAction("");
        _myOperationsList.add(_oper);
        if (_myOperations.get("getResourceData") == null) {
            _myOperations.put("getResourceData", new java.util.ArrayList());
        }
        ((java.util.List)_myOperations.get("getResourceData")).add(_oper);
        _params = new org.apache.axis.description.ParameterDesc [] {
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("", "in0"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("", "in1"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "base64Binary"), byte[].class, false, false), 
        };
        _oper = new org.apache.axis.description.OperationDesc("xquery", _params, new javax.xml.namespace.QName("", "xqueryReturn"));
        _oper.setReturnType(new javax.xml.namespace.QName("urn:exist", "QueryResponse"));
        _oper.setElementQName(new javax.xml.namespace.QName("urn:exist", "xquery"));
        _oper.setSoapAction("");
        _myOperationsList.add(_oper);
        if (_myOperations.get("xquery") == null) {
            _myOperations.put("xquery", new java.util.ArrayList());
        }
        ((java.util.List)_myOperations.get("xquery")).add(_oper);
        _params = new org.apache.axis.description.ParameterDesc [] {
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("", "in0"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("", "in1"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "int"), int.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("", "in2"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "int"), int.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("", "in3"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "boolean"), boolean.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("", "in4"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "boolean"), boolean.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("", "in5"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"), java.lang.String.class, false, false), 
        };
        _oper = new org.apache.axis.description.OperationDesc("retrieveData", _params, new javax.xml.namespace.QName("", "retrieveDataReturn"));
        _oper.setReturnType(new javax.xml.namespace.QName("urn:exist", "ArrayOf_xsd_base64Binary"));
        _oper.setElementQName(new javax.xml.namespace.QName("urn:exist", "retrieveData"));
        _oper.setSoapAction("");
        _myOperationsList.add(_oper);
        if (_myOperations.get("retrieveData") == null) {
            _myOperations.put("retrieveData", new java.util.ArrayList());
        }
        ((java.util.List)_myOperations.get("retrieveData")).add(_oper);
        _params = new org.apache.axis.description.ParameterDesc [] {
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("", "in0"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("", "in1"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "int"), int.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("", "in2"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "int"), int.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("", "in3"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("", "in4"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "boolean"), boolean.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("", "in5"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "boolean"), boolean.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("", "in6"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"), java.lang.String.class, false, false), 
        };
        _oper = new org.apache.axis.description.OperationDesc("retrieveByDocument", _params, new javax.xml.namespace.QName("", "retrieveByDocumentReturn"));
        _oper.setReturnType(new javax.xml.namespace.QName("urn:exist", "ArrayOf_xsd_string"));
        _oper.setElementQName(new javax.xml.namespace.QName("urn:exist", "retrieveByDocument"));
        _oper.setSoapAction("");
        _myOperationsList.add(_oper);
        if (_myOperations.get("retrieveByDocument") == null) {
            _myOperations.put("retrieveByDocument", new java.util.ArrayList());
        }
        ((java.util.List)_myOperations.get("retrieveByDocument")).add(_oper);
        _params = new org.apache.axis.description.ParameterDesc [] {
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("", "in0"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("", "in1"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"), java.lang.String.class, false, false), 
        };
        _oper = new org.apache.axis.description.OperationDesc("listCollection", _params, new javax.xml.namespace.QName("", "listCollectionReturn"));
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
    public java.lang.String getResource(java.lang.String in0, java.lang.String in1, boolean in2, boolean in3) throws java.rmi.RemoteException
    {
        java.lang.String ret = impl.getResource(in0, in1, in2, in3);
        return ret;
    }

    public org.exist.soap.QueryResponse query(java.lang.String in0, java.lang.String in1) throws java.rmi.RemoteException
    {
        org.exist.soap.QueryResponse ret = impl.query(in0, in1);
        return ret;
    }

    public java.lang.String connect(java.lang.String in0, java.lang.String in1) throws java.rmi.RemoteException
    {
        java.lang.String ret = impl.connect(in0, in1);
        return ret;
    }

    public java.lang.String[] retrieve(java.lang.String in0, int in1, int in2, boolean in3, boolean in4, java.lang.String in5) throws java.rmi.RemoteException
    {
        java.lang.String[] ret = impl.retrieve(in0, in1, in2, in3, in4, in5);
        return ret;
    }

    public void disconnect(java.lang.String in0) throws java.rmi.RemoteException
    {
        impl.disconnect(in0);
    }

    public byte[] getResourceData(java.lang.String in0, java.lang.String in1, boolean in2, boolean in3, boolean in4) throws java.rmi.RemoteException
    {
        byte[] ret = impl.getResourceData(in0, in1, in2, in3, in4);
        return ret;
    }

    public org.exist.soap.QueryResponse xquery(java.lang.String in0, byte[] in1) throws java.rmi.RemoteException
    {
        org.exist.soap.QueryResponse ret = impl.xquery(in0, in1);
        return ret;
    }

    public byte[][] retrieveData(java.lang.String in0, int in1, int in2, boolean in3, boolean in4, java.lang.String in5) throws java.rmi.RemoteException
    {
        byte[][] ret = impl.retrieveData(in0, in1, in2, in3, in4, in5);
        return ret;
    }

    public java.lang.String[] retrieveByDocument(java.lang.String in0, int in1, int in2, java.lang.String in3, boolean in4, boolean in5, java.lang.String in6) throws java.rmi.RemoteException
    {
        java.lang.String[] ret = impl.retrieveByDocument(in0, in1, in2, in3, in4, in5, in6);
        return ret;
    }

    public org.exist.soap.Collection listCollection(java.lang.String in0, java.lang.String in1) throws java.rmi.RemoteException
    {
        org.exist.soap.Collection ret = impl.listCollection(in0, in1);
        return ret;
    }

}
