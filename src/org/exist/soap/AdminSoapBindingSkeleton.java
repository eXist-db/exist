/**
 * AdminSoapBindingSkeleton.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis WSDL2Java emitter.
 */

package org.exist.soap;

public class AdminSoapBindingSkeleton implements org.exist.soap.Admin, org.apache.axis.wsdl.Skeleton {
    private org.exist.soap.Admin impl;
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
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("", "in1"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "base64Binary"), byte[].class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("", "in2"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("", "in3"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("", "in4"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "boolean"), boolean.class, false, false), 
        };
        _oper = new org.apache.axis.description.OperationDesc("store", _params, null);
        _oper.setElementQName(new javax.xml.namespace.QName("urn:exist", "store"));
        _oper.setSoapAction("");
        _myOperationsList.add(_oper);
        if (_myOperations.get("store") == null) {
            _myOperations.put("store", new java.util.ArrayList());
        }
        ((java.util.List)_myOperations.get("store")).add(_oper);
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
        };
        _oper = new org.apache.axis.description.OperationDesc("removeCollection", _params, new javax.xml.namespace.QName("", "removeCollectionReturn"));
        _oper.setReturnType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "boolean"));
        _oper.setElementQName(new javax.xml.namespace.QName("urn:exist", "removeCollection"));
        _oper.setSoapAction("");
        _myOperationsList.add(_oper);
        if (_myOperations.get("removeCollection") == null) {
            _myOperations.put("removeCollection", new java.util.ArrayList());
        }
        ((java.util.List)_myOperations.get("removeCollection")).add(_oper);
        _params = new org.apache.axis.description.ParameterDesc [] {
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("", "in0"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("", "in1"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"), java.lang.String.class, false, false), 
        };
        _oper = new org.apache.axis.description.OperationDesc("removeDocument", _params, new javax.xml.namespace.QName("", "removeDocumentReturn"));
        _oper.setReturnType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "boolean"));
        _oper.setElementQName(new javax.xml.namespace.QName("urn:exist", "removeDocument"));
        _oper.setSoapAction("");
        _myOperationsList.add(_oper);
        if (_myOperations.get("removeDocument") == null) {
            _myOperations.put("removeDocument", new java.util.ArrayList());
        }
        ((java.util.List)_myOperations.get("removeDocument")).add(_oper);
        _params = new org.apache.axis.description.ParameterDesc [] {
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("", "in0"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("", "in1"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "string"), java.lang.String.class, false, false), 
        };
        _oper = new org.apache.axis.description.OperationDesc("createCollection", _params, new javax.xml.namespace.QName("", "createCollectionReturn"));
        _oper.setReturnType(new javax.xml.namespace.QName("http://www.w3.org/2001/XMLSchema", "boolean"));
        _oper.setElementQName(new javax.xml.namespace.QName("urn:exist", "createCollection"));
        _oper.setSoapAction("");
        _myOperationsList.add(_oper);
        if (_myOperations.get("createCollection") == null) {
            _myOperations.put("createCollection", new java.util.ArrayList());
        }
        ((java.util.List)_myOperations.get("createCollection")).add(_oper);
    }

    public AdminSoapBindingSkeleton() {
        this.impl = new org.exist.soap.AdminSoapBindingImpl();
    }

    public AdminSoapBindingSkeleton(org.exist.soap.Admin impl) {
        this.impl = impl;
    }
    public void store(java.lang.String in0, byte[] in1, java.lang.String in2, java.lang.String in3, boolean in4) throws java.rmi.RemoteException
    {
        impl.store(in0, in1, in2, in3, in4);
    }

    public java.lang.String connect(java.lang.String in0, java.lang.String in1) throws java.rmi.RemoteException
    {
        java.lang.String ret = impl.connect(in0, in1);
        return ret;
    }

    public void disconnect(java.lang.String in0) throws java.rmi.RemoteException
    {
        impl.disconnect(in0);
    }

    public boolean removeCollection(java.lang.String in0, java.lang.String in1) throws java.rmi.RemoteException
    {
        boolean ret = impl.removeCollection(in0, in1);
        return ret;
    }

    public boolean removeDocument(java.lang.String in0, java.lang.String in1) throws java.rmi.RemoteException
    {
        boolean ret = impl.removeDocument(in0, in1);
        return ret;
    }

    public boolean createCollection(java.lang.String in0, java.lang.String in1) throws java.rmi.RemoteException
    {
        boolean ret = impl.createCollection(in0, in1);
        return ret;
    }

}
