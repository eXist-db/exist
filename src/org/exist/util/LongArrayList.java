package org.exist.util;

/**
 * @author wolf
 *
 * To change this generated comment edit the template variable "typecomment":
 * Window>Preferences>Java>Templates.
 * To enable and disable the creation of type comments go to
 * Window>Preferences>Java>Code Generation.
 */
public class LongArrayList {

	private long[] data;
	private int position = 0;
	private int grow;
	
	public LongArrayList( int initialSize ) {
		this( initialSize, 10 );
	}
	
	public LongArrayList( int initialSize, int grow ) {
		data = new long[initialSize];
		this.grow = grow;
	}
	
	public void add( long l ) {
		if( ++position < data.length )
			data[position] = l;
		else {
			// resize buffer
			long[] newData = new long[data.length + grow];
			System.arraycopy( data, 0, newData, 0, data.length );
			data = newData;
			data[position] = l;
		}
	}
	
	public long[] getData() {
		long[] newData = new long[position + 1];
		System.arraycopy( data, 0, newData, 0, newData.length );
		return newData;
	}
}
