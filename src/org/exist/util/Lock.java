package org.exist.util;

/**
 * @author wolf
 *
 * To change this generated comment edit the template variable "typecomment":
 * Window>Preferences>Java>Templates.
 * To enable and disable the creation of type comments go to
 * Window>Preferences>Java>Code Generation.
 */
public interface Lock {

	public final static int READ_LOCK = 0;
	public final static int WRITE_LOCK = 1;
	
    public boolean hasKey( Object key );
    public void acquire( Object key ) throws LockException;
	public void acquire( Object key, int mode ) throws LockException;
    public void enter( Object key ) throws LockException;
    public void release( Object key );
}
