package org.exist.util;

import java.io.OutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

public class VariableByteOutputStream extends OutputStream {

    protected ByteArray buf;

    public VariableByteOutputStream() {
        super();
		buf = new FastByteBuffer( 9 );
    }

	public VariableByteOutputStream(int size) {
		super();
		buf = new FastByteBuffer(size);
	}
	
    public void clear() {
        buf.setLength( 0 );
    }

    public void close() throws IOException {
    	buf = null;
    }

    public void flush() throws IOException {
    }

    public byte[] toByteArray() {
        byte[] b = new byte[buf.size()];
        buf.copyTo( b, 0 );
        return b;
    }

	public ByteArray data() {
		return buf;
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
    
    public void writeFixedLong( long l ) {
    	VariableByteCoding.encodeFixed( buf, l );
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
}

