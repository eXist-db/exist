/**
 * Query.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis WSDL2Java emitter.
 */

package org.exist.soap;

public interface Query extends java.rmi.Remote {
    public byte[] getResource(java.lang.String in0, java.lang.String in1, boolean in2) throws java.rmi.RemoteException;
    public org.exist.soap.QueryResponse query(java.lang.String in0) throws java.rmi.RemoteException;
    public byte[] retrieve(int in0, int in1, java.lang.String in2, boolean in3) throws java.rmi.RemoteException;
    public byte[] retrieveByDocument(int in0, int in1, java.lang.String in2, java.lang.String in3, boolean in4) throws java.rmi.RemoteException;
    public org.exist.soap.Collection listCollection(java.lang.String in0) throws java.rmi.RemoteException;
}
