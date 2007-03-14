/**
 * AdminSoapBindingSkeleton.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.2.1 Jun 14, 2005 (09:15:57 EDT) WSDL2Java emitter.
 */

package org.exist.soap;

import org.exist.Namespaces;

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
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "sessionId"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "data"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "base64Binary"), byte[].class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "encoding"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "path"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "replace"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "boolean"), boolean.class, false, false), 
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
        };
        _oper = new org.apache.axis.description.OperationDesc("removeCollection", _params, new javax.xml.namespace.QName("urn:exist", "removeCollectionReturn"));
        _oper.setReturnType(new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "boolean"));
        _oper.setElementQName(new javax.xml.namespace.QName("urn:exist", "removeCollection"));
        _oper.setSoapAction("");
        _myOperationsList.add(_oper);
        if (_myOperations.get("removeCollection") == null) {
            _myOperations.put("removeCollection", new java.util.ArrayList());
        }
        ((java.util.List)_myOperations.get("removeCollection")).add(_oper);
        _params = new org.apache.axis.description.ParameterDesc [] {
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "sessionId"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "path"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
        };
        _oper = new org.apache.axis.description.OperationDesc("removeDocument", _params, new javax.xml.namespace.QName("urn:exist", "removeDocumentReturn"));
        _oper.setReturnType(new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "boolean"));
        _oper.setElementQName(new javax.xml.namespace.QName("urn:exist", "removeDocument"));
        _oper.setSoapAction("");
        _myOperationsList.add(_oper);
        if (_myOperations.get("removeDocument") == null) {
            _myOperations.put("removeDocument", new java.util.ArrayList());
        }
        ((java.util.List)_myOperations.get("removeDocument")).add(_oper);
        _params = new org.apache.axis.description.ParameterDesc [] {
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "sessionId"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "path"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
        };
        _oper = new org.apache.axis.description.OperationDesc("createCollection", _params, new javax.xml.namespace.QName("urn:exist", "createCollectionReturn"));
        _oper.setReturnType(new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "boolean"));
        _oper.setElementQName(new javax.xml.namespace.QName("urn:exist", "createCollection"));
        _oper.setSoapAction("");
        _myOperationsList.add(_oper);
        if (_myOperations.get("createCollection") == null) {
            _myOperations.put("createCollection", new java.util.ArrayList());
        }
        ((java.util.List)_myOperations.get("createCollection")).add(_oper);
        _params = new org.apache.axis.description.ParameterDesc [] {
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "sessionId"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "collectionName"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "xupdate"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
        };
        _oper = new org.apache.axis.description.OperationDesc("xupdate", _params, new javax.xml.namespace.QName("urn:exist", "xupdateReturn"));
        _oper.setReturnType(new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "int"));
        _oper.setElementQName(new javax.xml.namespace.QName("urn:exist", "xupdate"));
        _oper.setSoapAction("");
        _myOperationsList.add(_oper);
        if (_myOperations.get("xupdate") == null) {
            _myOperations.put("xupdate", new java.util.ArrayList());
        }
        ((java.util.List)_myOperations.get("xupdate")).add(_oper);
        _params = new org.apache.axis.description.ParameterDesc [] {
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "sessionId"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "documentName"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "xupdate"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
        };
        _oper = new org.apache.axis.description.OperationDesc("xupdateResource", _params, new javax.xml.namespace.QName("urn:exist", "xupdateResourceReturn"));
        _oper.setReturnType(new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "int"));
        _oper.setElementQName(new javax.xml.namespace.QName("urn:exist", "xupdateResource"));
        _oper.setSoapAction("");
        _myOperationsList.add(_oper);
        if (_myOperations.get("xupdateResource") == null) {
            _myOperations.put("xupdateResource", new java.util.ArrayList());
        }
        ((java.util.List)_myOperations.get("xupdateResource")).add(_oper);
        _params = new org.apache.axis.description.ParameterDesc [] {
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "sessionId"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "name"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
        };
        _oper = new org.apache.axis.description.OperationDesc("getBinaryResource", _params, new javax.xml.namespace.QName("urn:exist", "getBinaryResourceReturn"));
        _oper.setReturnType(new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "base64Binary"));
        _oper.setElementQName(new javax.xml.namespace.QName("urn:exist", "getBinaryResource"));
        _oper.setSoapAction("urn:exist/getBinaryResource");
        _myOperationsList.add(_oper);
        if (_myOperations.get("getBinaryResource") == null) {
            _myOperations.put("getBinaryResource", new java.util.ArrayList());
        }
        ((java.util.List)_myOperations.get("getBinaryResource")).add(_oper);
        _params = new org.apache.axis.description.ParameterDesc [] {
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "sessionId"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "collectionName"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
        };
        _oper = new org.apache.axis.description.OperationDesc("getCollectionDesc", _params, new javax.xml.namespace.QName("urn:exist", "getCollectionDescReturn"));
        _oper.setReturnType(new javax.xml.namespace.QName("urn:exist", "CollectionDesc"));
        _oper.setElementQName(new javax.xml.namespace.QName("urn:exist", "getCollectionDesc"));
        _oper.setSoapAction("urn:exist/getCollectionDesc");
        _myOperationsList.add(_oper);
        if (_myOperations.get("getCollectionDesc") == null) {
            _myOperations.put("getCollectionDesc", new java.util.ArrayList());
        }
        ((java.util.List)_myOperations.get("getCollectionDesc")).add(_oper);
        _params = new org.apache.axis.description.ParameterDesc [] {
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "sessionId"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "resource"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "owner"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "ownerGroup"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "permissions"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "int"), int.class, false, false), 
        };
        _oper = new org.apache.axis.description.OperationDesc("setPermissions", _params, null);
        _oper.setElementQName(new javax.xml.namespace.QName("urn:exist", "setPermissions"));
        _oper.setSoapAction("urn:exist/setPermissions");
        _myOperationsList.add(_oper);
        if (_myOperations.get("setPermissions") == null) {
            _myOperations.put("setPermissions", new java.util.ArrayList());
        }
        ((java.util.List)_myOperations.get("setPermissions")).add(_oper);
        _params = new org.apache.axis.description.ParameterDesc [] {
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "sessionId"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "docPath"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "destinationPath"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "newName"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
        };
        _oper = new org.apache.axis.description.OperationDesc("copyResource", _params, null);
        _oper.setElementQName(new javax.xml.namespace.QName("urn:exist", "copyResource"));
        _oper.setSoapAction("urn:exist/copyResource");
        _myOperationsList.add(_oper);
        if (_myOperations.get("copyResource") == null) {
            _myOperations.put("copyResource", new java.util.ArrayList());
        }
        ((java.util.List)_myOperations.get("copyResource")).add(_oper);
        _params = new org.apache.axis.description.ParameterDesc [] {
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "sessionId"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "collectionPath"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "destinationPath"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "newName"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
        };
        _oper = new org.apache.axis.description.OperationDesc("copyCollection", _params, null);
        _oper.setElementQName(new javax.xml.namespace.QName("urn:exist", "copyCollection"));
        _oper.setSoapAction("urn:exist/copyCollection");
        _myOperationsList.add(_oper);
        if (_myOperations.get("copyCollection") == null) {
            _myOperations.put("copyCollection", new java.util.ArrayList());
        }
        ((java.util.List)_myOperations.get("copyCollection")).add(_oper);
        _params = new org.apache.axis.description.ParameterDesc [] {
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "sessionId"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "name"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "password"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "groups"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName("urn:exist", "Strings"), org.exist.soap.Strings.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "home"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
        };
        _oper = new org.apache.axis.description.OperationDesc("setUser", _params, null);
        _oper.setElementQName(new javax.xml.namespace.QName("urn:exist", "setUser"));
        _oper.setSoapAction("urn:exist/setUser");
        _myOperationsList.add(_oper);
        if (_myOperations.get("setUser") == null) {
            _myOperations.put("setUser", new java.util.ArrayList());
        }
        ((java.util.List)_myOperations.get("setUser")).add(_oper);
        _params = new org.apache.axis.description.ParameterDesc [] {
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "sessionId"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "user"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
        };
        _oper = new org.apache.axis.description.OperationDesc("getUser", _params, new javax.xml.namespace.QName("urn:exist", "getUserReturn"));
        _oper.setReturnType(new javax.xml.namespace.QName("urn:exist", "UserDesc"));
        _oper.setElementQName(new javax.xml.namespace.QName("urn:exist", "getUser"));
        _oper.setSoapAction("urn:exist/getUser");
        _myOperationsList.add(_oper);
        if (_myOperations.get("getUser") == null) {
            _myOperations.put("getUser", new java.util.ArrayList());
        }
        ((java.util.List)_myOperations.get("getUser")).add(_oper);
        _params = new org.apache.axis.description.ParameterDesc [] {
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "sessionId"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "name"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
        };
        _oper = new org.apache.axis.description.OperationDesc("removeUser", _params, null);
        _oper.setElementQName(new javax.xml.namespace.QName("urn:exist", "removeUser"));
        _oper.setSoapAction("urn:exist/removeUser");
        _myOperationsList.add(_oper);
        if (_myOperations.get("removeUser") == null) {
            _myOperations.put("removeUser", new java.util.ArrayList());
        }
        ((java.util.List)_myOperations.get("removeUser")).add(_oper);
        _params = new org.apache.axis.description.ParameterDesc [] {
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "sessionId"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
        };
        _oper = new org.apache.axis.description.OperationDesc("getUsers", _params, new javax.xml.namespace.QName("urn:exist", "getUsersReturn"));
        _oper.setReturnType(new javax.xml.namespace.QName("urn:exist", "UserDescs"));
        _oper.setElementQName(new javax.xml.namespace.QName("urn:exist", "getUsers"));
        _oper.setSoapAction("urn:exist/getUsers");
        _myOperationsList.add(_oper);
        if (_myOperations.get("getUsers") == null) {
            _myOperations.put("getUsers", new java.util.ArrayList());
        }
        ((java.util.List)_myOperations.get("getUsers")).add(_oper);
        _params = new org.apache.axis.description.ParameterDesc [] {
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "sessionId"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
        };
        _oper = new org.apache.axis.description.OperationDesc("getGroups", _params, new javax.xml.namespace.QName("urn:exist", "getGroupsReturn"));
        _oper.setReturnType(new javax.xml.namespace.QName("urn:exist", "Strings"));
        _oper.setElementQName(new javax.xml.namespace.QName("urn:exist", "getGroups"));
        _oper.setSoapAction("urn:exist/getGroups");
        _myOperationsList.add(_oper);
        if (_myOperations.get("getGroups") == null) {
            _myOperations.put("getGroups", new java.util.ArrayList());
        }
        ((java.util.List)_myOperations.get("getGroups")).add(_oper);
        _params = new org.apache.axis.description.ParameterDesc [] {
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "sessionId"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "collectionPath"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "destinationPath"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "newName"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
        };
        _oper = new org.apache.axis.description.OperationDesc("moveCollection", _params, null);
        _oper.setElementQName(new javax.xml.namespace.QName("urn:exist", "moveCollection"));
        _oper.setSoapAction("urn:exist/moveCollection");
        _myOperationsList.add(_oper);
        if (_myOperations.get("moveCollection") == null) {
            _myOperations.put("moveCollection", new java.util.ArrayList());
        }
        ((java.util.List)_myOperations.get("moveCollection")).add(_oper);
        _params = new org.apache.axis.description.ParameterDesc [] {
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "sessionId"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "docPath"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "destinationPath"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "newName"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
        };
        _oper = new org.apache.axis.description.OperationDesc("moveResource", _params, null);
        _oper.setElementQName(new javax.xml.namespace.QName("urn:exist", "moveResource"));
        _oper.setSoapAction("urn:exist/moveResource");
        _myOperationsList.add(_oper);
        if (_myOperations.get("moveResource") == null) {
            _myOperations.put("moveResource", new java.util.ArrayList());
        }
        ((java.util.List)_myOperations.get("moveResource")).add(_oper);
        _params = new org.apache.axis.description.ParameterDesc [] {
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "sessionId"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "path"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "userName"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
        };
        _oper = new org.apache.axis.description.OperationDesc("lockResource", _params, null);
        _oper.setElementQName(new javax.xml.namespace.QName("urn:exist", "lockResource"));
        _oper.setSoapAction("urn:exist/lockResource");
        _myOperationsList.add(_oper);
        if (_myOperations.get("lockResource") == null) {
            _myOperations.put("lockResource", new java.util.ArrayList());
        }
        ((java.util.List)_myOperations.get("lockResource")).add(_oper);
        _params = new org.apache.axis.description.ParameterDesc [] {
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "sessionId"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "path"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
        };
        _oper = new org.apache.axis.description.OperationDesc("unlockResource", _params, null);
        _oper.setElementQName(new javax.xml.namespace.QName("urn:exist", "unlockResource"));
        _oper.setSoapAction("urn:exist/unlockResource");
        _myOperationsList.add(_oper);
        if (_myOperations.get("unlockResource") == null) {
            _myOperations.put("unlockResource", new java.util.ArrayList());
        }
        ((java.util.List)_myOperations.get("unlockResource")).add(_oper);
        _params = new org.apache.axis.description.ParameterDesc [] {
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "sessionId"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "path"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
        };
        _oper = new org.apache.axis.description.OperationDesc("hasUserLock", _params, new javax.xml.namespace.QName("urn:exist", "hasUserLockReturn"));
        _oper.setReturnType(new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"));
        _oper.setElementQName(new javax.xml.namespace.QName("urn:exist", "hasUserLock"));
        _oper.setSoapAction("urn:exist/hasUserLock");
        _myOperationsList.add(_oper);
        if (_myOperations.get("hasUserLock") == null) {
            _myOperations.put("hasUserLock", new java.util.ArrayList());
        }
        ((java.util.List)_myOperations.get("hasUserLock")).add(_oper);
        _params = new org.apache.axis.description.ParameterDesc [] {
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "sessionId"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "resource"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
        };
        _oper = new org.apache.axis.description.OperationDesc("getPermissions", _params, new javax.xml.namespace.QName("urn:exist", "getPermissionsReturn"));
        _oper.setReturnType(new javax.xml.namespace.QName("urn:exist", "Permissions"));
        _oper.setElementQName(new javax.xml.namespace.QName("urn:exist", "getPermissions"));
        _oper.setSoapAction("urn:exist/getPermissions");
        _myOperationsList.add(_oper);
        if (_myOperations.get("getPermissions") == null) {
            _myOperations.put("getPermissions", new java.util.ArrayList());
        }
        ((java.util.List)_myOperations.get("getPermissions")).add(_oper);
        _params = new org.apache.axis.description.ParameterDesc [] {
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "sessionId"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "name"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
        };
        _oper = new org.apache.axis.description.OperationDesc("listCollectionPermissions", _params, new javax.xml.namespace.QName("urn:exist", "listCollectionPermissionsReturn"));
        _oper.setReturnType(new javax.xml.namespace.QName("urn:exist", "EntityPermissionsList"));
        _oper.setElementQName(new javax.xml.namespace.QName("urn:exist", "listCollectionPermissions"));
        _oper.setSoapAction("urn:exist/listCollectionPermissions");
        _myOperationsList.add(_oper);
        if (_myOperations.get("listCollectionPermissions") == null) {
            _myOperations.put("listCollectionPermissions", new java.util.ArrayList());
        }
        ((java.util.List)_myOperations.get("listCollectionPermissions")).add(_oper);
        _params = new org.apache.axis.description.ParameterDesc [] {
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "sessionId"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "name"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
        };
        _oper = new org.apache.axis.description.OperationDesc("listDocumentPermissions", _params, new javax.xml.namespace.QName("urn:exist", "listDocumentPermissionsReturn"));
        _oper.setReturnType(new javax.xml.namespace.QName("urn:exist", "EntityPermissionsList"));
        _oper.setElementQName(new javax.xml.namespace.QName("urn:exist", "listDocumentPermissions"));
        _oper.setSoapAction("urn:exist/listDocumentPermissions");
        _myOperationsList.add(_oper);
        if (_myOperations.get("listDocumentPermissions") == null) {
            _myOperations.put("listDocumentPermissions", new java.util.ArrayList());
        }
        ((java.util.List)_myOperations.get("listDocumentPermissions")).add(_oper);
        _params = new org.apache.axis.description.ParameterDesc [] {
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "sessionId"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "collectionName"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "inclusive"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "boolean"), boolean.class, false, false), 
        };
        _oper = new org.apache.axis.description.OperationDesc("getIndexedElements", _params, new javax.xml.namespace.QName("urn:exist", "getIndexedElementsReturn"));
        _oper.setReturnType(new javax.xml.namespace.QName("urn:exist", "IndexedElements"));
        _oper.setElementQName(new javax.xml.namespace.QName("urn:exist", "getIndexedElements"));
        _oper.setSoapAction("urn:exist/getIndexedElements");
        _myOperationsList.add(_oper);
        if (_myOperations.get("getIndexedElements") == null) {
            _myOperations.put("getIndexedElements", new java.util.ArrayList());
        }
        ((java.util.List)_myOperations.get("getIndexedElements")).add(_oper);
        _params = new org.apache.axis.description.ParameterDesc [] {
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "sessionId"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "data"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "base64Binary"), byte[].class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "path"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "mimeType"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "string"), java.lang.String.class, false, false), 
            new org.apache.axis.description.ParameterDesc(new javax.xml.namespace.QName("urn:exist", "replace"), org.apache.axis.description.ParameterDesc.IN, new javax.xml.namespace.QName(Namespaces.SCHEMA_NS, "boolean"), boolean.class, false, false), 
        };
        _oper = new org.apache.axis.description.OperationDesc("storeBinary", _params, null);
        _oper.setElementQName(new javax.xml.namespace.QName("urn:exist", "storeBinary"));
        _oper.setSoapAction("urn:exist/storeBinary");
        _myOperationsList.add(_oper);
        if (_myOperations.get("storeBinary") == null) {
            _myOperations.put("storeBinary", new java.util.ArrayList());
        }
        ((java.util.List)_myOperations.get("storeBinary")).add(_oper);
    }

    public AdminSoapBindingSkeleton() {
        this.impl = new org.exist.soap.AdminSoapBindingImpl();
    }

    public AdminSoapBindingSkeleton(org.exist.soap.Admin impl) {
        this.impl = impl;
    }
    public void store(java.lang.String sessionId, byte[] data, java.lang.String encoding, java.lang.String path, boolean replace) throws java.rmi.RemoteException
    {
        impl.store(sessionId, data, encoding, path, replace);
    }

    public java.lang.String connect(java.lang.String userId, java.lang.String password) throws java.rmi.RemoteException
    {
        java.lang.String ret = impl.connect(userId, password);
        return ret;
    }

    public void disconnect(java.lang.String sessionId) throws java.rmi.RemoteException
    {
        impl.disconnect(sessionId);
    }

    public boolean removeCollection(java.lang.String sessionId, java.lang.String path) throws java.rmi.RemoteException
    {
        boolean ret = impl.removeCollection(sessionId, path);
        return ret;
    }

    public boolean removeDocument(java.lang.String sessionId, java.lang.String path) throws java.rmi.RemoteException
    {
        boolean ret = impl.removeDocument(sessionId, path);
        return ret;
    }

    public boolean createCollection(java.lang.String sessionId, java.lang.String path) throws java.rmi.RemoteException
    {
        boolean ret = impl.createCollection(sessionId, path);
        return ret;
    }

    public int xupdate(java.lang.String sessionId, java.lang.String collectionName, java.lang.String xupdate) throws java.rmi.RemoteException
    {
        int ret = impl.xupdate(sessionId, collectionName, xupdate);
        return ret;
    }

    public int xupdateResource(java.lang.String sessionId, java.lang.String documentName, java.lang.String xupdate) throws java.rmi.RemoteException
    {
        int ret = impl.xupdateResource(sessionId, documentName, xupdate);
        return ret;
    }

    public byte[] getBinaryResource(java.lang.String sessionId, java.lang.String name) throws java.rmi.RemoteException
    {
        byte[] ret = impl.getBinaryResource(sessionId, name);
        return ret;
    }

    public org.exist.soap.CollectionDesc getCollectionDesc(java.lang.String sessionId, java.lang.String collectionName) throws java.rmi.RemoteException
    {
        org.exist.soap.CollectionDesc ret = impl.getCollectionDesc(sessionId, collectionName);
        return ret;
    }

    public void setPermissions(java.lang.String sessionId, java.lang.String resource, java.lang.String owner, java.lang.String ownerGroup, int permissions) throws java.rmi.RemoteException
    {
        impl.setPermissions(sessionId, resource, owner, ownerGroup, permissions);
    }

    public void copyResource(java.lang.String sessionId, java.lang.String docPath, java.lang.String destinationPath, java.lang.String newName) throws java.rmi.RemoteException
    {
        impl.copyResource(sessionId, docPath, destinationPath, newName);
    }

    public void copyCollection(java.lang.String sessionId, java.lang.String collectionPath, java.lang.String destinationPath, java.lang.String newName) throws java.rmi.RemoteException
    {
        impl.copyCollection(sessionId, collectionPath, destinationPath, newName);
    }

    public void setUser(java.lang.String sessionId, java.lang.String name, java.lang.String password, org.exist.soap.Strings groups, java.lang.String home) throws java.rmi.RemoteException
    {
        impl.setUser(sessionId, name, password, groups, home);
    }

    public org.exist.soap.UserDesc getUser(java.lang.String sessionId, java.lang.String user) throws java.rmi.RemoteException
    {
        org.exist.soap.UserDesc ret = impl.getUser(sessionId, user);
        return ret;
    }

    public void removeUser(java.lang.String sessionId, java.lang.String name) throws java.rmi.RemoteException
    {
        impl.removeUser(sessionId, name);
    }

    public org.exist.soap.UserDescs getUsers(java.lang.String sessionId) throws java.rmi.RemoteException
    {
        org.exist.soap.UserDescs ret = impl.getUsers(sessionId);
        return ret;
    }

    public org.exist.soap.Strings getGroups(java.lang.String sessionId) throws java.rmi.RemoteException
    {
        org.exist.soap.Strings ret = impl.getGroups(sessionId);
        return ret;
    }

    public void moveCollection(java.lang.String sessionId, java.lang.String collectionPath, java.lang.String destinationPath, java.lang.String newName) throws java.rmi.RemoteException
    {
        impl.moveCollection(sessionId, collectionPath, destinationPath, newName);
    }

    public void moveResource(java.lang.String sessionId, java.lang.String docPath, java.lang.String destinationPath, java.lang.String newName) throws java.rmi.RemoteException
    {
        impl.moveResource(sessionId, docPath, destinationPath, newName);
    }

    public void lockResource(java.lang.String sessionId, java.lang.String path, java.lang.String userName) throws java.rmi.RemoteException
    {
        impl.lockResource(sessionId, path, userName);
    }

    public void unlockResource(java.lang.String sessionId, java.lang.String path) throws java.rmi.RemoteException
    {
        impl.unlockResource(sessionId, path);
    }

    public java.lang.String hasUserLock(java.lang.String sessionId, java.lang.String path) throws java.rmi.RemoteException
    {
        java.lang.String ret = impl.hasUserLock(sessionId, path);
        return ret;
    }

    public org.exist.soap.Permissions getPermissions(java.lang.String sessionId, java.lang.String resource) throws java.rmi.RemoteException
    {
        org.exist.soap.Permissions ret = impl.getPermissions(sessionId, resource);
        return ret;
    }

    public org.exist.soap.EntityPermissionsList listCollectionPermissions(java.lang.String sessionId, java.lang.String name) throws java.rmi.RemoteException
    {
        org.exist.soap.EntityPermissionsList ret = impl.listCollectionPermissions(sessionId, name);
        return ret;
    }

    public org.exist.soap.EntityPermissionsList listDocumentPermissions(java.lang.String sessionId, java.lang.String name) throws java.rmi.RemoteException
    {
        org.exist.soap.EntityPermissionsList ret = impl.listDocumentPermissions(sessionId, name);
        return ret;
    }

    public org.exist.soap.IndexedElements getIndexedElements(java.lang.String sessionId, java.lang.String collectionName, boolean inclusive) throws java.rmi.RemoteException
    {
        org.exist.soap.IndexedElements ret = impl.getIndexedElements(sessionId, collectionName, inclusive);
        return ret;
    }

    public void storeBinary(java.lang.String sessionId, byte[] data, java.lang.String path, java.lang.String mimeType, boolean replace) throws java.rmi.RemoteException
    {
        impl.storeBinary(sessionId, data, path, mimeType, replace);
    }

}
