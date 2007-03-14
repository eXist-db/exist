/**
 * QuerySoapBindingStub.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.2.1 Jun 14, 2005 (09:15:57 EDT) WSDL2Java emitter.
 */

package org.exist.soap;

import org.exist.Namespaces;

public class QuerySoapBindingStub extends org.apache.axis.client.Stub implements org.exist.soap.Query {
    private java.util.Vector cachedSerClasses = new java.util.Vector();
    private java.util.Vector cachedSerQNames = new java.util.Vector();
    private java.util.Vector cachedSerFactories = new java.util.Vector();
    private java.util.Vector cachedDeserFactories = new java.util.Vector();

    static org.apache.axis.description.OperationDesc [] _operations;

    static {
        _operations = new org.apache.axis.description.OperationDesc[10];
        _initOperationDesc1();
    }

    private static void _initOperationDesc1(){
        org.apache.axis.description.OperationDesc oper;
        org.apache.axis.description.ParameterDesc param;
        oper = new org.apache.axis.description.OperationDesc();
        oper.setName("getResource");
        param = new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "sessionId"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false);
        oper.addParameter(param);
        param = new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "path"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false);
        oper.addParameter(param);
        param = new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "indent"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "boolean"), boolean.class, false, false);
        oper.addParameter(param);
        param = new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "xinclude"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "boolean"), boolean.class, false, false);
        oper.addParameter(param);
        oper.setReturnType(new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"));
        oper.setReturnClass(java.lang.String.class);
        oper.setReturnQName(new javax.xml.namespace.QName("urn:exist", "getResourceReturn"));
        oper.setStyle(org.apache.axis.constants.Style.WRAPPED);
        oper.setUse(org.apache.axis.constants.Use.LITERAL);
        _operations[0] = oper;

        oper = new org.apache.axis.description.OperationDesc();
        oper.setName("query");
        param = new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "sessionId"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false);
        oper.addParameter(param);
        param = new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "xpath"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false);
        oper.addParameter(param);
        oper.setReturnType(new javax.xml.namespace.QName("urn:exist", "QueryResponse"));
        oper.setReturnClass(org.exist.soap.QueryResponse.class);
        oper.setReturnQName(new javax.xml.namespace.QName("urn:exist", "queryReturn"));
        oper.setStyle(org.apache.axis.constants.Style.WRAPPED);
        oper.setUse(org.apache.axis.constants.Use.LITERAL);
        _operations[1] = oper;

        oper = new org.apache.axis.description.OperationDesc();
        oper.setName("connect");
        param = new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "userId"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false);
        oper.addParameter(param);
        param = new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "password"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false);
        oper.addParameter(param);
        oper.setReturnType(new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"));
        oper.setReturnClass(java.lang.String.class);
        oper.setReturnQName(new javax.xml.namespace.QName("urn:exist", "connectReturn"));
        oper.setStyle(org.apache.axis.constants.Style.WRAPPED);
        oper.setUse(org.apache.axis.constants.Use.LITERAL);
        _operations[2] = oper;

        oper = new org.apache.axis.description.OperationDesc();
        oper.setName("retrieve");
        param = new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "sessionId"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false);
        oper.addParameter(param);
        param = new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "start"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "int"), int.class, false, false);
        oper.addParameter(param);
        param = new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "howmany"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "int"), int.class, false, false);
        oper.addParameter(param);
        param = new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "indent"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "boolean"), boolean.class, false, false);
        oper.addParameter(param);
        param = new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "xinclude"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "boolean"), boolean.class, false, false);
        oper.addParameter(param);
        param = new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "highlight"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false);
        oper.addParameter(param);
        oper.setReturnType(new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"));
        oper.setReturnClass(java.lang.String[].class);
        oper.setReturnQName(new javax.xml.namespace.QName("urn:exist", "retrieveReturn"));
        oper.setStyle(org.apache.axis.constants.Style.WRAPPED);
        oper.setUse(org.apache.axis.constants.Use.LITERAL);
        _operations[3] = oper;

        oper = new org.apache.axis.description.OperationDesc();
        oper.setName("disconnect");
        param = new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "sessionId"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false);
        oper.addParameter(param);
        oper.setReturnType(org.apache.axis.encoding.XMLType.AXIS_VOID);
        oper.setStyle(org.apache.axis.constants.Style.WRAPPED);
        oper.setUse(org.apache.axis.constants.Use.LITERAL);
        _operations[4] = oper;

        oper = new org.apache.axis.description.OperationDesc();
        oper.setName("getResourceData");
        param = new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "sessionId"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false);
        oper.addParameter(param);
        param = new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "path"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false);
        oper.addParameter(param);
        param = new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "indent"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "boolean"), boolean.class, false, false);
        oper.addParameter(param);
        param = new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "xinclude"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "boolean"), boolean.class, false, false);
        oper.addParameter(param);
        param = new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "processXSLPI"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "boolean"), boolean.class, false, false);
        oper.addParameter(param);
        oper.setReturnType(new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "base64Binary"));
        oper.setReturnClass(byte[].class);
        oper.setReturnQName(new javax.xml.namespace.QName("urn:exist", "getResourceDataReturn"));
        oper.setStyle(org.apache.axis.constants.Style.WRAPPED);
        oper.setUse(org.apache.axis.constants.Use.LITERAL);
        _operations[5] = oper;

        oper = new org.apache.axis.description.OperationDesc();
        oper.setName("xquery");
        param = new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "sessionId"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false);
        oper.addParameter(param);
        param = new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "xquery"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "base64Binary"), byte[].class, false, false);
        oper.addParameter(param);
        oper.setReturnType(new javax.xml.namespace.QName("urn:exist", "QueryResponse"));
        oper.setReturnClass(org.exist.soap.QueryResponse.class);
        oper.setReturnQName(new javax.xml.namespace.QName("urn:exist", "xqueryReturn"));
        oper.setStyle(org.apache.axis.constants.Style.WRAPPED);
        oper.setUse(org.apache.axis.constants.Use.LITERAL);
        _operations[6] = oper;

        oper = new org.apache.axis.description.OperationDesc();
        oper.setName("retrieveData");
        param = new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "sessionId"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false);
        oper.addParameter(param);
        param = new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "start"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "int"), int.class, false, false);
        oper.addParameter(param);
        param = new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "howmany"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "int"), int.class, false, false);
        oper.addParameter(param);
        param = new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "indent"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "boolean"), boolean.class, false, false);
        oper.addParameter(param);
        param = new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "xinclude"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "boolean"), boolean.class, false, false);
        oper.addParameter(param);
        param = new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "highlight"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false);
        oper.addParameter(param);
        oper.setReturnType(new javax.xml.namespace.QName("urn:exist", "Base64BinaryArray"));
        oper.setReturnClass(org.exist.soap.Base64BinaryArray.class);
        oper.setReturnQName(new javax.xml.namespace.QName("urn:exist", "retrieveDataReturn"));
        oper.setStyle(org.apache.axis.constants.Style.WRAPPED);
        oper.setUse(org.apache.axis.constants.Use.LITERAL);
        _operations[7] = oper;

        oper = new org.apache.axis.description.OperationDesc();
        oper.setName("retrieveByDocument");
        param = new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "sessionId"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false);
        oper.addParameter(param);
        param = new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "start"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "int"), int.class, false, false);
        oper.addParameter(param);
        param = new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "howmany"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "int"), int.class, false, false);
        oper.addParameter(param);
        param = new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "path"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false);
        oper.addParameter(param);
        param = new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "indent"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "boolean"), boolean.class, false, false);
        oper.addParameter(param);
        param = new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "xinclude"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "boolean"), boolean.class, false, false);
        oper.addParameter(param);
        param = new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "highlight"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false);
        oper.addParameter(param);
        oper.setReturnType(new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"));
        oper.setReturnClass(java.lang.String[].class);
        oper.setReturnQName(new javax.xml.namespace.QName("urn:exist", "retrieveByDocumentReturn"));
        oper.setStyle(org.apache.axis.constants.Style.WRAPPED);
        oper.setUse(org.apache.axis.constants.Use.LITERAL);
        _operations[8] = oper;

        oper = new org.apache.axis.description.OperationDesc();
        oper.setName("listCollection");
        param = new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "sessionId"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false);
        oper.addParameter(param);
        param = new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "path"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false);
        oper.addParameter(param);
        oper.setReturnType(new javax.xml.namespace.QName("urn:exist", "Collection"));
        oper.setReturnClass(org.exist.soap.Collection.class);
        oper.setReturnQName(new javax.xml.namespace.QName("urn:exist", "listCollectionReturn"));
        oper.setStyle(org.apache.axis.constants.Style.WRAPPED);
        oper.setUse(org.apache.axis.constants.Use.LITERAL);
        _operations[9] = oper;

    }

    public QuerySoapBindingStub() throws org.apache.axis.AxisFault {
         this(null);
    }

    public QuerySoapBindingStub(java.net.URL endpointURL, javax.xml.rpc.Service service) throws org.apache.axis.AxisFault {
         this(service);
         super.cachedEndpoint = endpointURL;
    }

    public QuerySoapBindingStub(javax.xml.rpc.Service service) throws org.apache.axis.AxisFault {
        if (service == null) {
            super.service = new org.apache.axis.client.Service();
        } else {
            super.service = service;
        }
        ((org.apache.axis.client.Service)super.service).setTypeMappingVersion("1.2");
            java.lang.Class cls;
            javax.xml.namespace.QName qName;
            javax.xml.namespace.QName qName2;
            java.lang.Class beansf = org.apache.axis.encoding.ser.BeanSerializerFactory.class;
            java.lang.Class beandf = org.apache.axis.encoding.ser.BeanDeserializerFactory.class;
            java.lang.Class enumsf = org.apache.axis.encoding.ser.EnumSerializerFactory.class;
            java.lang.Class enumdf = org.apache.axis.encoding.ser.EnumDeserializerFactory.class;
            java.lang.Class arraysf = org.apache.axis.encoding.ser.ArraySerializerFactory.class;
            java.lang.Class arraydf = org.apache.axis.encoding.ser.ArrayDeserializerFactory.class;
            java.lang.Class simplesf = org.apache.axis.encoding.ser.SimpleSerializerFactory.class;
            java.lang.Class simpledf = org.apache.axis.encoding.ser.SimpleDeserializerFactory.class;
            java.lang.Class simplelistsf = org.apache.axis.encoding.ser.SimpleListSerializerFactory.class;
            java.lang.Class simplelistdf = org.apache.axis.encoding.ser.SimpleListDeserializerFactory.class;
            qName = new javax.xml.namespace.QName("urn:exist", "Base64BinaryArray");
            cachedSerQNames.add(qName);
            cls = org.exist.soap.Base64BinaryArray.class;
            cachedSerClasses.add(cls);
            cachedSerFactories.add(beansf);
            cachedDeserFactories.add(beandf);

            qName = new javax.xml.namespace.QName("urn:exist", "Collection");
            cachedSerQNames.add(qName);
            cls = org.exist.soap.Collection.class;
            cachedSerClasses.add(cls);
            cachedSerFactories.add(beansf);
            cachedDeserFactories.add(beandf);

            qName = new javax.xml.namespace.QName("urn:exist", "QueryResponse");
            cachedSerQNames.add(qName);
            cls = org.exist.soap.QueryResponse.class;
            cachedSerClasses.add(cls);
            cachedSerFactories.add(beansf);
            cachedDeserFactories.add(beandf);

            qName = new javax.xml.namespace.QName("urn:exist", "QueryResponseCollection");
            cachedSerQNames.add(qName);
            cls = org.exist.soap.QueryResponseCollection.class;
            cachedSerClasses.add(cls);
            cachedSerFactories.add(beansf);
            cachedDeserFactories.add(beandf);

            qName = new javax.xml.namespace.QName("urn:exist", "QueryResponseCollections");
            cachedSerQNames.add(qName);
            cls = org.exist.soap.QueryResponseCollections.class;
            cachedSerClasses.add(cls);
            cachedSerFactories.add(beansf);
            cachedDeserFactories.add(beandf);

            qName = new javax.xml.namespace.QName("urn:exist", "QueryResponseDocument");
            cachedSerQNames.add(qName);
            cls = org.exist.soap.QueryResponseDocument.class;
            cachedSerClasses.add(cls);
            cachedSerFactories.add(beansf);
            cachedDeserFactories.add(beandf);

            qName = new javax.xml.namespace.QName("urn:exist", "QueryResponseDocuments");
            cachedSerQNames.add(qName);
            cls = org.exist.soap.QueryResponseDocuments.class;
            cachedSerClasses.add(cls);
            cachedSerFactories.add(beansf);
            cachedDeserFactories.add(beandf);

            qName = new javax.xml.namespace.QName("urn:exist", "StringArray");
            cachedSerQNames.add(qName);
            cls = org.exist.soap.StringArray.class;
            cachedSerClasses.add(cls);
            cachedSerFactories.add(beansf);
            cachedDeserFactories.add(beandf);

    }

    protected org.apache.axis.client.Call createCall() throws java.rmi.RemoteException {
        try {
            org.apache.axis.client.Call _call = super._createCall();
            if (super.maintainSessionSet) {
                _call.setMaintainSession(super.maintainSession);
            }
            if (super.cachedUsername != null) {
                _call.setUsername(super.cachedUsername);
            }
            if (super.cachedPassword != null) {
                _call.setPassword(super.cachedPassword);
            }
            if (super.cachedEndpoint != null) {
                _call.setTargetEndpointAddress(super.cachedEndpoint);
            }
            if (super.cachedTimeout != null) {
                _call.setTimeout(super.cachedTimeout);
            }
            if (super.cachedPortName != null) {
                _call.setPortName(super.cachedPortName);
            }
            java.util.Enumeration keys = super.cachedProperties.keys();
            while (keys.hasMoreElements()) {
                java.lang.String key = (java.lang.String) keys.nextElement();
                _call.setProperty(key, super.cachedProperties.get(key));
            }
            // All the type mapping information is registered
            // when the first call is made.
            // The type mapping information is actually registered in
            // the TypeMappingRegistry of the service, which
            // is the reason why registration is only needed for the first call.
            synchronized (this) {
                if (firstCall()) {
                    // must set encoding style before registering serializers
                    _call.setEncodingStyle(null);
                    for (int i = 0; i < cachedSerFactories.size(); ++i) {
                        java.lang.Class cls = (java.lang.Class) cachedSerClasses.get(i);
                        javax.xml.namespace.QName qName =
                                (javax.xml.namespace.QName) cachedSerQNames.get(i);
                        java.lang.Object x = cachedSerFactories.get(i);
                        if (x instanceof Class) {
                            java.lang.Class sf = (java.lang.Class)
                                 cachedSerFactories.get(i);
                            java.lang.Class df = (java.lang.Class)
                                 cachedDeserFactories.get(i);
                            _call.registerTypeMapping(cls, qName, sf, df, false);
                        }
                        else if (x instanceof javax.xml.rpc.encoding.SerializerFactory) {
                            org.apache.axis.encoding.SerializerFactory sf = (org.apache.axis.encoding.SerializerFactory)
                                 cachedSerFactories.get(i);
                            org.apache.axis.encoding.DeserializerFactory df = (org.apache.axis.encoding.DeserializerFactory)
                                 cachedDeserFactories.get(i);
                            _call.registerTypeMapping(cls, qName, sf, df, false);
                        }
                    }
                }
            }
            return _call;
        }
        catch (java.lang.Throwable _t) {
            throw new org.apache.axis.AxisFault("Failure trying to get the Call object", _t);
        }
    }

    public java.lang.String getResource(java.lang.String sessionId, java.lang.String path, boolean indent, boolean xinclude) throws java.rmi.RemoteException {
        if (super.cachedEndpoint == null) {
            throw new org.apache.axis.NoEndPointException();
        }
        org.apache.axis.client.Call _call = createCall();
        _call.setOperation(_operations[0]);
        _call.setUseSOAPAction(true);
        _call.setSOAPActionURI("");
        _call.setEncodingStyle(null);
        _call.setProperty(org.apache.axis.client.Call.SEND_TYPE_ATTR, Boolean.FALSE);
        _call.setProperty(org.apache.axis.AxisEngine.PROP_DOMULTIREFS, Boolean.FALSE);
        _call.setSOAPVersion(org.apache.axis.soap.SOAPConstants.SOAP11_CONSTANTS);
        _call.setOperationName(new javax.xml.namespace.QName("urn:exist", "getResource"));

        setRequestHeaders(_call);
        setAttachments(_call);
 try {        java.lang.Object _resp = _call.invoke(new java.lang.Object[] {sessionId, path, new java.lang.Boolean(indent), new java.lang.Boolean(xinclude)});

        if (_resp instanceof java.rmi.RemoteException) {
            throw (java.rmi.RemoteException)_resp;
        }
        else {
            extractAttachments(_call);
            try {
                return (java.lang.String) _resp;
            } catch (java.lang.Exception _exception) {
                return (java.lang.String) org.apache.axis.utils.JavaUtils.convert(_resp, java.lang.String.class);
            }
        }
  } catch (org.apache.axis.AxisFault axisFaultException) {
  throw axisFaultException;
}
    }

    public org.exist.soap.QueryResponse query(java.lang.String sessionId, java.lang.String xpath) throws java.rmi.RemoteException {
        if (super.cachedEndpoint == null) {
            throw new org.apache.axis.NoEndPointException();
        }
        org.apache.axis.client.Call _call = createCall();
        _call.setOperation(_operations[1]);
        _call.setUseSOAPAction(true);
        _call.setSOAPActionURI("");
        _call.setEncodingStyle(null);
        _call.setProperty(org.apache.axis.client.Call.SEND_TYPE_ATTR, Boolean.FALSE);
        _call.setProperty(org.apache.axis.AxisEngine.PROP_DOMULTIREFS, Boolean.FALSE);
        _call.setSOAPVersion(org.apache.axis.soap.SOAPConstants.SOAP11_CONSTANTS);
        _call.setOperationName(new javax.xml.namespace.QName("urn:exist", "query"));

        setRequestHeaders(_call);
        setAttachments(_call);
 try {        java.lang.Object _resp = _call.invoke(new java.lang.Object[] {sessionId, xpath});

        if (_resp instanceof java.rmi.RemoteException) {
            throw (java.rmi.RemoteException)_resp;
        }
        else {
            extractAttachments(_call);
            try {
                return (org.exist.soap.QueryResponse) _resp;
            } catch (java.lang.Exception _exception) {
                return (org.exist.soap.QueryResponse) org.apache.axis.utils.JavaUtils.convert(_resp, org.exist.soap.QueryResponse.class);
            }
        }
  } catch (org.apache.axis.AxisFault axisFaultException) {
  throw axisFaultException;
}
    }

    public java.lang.String connect(java.lang.String userId, java.lang.String password) throws java.rmi.RemoteException {
        if (super.cachedEndpoint == null) {
            throw new org.apache.axis.NoEndPointException();
        }
        org.apache.axis.client.Call _call = createCall();
        _call.setOperation(_operations[2]);
        _call.setUseSOAPAction(true);
        _call.setSOAPActionURI("");
        _call.setEncodingStyle(null);
        _call.setProperty(org.apache.axis.client.Call.SEND_TYPE_ATTR, Boolean.FALSE);
        _call.setProperty(org.apache.axis.AxisEngine.PROP_DOMULTIREFS, Boolean.FALSE);
        _call.setSOAPVersion(org.apache.axis.soap.SOAPConstants.SOAP11_CONSTANTS);
        _call.setOperationName(new javax.xml.namespace.QName("urn:exist", "connect"));

        setRequestHeaders(_call);
        setAttachments(_call);
 try {        java.lang.Object _resp = _call.invoke(new java.lang.Object[] {userId, password});

        if (_resp instanceof java.rmi.RemoteException) {
            throw (java.rmi.RemoteException)_resp;
        }
        else {
            extractAttachments(_call);
            try {
                return (java.lang.String) _resp;
            } catch (java.lang.Exception _exception) {
                return (java.lang.String) org.apache.axis.utils.JavaUtils.convert(_resp, java.lang.String.class);
            }
        }
  } catch (org.apache.axis.AxisFault axisFaultException) {
  throw axisFaultException;
}
    }

    public java.lang.String[] retrieve(java.lang.String sessionId, int start, int howmany, boolean indent, boolean xinclude, java.lang.String highlight) throws java.rmi.RemoteException {
        if (super.cachedEndpoint == null) {
            throw new org.apache.axis.NoEndPointException();
        }
        org.apache.axis.client.Call _call = createCall();
        _call.setOperation(_operations[3]);
        _call.setUseSOAPAction(true);
        _call.setSOAPActionURI("");
        _call.setEncodingStyle(null);
        _call.setProperty(org.apache.axis.client.Call.SEND_TYPE_ATTR, Boolean.FALSE);
        _call.setProperty(org.apache.axis.AxisEngine.PROP_DOMULTIREFS, Boolean.FALSE);
        _call.setSOAPVersion(org.apache.axis.soap.SOAPConstants.SOAP11_CONSTANTS);
        _call.setOperationName(new javax.xml.namespace.QName("urn:exist", "retrieve"));

        setRequestHeaders(_call);
        setAttachments(_call);
 try {        java.lang.Object _resp = _call.invoke(new java.lang.Object[] {sessionId, new java.lang.Integer(start), new java.lang.Integer(howmany), new java.lang.Boolean(indent), new java.lang.Boolean(xinclude), highlight});

        if (_resp instanceof java.rmi.RemoteException) {
            throw (java.rmi.RemoteException)_resp;
        }
        else {
            extractAttachments(_call);
            try {
                return (java.lang.String[]) _resp;
            } catch (java.lang.Exception _exception) {
                return (java.lang.String[]) org.apache.axis.utils.JavaUtils.convert(_resp, java.lang.String[].class);
            }
        }
  } catch (org.apache.axis.AxisFault axisFaultException) {
  throw axisFaultException;
}
    }

    public void disconnect(java.lang.String sessionId) throws java.rmi.RemoteException {
        if (super.cachedEndpoint == null) {
            throw new org.apache.axis.NoEndPointException();
        }
        org.apache.axis.client.Call _call = createCall();
        _call.setOperation(_operations[4]);
        _call.setUseSOAPAction(true);
        _call.setSOAPActionURI("");
        _call.setEncodingStyle(null);
        _call.setProperty(org.apache.axis.client.Call.SEND_TYPE_ATTR, Boolean.FALSE);
        _call.setProperty(org.apache.axis.AxisEngine.PROP_DOMULTIREFS, Boolean.FALSE);
        _call.setSOAPVersion(org.apache.axis.soap.SOAPConstants.SOAP11_CONSTANTS);
        _call.setOperationName(new javax.xml.namespace.QName("urn:exist", "disconnect"));

        setRequestHeaders(_call);
        setAttachments(_call);
 try {        java.lang.Object _resp = _call.invoke(new java.lang.Object[] {sessionId});

        if (_resp instanceof java.rmi.RemoteException) {
            throw (java.rmi.RemoteException)_resp;
        }
        extractAttachments(_call);
  } catch (org.apache.axis.AxisFault axisFaultException) {
  throw axisFaultException;
}
    }

    public byte[] getResourceData(java.lang.String sessionId, java.lang.String path, boolean indent, boolean xinclude, boolean processXSLPI) throws java.rmi.RemoteException {
        if (super.cachedEndpoint == null) {
            throw new org.apache.axis.NoEndPointException();
        }
        org.apache.axis.client.Call _call = createCall();
        _call.setOperation(_operations[5]);
        _call.setUseSOAPAction(true);
        _call.setSOAPActionURI("");
        _call.setEncodingStyle(null);
        _call.setProperty(org.apache.axis.client.Call.SEND_TYPE_ATTR, Boolean.FALSE);
        _call.setProperty(org.apache.axis.AxisEngine.PROP_DOMULTIREFS, Boolean.FALSE);
        _call.setSOAPVersion(org.apache.axis.soap.SOAPConstants.SOAP11_CONSTANTS);
        _call.setOperationName(new javax.xml.namespace.QName("urn:exist", "getResourceData"));

        setRequestHeaders(_call);
        setAttachments(_call);
 try {        java.lang.Object _resp = _call.invoke(new java.lang.Object[] {sessionId, path, new java.lang.Boolean(indent), new java.lang.Boolean(xinclude), new java.lang.Boolean(processXSLPI)});

        if (_resp instanceof java.rmi.RemoteException) {
            throw (java.rmi.RemoteException)_resp;
        }
        else {
            extractAttachments(_call);
            try {
                return (byte[]) _resp;
            } catch (java.lang.Exception _exception) {
                return (byte[]) org.apache.axis.utils.JavaUtils.convert(_resp, byte[].class);
            }
        }
  } catch (org.apache.axis.AxisFault axisFaultException) {
  throw axisFaultException;
}
    }

    public org.exist.soap.QueryResponse xquery(java.lang.String sessionId, byte[] xquery) throws java.rmi.RemoteException {
        if (super.cachedEndpoint == null) {
            throw new org.apache.axis.NoEndPointException();
        }
        org.apache.axis.client.Call _call = createCall();
        _call.setOperation(_operations[6]);
        _call.setUseSOAPAction(true);
        _call.setSOAPActionURI("");
        _call.setEncodingStyle(null);
        _call.setProperty(org.apache.axis.client.Call.SEND_TYPE_ATTR, Boolean.FALSE);
        _call.setProperty(org.apache.axis.AxisEngine.PROP_DOMULTIREFS, Boolean.FALSE);
        _call.setSOAPVersion(org.apache.axis.soap.SOAPConstants.SOAP11_CONSTANTS);
        _call.setOperationName(new javax.xml.namespace.QName("urn:exist", "xquery"));

        setRequestHeaders(_call);
        setAttachments(_call);
 try {        java.lang.Object _resp = _call.invoke(new java.lang.Object[] {sessionId, xquery});

        if (_resp instanceof java.rmi.RemoteException) {
            throw (java.rmi.RemoteException)_resp;
        }
        else {
            extractAttachments(_call);
            try {
                return (org.exist.soap.QueryResponse) _resp;
            } catch (java.lang.Exception _exception) {
                return (org.exist.soap.QueryResponse) org.apache.axis.utils.JavaUtils.convert(_resp, org.exist.soap.QueryResponse.class);
            }
        }
  } catch (org.apache.axis.AxisFault axisFaultException) {
  throw axisFaultException;
}
    }

    public org.exist.soap.Base64BinaryArray retrieveData(java.lang.String sessionId, int start, int howmany, boolean indent, boolean xinclude, java.lang.String highlight) throws java.rmi.RemoteException {
        if (super.cachedEndpoint == null) {
            throw new org.apache.axis.NoEndPointException();
        }
        org.apache.axis.client.Call _call = createCall();
        _call.setOperation(_operations[7]);
        _call.setUseSOAPAction(true);
        _call.setSOAPActionURI("");
        _call.setEncodingStyle(null);
        _call.setProperty(org.apache.axis.client.Call.SEND_TYPE_ATTR, Boolean.FALSE);
        _call.setProperty(org.apache.axis.AxisEngine.PROP_DOMULTIREFS, Boolean.FALSE);
        _call.setSOAPVersion(org.apache.axis.soap.SOAPConstants.SOAP11_CONSTANTS);
        _call.setOperationName(new javax.xml.namespace.QName("urn:exist", "retrieveData"));

        setRequestHeaders(_call);
        setAttachments(_call);
 try {        java.lang.Object _resp = _call.invoke(new java.lang.Object[] {sessionId, new java.lang.Integer(start), new java.lang.Integer(howmany), new java.lang.Boolean(indent), new java.lang.Boolean(xinclude), highlight});

        if (_resp instanceof java.rmi.RemoteException) {
            throw (java.rmi.RemoteException)_resp;
        }
        else {
            extractAttachments(_call);
            try {
                return (org.exist.soap.Base64BinaryArray) _resp;
            } catch (java.lang.Exception _exception) {
                return (org.exist.soap.Base64BinaryArray) org.apache.axis.utils.JavaUtils.convert(_resp, org.exist.soap.Base64BinaryArray.class);
            }
        }
  } catch (org.apache.axis.AxisFault axisFaultException) {
  throw axisFaultException;
}
    }

    public java.lang.String[] retrieveByDocument(java.lang.String sessionId, int start, int howmany, java.lang.String path, boolean indent, boolean xinclude, java.lang.String highlight) throws java.rmi.RemoteException {
        if (super.cachedEndpoint == null) {
            throw new org.apache.axis.NoEndPointException();
        }
        org.apache.axis.client.Call _call = createCall();
        _call.setOperation(_operations[8]);
        _call.setUseSOAPAction(true);
        _call.setSOAPActionURI("");
        _call.setEncodingStyle(null);
        _call.setProperty(org.apache.axis.client.Call.SEND_TYPE_ATTR, Boolean.FALSE);
        _call.setProperty(org.apache.axis.AxisEngine.PROP_DOMULTIREFS, Boolean.FALSE);
        _call.setSOAPVersion(org.apache.axis.soap.SOAPConstants.SOAP11_CONSTANTS);
        _call.setOperationName(new javax.xml.namespace.QName("urn:exist", "retrieveByDocument"));

        setRequestHeaders(_call);
        setAttachments(_call);
 try {        java.lang.Object _resp = _call.invoke(new java.lang.Object[] {sessionId, new java.lang.Integer(start), new java.lang.Integer(howmany), path, new java.lang.Boolean(indent), new java.lang.Boolean(xinclude), highlight});

        if (_resp instanceof java.rmi.RemoteException) {
            throw (java.rmi.RemoteException)_resp;
        }
        else {
            extractAttachments(_call);
            try {
                return (java.lang.String[]) _resp;
            } catch (java.lang.Exception _exception) {
                return (java.lang.String[]) org.apache.axis.utils.JavaUtils.convert(_resp, java.lang.String[].class);
            }
        }
  } catch (org.apache.axis.AxisFault axisFaultException) {
  throw axisFaultException;
}
    }

    public org.exist.soap.Collection listCollection(java.lang.String sessionId, java.lang.String path) throws java.rmi.RemoteException {
        if (super.cachedEndpoint == null) {
            throw new org.apache.axis.NoEndPointException();
        }
        org.apache.axis.client.Call _call = createCall();
        _call.setOperation(_operations[9]);
        _call.setUseSOAPAction(true);
        _call.setSOAPActionURI("");
        _call.setEncodingStyle(null);
        _call.setProperty(org.apache.axis.client.Call.SEND_TYPE_ATTR, Boolean.FALSE);
        _call.setProperty(org.apache.axis.AxisEngine.PROP_DOMULTIREFS, Boolean.FALSE);
        _call.setSOAPVersion(org.apache.axis.soap.SOAPConstants.SOAP11_CONSTANTS);
        _call.setOperationName(new javax.xml.namespace.QName("urn:exist", "listCollection"));

        setRequestHeaders(_call);
        setAttachments(_call);
 try {        java.lang.Object _resp = _call.invoke(new java.lang.Object[] {sessionId, path});

        if (_resp instanceof java.rmi.RemoteException) {
            throw (java.rmi.RemoteException)_resp;
        }
        else {
            extractAttachments(_call);
            try {
                return (org.exist.soap.Collection) _resp;
            } catch (java.lang.Exception _exception) {
                return (org.exist.soap.Collection) org.apache.axis.utils.JavaUtils.convert(_resp, org.exist.soap.Collection.class);
            }
        }
  } catch (org.apache.axis.AxisFault axisFaultException) {
  throw axisFaultException;
}
    }

}
