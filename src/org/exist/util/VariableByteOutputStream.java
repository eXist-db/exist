package org.exist.util;

import java.io.OutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

public class VariableByteOutputStream extends OutputStream {

    private FastByteBuffer buf = new FastByteBuffer( 6, 10, 3 );

    public VariableByteOutputStream() {
        super();
    }

    public void clear() {
        buf.setLength( 0 );
    }

    public void close() throws IOException {
    }

    public void copyTo( byte[] b ) {
        buf.copyTo( b, 0 );
    }

    public void copyTo( byte[] b, int off ) {
        buf.copyTo( b, off );
    }

    public void flush() throws IOException {
    }

    public byte[] toByteArray() {
        byte[] b = new byte[buf.size()];
        buf.copyTo( b, 0 );
        return b;
    }


    public void write( int b ) throws IOException {
        buf.append( (byte) b );
    }

    public void write( byte[] b ) throws IOException {
        buf.append( b );
    }

    public void write( byte[] b, int off, int len ) throws IOException {
        buf.append( b, off, len );
    }
    
    public void writeByte( byte b ) {
        buf.append( b );
    }
    
    public void writeShort( int s ) {
        VariableByteCoding.encode( buf, s );
    }
    
    public void writeInt( int i ) {
        VariableByteCoding.encode( buf, i );
    }

    public void writeLong( long l ) {
        VariableByteCoding.encode( buf, l );
    }
    
    public void writeUTF( String s ) throws IOException {
        byte[] data = null;
        try {
            data = s.getBytes( "UTF-8" );
        } catch( UnsupportedEncodingException e ) {
            data = s.getBytes();
        }
        writeInt( data.length );
        write( data, 0, data.length );
    }
    
    protected void finalize() {
    }
}

