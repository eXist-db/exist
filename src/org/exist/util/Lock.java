package org.exist.util;

public interface Lock {

	public final static int READ_LOCK = 0;
	public final static int WRITE_LOCK = 1;
	
    public boolean acquire( ) throws LockException;
	public boolean acquire( int mode ) throws LockException;
    public void release( );
}
