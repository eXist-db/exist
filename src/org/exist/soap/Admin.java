/**
 * Admin.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis WSDL2Java emitter.
 */

package org.exist.soap;

public interface Admin extends java.rmi.Remote {
    public void store(byte[] in0, java.lang.String in1, java.lang.String in2, boolean in3) throws java.rmi.RemoteException;
    public boolean removeCollection(java.lang.String in0) throws java.rmi.RemoteException;
    public boolean removeDocument(java.lang.String in0) throws java.rmi.RemoteException;
    public boolean createCollection(java.lang.String in0) throws java.rmi.RemoteException;
}
