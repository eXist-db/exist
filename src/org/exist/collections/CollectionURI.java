package org.exist.collections;


/**
 * URI to represent a Collection path internally in eXist
 * */
public class CollectionURI
{
	public final static char FRAGMENT_SEPARATOR = '/';
	
	private char[] uri = null;
	private int length = 0;
	
    private int hash; // Default to 0
	
    public CollectionURI(String path)
    {
    	uri = new char[path.length()];
        path.getChars(0, path.length(), uri, 0);
        length = path.length();
    }

	public void append(final String segment)
	{
		if(uri == null)
		{
			uri = new char[segment.length() + 1];
			uri[0] = FRAGMENT_SEPARATOR;
			segment.getChars(0, segment.length(), uri, 1);
		}
		else
		{
			char newURI[] = new char[length + 1 + segment.length()];
			System.arraycopy(uri, 0, newURI, 0, length);
			newURI[length] = FRAGMENT_SEPARATOR;
			segment.getChars(0, segment.length(), newURI, length+1);
			uri = newURI;
		}
		
		length += segment.length() + 1;
		
		//reset the cache
		hash = 0;
	}
	
	public void removeLastSegment()
	{
		char c;
		int pos = length - 1;
		while(pos > -1 && (c = uri[pos]) != FRAGMENT_SEPARATOR)
		{
			pos--;
		}
		
		length = pos;
		
		//reset the cache
		hash = 0;
	}
	
	public String toString() {
		return new String(uri, 0, length);
	}
	
    /**
     * Copied from java.lang.String.hashCode();
     * 
     * Returns a hash code for this string. The hash code for a
     * <code>String</code> object is computed as
     * <blockquote><pre>
     * s[0]*31^(n-1) + s[1]*31^(n-2) + ... + s[n-1]
     * </pre></blockquote>
     * using <code>int</code> arithmetic, where <code>s[i]</code> is the
     * <i>i</i>th character of the string, <code>n</code> is the length of
     * the string, and <code>^</code> indicates exponentiation.
     * (The hash value of the empty string is zero.)
     *
     * @return  a hash code value for this object.
     */
    public int hashCode()
    {
    	int h = hash;
    	if(h == 0)
    	{
		    int off = 0;
	
            for(int i = 0; i < length; i++)
            {
                h = 31*h + uri[off++];
            }
            hash = h;
        }
        return h;
    }
	
	public boolean equals(Object object)
	{
		if(object instanceof CollectionURI)
		{
			CollectionURI otherCollectionURI = (CollectionURI)object;
			
			if(this.length == otherCollectionURI.length)
			{
				int pos = length - 1;
				while(pos > -1)
				{
					if(this.uri[pos] != otherCollectionURI.uri[pos--])
						return false;
				}
				
				return true;
			}
		}
		
		return false;
	}
}