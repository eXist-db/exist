/*
 *  The Apache Software License, Version 1.1
 *
 *
 *  Copyright (c) 1999 The Apache Software Foundation.  All rights
 *  reserved.
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions
 *  are met:
 *
 *  1. Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the
 *  distribution.
 *
 *  3. The end-user documentation included with the redistribution,
 *  if any, must include the following acknowledgment:
 *  "This product includes software developed by the
 *  Apache Software Foundation (http://www.apache.org/)."
 *  Alternately, this acknowledgment may appear in the software itself,
 *  if and wherever such third-party acknowledgments normally appear.
 *
 *  4. The names "Xalan" and "Apache Software Foundation" must
 *  not be used to endorse or promote products derived from this
 *  software without prior written permission. For written
 *  permission, please contact apache@apache.org.
 *
 *  5. Products derived from this software may not be called "Apache",
 *  nor may "Apache" appear in their name, without prior written
 *  permission of the Apache Software Foundation.
 *
 *  THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 *  WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 *  OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *  DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 *  ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 *  SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 *  LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 *  USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 *  ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 *  OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 *  OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 *  SUCH DAMAGE.
 *  ====================================================================
 *
 *  This software consists of voluntary contributions made by many
 *  individuals on behalf of the Apache Software Foundation and was
 *  originally based on software copyright (c) 1999, Lotus
 *  Development Corporation., http://www.lotus.com.  For more
 *  information on the Apache Software Foundation, please see
 *  <http://www.apache.org/>.
 */
package org.exist.util;

/**
 *  Copied this class from Xalan and adopted it for eXist. Bare-bones, unsafe,
 *  fast string buffer. No thread-safety, no parameter range checking, exposed
 *  fields. Note that in typical applications, thread-safety of a StringBuffer
 *  is a somewhat dubious concept in any case. <p>
 *
 *  Note that Stree and DTM used a single FastStringBuffer as a string pool, by
 *  recording start and length indices within this single buffer. This minimizes
 *  heap overhead, but of course requires more work when retrieving the data.
 *  <p>
 *
 *  FastStringBuffer operates as a "chunked buffer". Doing so reduces the need
 *  to recopy existing information when an append exceeds the space available;
 *  we just allocate another chunk and flow across to it. (The array of chunks
 *  may need to grow, admittedly, but that's a much smaller object.) Some excess
 *  recopying may arise when we extract Strings which cross chunk boundaries;
 *  larger chunks make that less frequent. <p>
 *
 *  The size values are parameterized, to allow tuning this code. In theory,
 *  Result Tree Fragments might want to be tuned differently from the main
 *  document's text. <p>
 *
 *  %REVIEW% An experiment in self-tuning is included in the code (using nested
 *  FastStringBuffers to achieve variation in chunk sizes), but this
 *  implementation has proven to be problematic when data may be being copied
 *  from the FSB into itself. We should either re-architect that to make this
 *  safe (if possible) or remove that code and clean up for
 *  performance/maintainability reasons. <p>
 *
 *
 *
 *
 *@created    27. Juni 2002
 */
public class FastStringBuffer implements CharSequence {
    // If nonzero, forces the inial chunk size.
    /*
     *
     */
    final static int DEBUG_FORCE_INIT_BITS = 0;

    // %BUG% %REVIEW% *****PROBLEM SUSPECTED: If data from an FSB is being copied
    // back into the same FSB (variable set from previous variable, for example)
    // and blocksize changes in mid-copy... there's risk of severe malfunction in
    // the read process, due to how the resizing code re-jiggers storage. Arggh.
    // If we want to retain the variable-size-block feature, we need to reconsider
    // that issue. For now, I have forced us into fixed-size mode.
    static boolean DEBUG_FORCE_FIXED_CHUNKSIZE = true;

    /**
     *  Manefest constant: Suppress leading whitespace. This should be used when
     *  normalize-to-SAX is called for the first chunk of a multi-chunk output,
     *  or one following unsuppressed whitespace in a previous chunk.
     *
     *@see    sendNormalizedSAXcharacters(char[],int,int,org.xml.sax.ContentHandler,int)
     */
    public final static int SUPPRESS_LEADING_WS = 0x01;

    /**
     *  Manefest constant: Suppress trailing whitespace. This should be used
     *  when normalize-to-SAX is called for the last chunk of a multi-chunk
     *  output; it may have to be or'ed with SUPPRESS_LEADING_WS.
     */
    public final static int SUPPRESS_TRAILING_WS = 0x02;

    /**
     *  Manefest constant: Suppress both leading and trailing whitespace. This
     *  should be used when normalize-to-SAX is called for a complete string.
     *  (I'm not wild about the name of this one. Ideas welcome.)
     *
     *@see    sendNormalizedSAXcharacters(char[],int,int,org.xml.sax.ContentHandler,int)
     */
    public final static int SUPPRESS_BOTH
             = SUPPRESS_LEADING_WS | SUPPRESS_TRAILING_WS;

    /**
     *  Manefest constant: Carry trailing whitespace of one chunk as leading
     *  whitespace of the next chunk. Used internally; I don't see any reason to
     *  make it public right now.
     */
    private final static int CARRY_WS = 0x04;

    /**
     *  Field m_chunkBits sets our chunking strategy, by saying how many bits of
     *  index can be used within a single chunk before flowing over to the next
     *  chunk. For example, if m_chunkbits is set to 15, each chunk can contain
     *  up to 2^15 (32K) characters
     */
    int m_chunkBits = 15;

    /**
     *  Field m_maxChunkBits affects our chunk-growth strategy, by saying what
     *  the largest permissible chunk size is in this particular
     *  FastStringBuffer hierarchy.
     */
    int m_maxChunkBits = 15;

    /**
     *  Field m_rechunkBits affects our chunk-growth strategy, by saying how
     *  many chunks should be allocated at one size before we encapsulate them
     *  into the first chunk of the next size up. For example, if m_rechunkBits
     *  is set to 3, then after 8 chunks at a given size we will rebundle them
     *  as the first element of a FastStringBuffer using a chunk size 8 times
     *  larger (chunkBits shifted left three bits).
     */
    int m_rebundleBits = 2;

    /**
     *  Field m_chunkSize establishes the maximum size of one chunk of the array
     *  as 2**chunkbits characters. (Which may also be the minimum size if we
     *  aren't tuning for storage)
     */
    int m_chunkSize;
    // =1<<(m_chunkBits-1);

    /**
     *  Field m_chunkMask is m_chunkSize-1 -- in other words, m_chunkBits worth
     *  of low-order '1' bits, useful for shift-and-mask addressing within the
     *  chunks.
     */
    int m_chunkMask;
    // =m_chunkSize-1;

    /**
     *  Field m_array holds the string buffer's text contents, using an
     *  array-of-arrays. Note that this array, and the arrays it contains, may
     *  be reallocated when necessary in order to allow the buffer to grow;
     *  references to them should be considered to be invalidated after any
     *  append. However, the only time these arrays are directly exposed is in
     *  the sendSAXcharacters call.
     */
    char[][] m_array;

    /**
     *  Field m_lastChunk is an index into m_array[], pointing to the last chunk
     *  of the Chunked Array currently in use. Note that additional chunks may
     *  actually be allocated, eg if the FastStringBuffer had previously been
     *  truncated or if someone issued an ensureSpace request. <p>
     *
     *  The insertion point for append operations is addressed by the
     *  combination of m_lastChunk and m_firstFree.
     */
    int m_lastChunk = 0;

    /**
     *  Field m_firstFree is an index into m_array[m_lastChunk][], pointing to
     *  the first character in the Chunked Array which is not part of the
     *  FastStringBuffer's current content. Since m_array[][] is zero-based, the
     *  length of that content can be calculated as (m_lastChunk<<m_chunkBits) +
     *  m_firstFree
     */
    int m_firstFree = 0;

    /**
     *  Field m_innerFSB, when non-null, is a FastStringBuffer whose total
     *  length equals m_chunkSize, and which replaces m_array[0]. This allows
     *  building a hierarchy of FastStringBuffers, where early appends use a
     *  smaller chunkSize (for less wasted memory overhead) but later ones use a
     *  larger chunkSize (for less heap activity overhead).
     */
    FastStringBuffer m_innerFSB = null;

    private StringBuffer tempBuf = new StringBuffer();
    
    /**
     *  Construct a FastStringBuffer, with allocation policy as per parameters.
     *  <p>
     *
     *  For coding convenience, I've expressed both allocation sizes in terms of
     *  a number of bits. That's needed for the final size of a chunk, to permit
     *  fast and efficient shift-and-mask addressing. It's less critical for the
     *  inital size, and may be reconsidered. <p>
     *
     *  An alternative would be to accept integer sizes and round to powers of
     *  two; that really doesn't seem to buy us much, if anything.
     *
     *@param  initChunkBits  Length in characters of the initial allocation of a
     *      chunk, expressed in log-base-2. (That is, 10 means allocate 1024
     *      characters.) Later chunks will use larger allocation units, to trade
     *      off allocation speed of large document against storage efficiency of
     *      small ones.
     *@param  maxChunkBits   Number of character-offset bits that should be used
     *      for addressing within a chunk. Maximum length of a chunk is
     *      2^chunkBits characters.
     *@param  rebundleBits   Number of character-offset bits that addressing
     *      should advance before we attempt to take a step from initChunkBits
     *      to maxChunkBits
     */
    public FastStringBuffer( int initChunkBits, int maxChunkBits,
            int rebundleBits ) {
        if ( DEBUG_FORCE_INIT_BITS != 0 ) {
            initChunkBits = DEBUG_FORCE_INIT_BITS;
        }

        // %REVIEW%
        // Should this force to larger value, or smaller? Smaller less efficient, but if
        // someone requested variable mode it's because they care about storage space.
        // On the other hand, given the other changes I'm making, odds are that we should
        // adopt the larger size. Dither, dither, dither... This is just stopgap workaround
        // anyway; we need a permanant solution.
        //
        if ( DEBUG_FORCE_FIXED_CHUNKSIZE ) {
            maxChunkBits = initChunkBits;
        }
        //if(DEBUG_FORCE_FIXED_CHUNKSIZE) initChunkBits=maxChunkBits;

        m_array = new char[16][];

        // Don't bite off more than we're prepared to swallow!
        if ( initChunkBits > maxChunkBits ) {
            initChunkBits = maxChunkBits;
        }

        m_chunkBits = initChunkBits;
        m_maxChunkBits = maxChunkBits;
        m_rebundleBits = rebundleBits;
        m_chunkSize = 1 << ( initChunkBits );
        m_chunkMask = m_chunkSize - 1;
        m_array[0] = new char[m_chunkSize];
    }


    /**
     *  Construct a FastStringBuffer, using a default rebundleBits value.
     *  NEEDSDOC
     *
     *@param  initChunkBits  NEEDSDOC
     *@param  maxChunkBits
     */
    public FastStringBuffer( int initChunkBits, int maxChunkBits ) {
        this( initChunkBits, maxChunkBits, 2 );
    }


    /**
     *  Construct a FastStringBuffer, using default maxChunkBits and
     *  rebundleBits values. <p>
     *
     *  ISSUE: Should this call assert initial size, or fixed size? Now
     *  configured as initial, with a default for fixed.
     *
     *@param  initChunkBits
     */
    public FastStringBuffer( int initChunkBits ) {
        this( initChunkBits, 15, 2 );
    }


    /**  Construct a FastStringBuffer, using a default allocation policy. */
    public FastStringBuffer() {

        // 10 bits is 1K. 15 bits is 32K. Remember that these are character
        // counts, so actual memory allocation unit is doubled for UTF-16 chars.
        //
        // For reference: In the original FastStringBuffer, we simply
        // overallocated by blocksize (default 1KB) on each buffer-growth.
        this( 10, 15, 2 );
    }


    /**
     *  Get the length of the list. Synonym for length().
     *
     *@return    the number of characters in the FastStringBuffer's content.
     */
    public final int size() {
        return ( m_lastChunk << m_chunkBits ) + m_firstFree;
    }


    /**
     *  Get the length of the list. Synonym for size().
     *
     *@return    the number of characters in the FastStringBuffer's content.
     */
    public final int length() {
        return ( m_lastChunk << m_chunkBits ) + m_firstFree;
    }


    /**
     *  Discard the content of the FastStringBuffer, and most of the memory that
     *  was allocated by it, restoring the initial state. Note that this may
     *  eventually be different from setLength(0), which see.
     */
    public final void reset() {

        m_lastChunk = 0;
        m_firstFree = 0;

        // Recover the original chunk size
        FastStringBuffer innermost = this;

        while ( innermost.m_innerFSB != null ) {
            innermost = innermost.m_innerFSB;
        }

        m_chunkBits = innermost.m_chunkBits;
        m_chunkSize = innermost.m_chunkSize;
        m_chunkMask = innermost.m_chunkMask;

        // Discard the hierarchy
        m_innerFSB = null;
        m_array = new char[16][0];
        m_array[0] = new char[m_chunkSize];
    }


    /**
     *  Directly set how much of the FastStringBuffer's storage is to be
     *  considered part of its content. This is a fast but hazardous operation.
     *  It is not protected against negative values, or values greater than the
     *  amount of storage currently available... and even if additional storage
     *  does exist, its contents are unpredictable. The only safe use for our
     *  setLength() is to truncate the FastStringBuffer to a shorter string.
     *
     *@param  l  New length. If l<0 or l>=getLength(), this operation will not
     *      report an error but future operations will almost certainly fail.
     */
    public final void setLength( int l ) {
        m_lastChunk = l >>> m_chunkBits;

        if ( m_lastChunk == 0 && m_innerFSB != null ) {
            // Replace this FSB with the appropriate inner FSB, truncated
            m_innerFSB.setLength( l, this );
        } else {
            m_firstFree = l & m_chunkMask;

            // There's an edge case if l is an exact multiple of m_chunkBits, which risks leaving
            // us pointing at the start of a chunk which has not yet been allocated. Rather than
            // pay the cost of dealing with that in the append loops (more scattered and more
            // inner-loop), we correct it here by moving to the safe side of that
            // line -- as we would have left the indexes had we appended up to that point.
            if ( m_firstFree == 0 && m_lastChunk > 0 ) {
                --m_lastChunk;
                m_firstFree = m_chunkSize;
            }
        }
    }


    /**
     *  Subroutine for the public setLength() method. Deals with the fact that
     *  truncation may require restoring one of the innerFSBs NEEDSDOC
     *
     *@param  l        NEEDSDOC
     *@param  rootFSB
     */
    private final void setLength( int l, FastStringBuffer rootFSB ) {

        m_lastChunk = l >>> m_chunkBits;

        if ( m_lastChunk == 0 && m_innerFSB != null ) {
            m_innerFSB.setLength( l, rootFSB );
        } else {

            // Undo encapsulation -- pop the innerFSB data back up to root.
            // Inefficient, but attempts to keep the code simple.
            rootFSB.m_chunkBits = m_chunkBits;
            rootFSB.m_maxChunkBits = m_maxChunkBits;
            rootFSB.m_rebundleBits = m_rebundleBits;
            rootFSB.m_chunkSize = m_chunkSize;
            rootFSB.m_chunkMask = m_chunkMask;
            rootFSB.m_array = m_array;
            rootFSB.m_innerFSB = m_innerFSB;
            rootFSB.m_lastChunk = m_lastChunk;

            // Finally, truncate this sucker.
            rootFSB.m_firstFree = l & m_chunkMask;
        }
    }


    /**
     *  Note that this operation has been somewhat deoptimized by the shift to a
     *  chunked array, as there is no factory method to produce a String object
     *  directly from an array of arrays and hence a double copy is needed. By
     *  using ensureCapacity we hope to minimize the heap overhead of building
     *  the intermediate StringBuffer. <p>
     *
     *  (It really is a pity that Java didn't design String as a final subclass
     *  of MutableString, rather than having StringBuffer be a separate
     *  hierarchy. We'd avoid a <strong>lot</strong> of double-buffering.)
     *
     *@return    the contents of the FastStringBuffer as a standard Java string.
     */
    public final String toString() {

        int length = ( m_lastChunk << m_chunkBits ) + m_firstFree;
        tempBuf.setLength(0);
        return getString( tempBuf, 0, 0, length ).toString();
    }


    /**
     *  Append a single character onto the FastStringBuffer, growing the storage
     *  if necessary. <p>
     *
     *  NOTE THAT after calling append(), previously obtained references to
     *  m_array[][] may no longer be valid.... though in fact they should be in
     *  this instance.
     *
     *@param  value  character to be appended.
     */
    public final void append( char value ) {

        char[] chunk;

        // We may have preallocated chunks. If so, all but last should
        // be at full size.
        boolean lastchunk = ( m_lastChunk + 1 == m_array.length );

        if ( m_firstFree < m_chunkSize ) {
            // Simplified test single-character-fits
            chunk = m_array[m_lastChunk];
        } else {

            // Extend array?
            int i = m_array.length;

            if ( m_lastChunk + 1 == i ) {
                char[][] newarray = new char[i + 16][];

                System.arraycopy( m_array, 0, newarray, 0, i );

                m_array = newarray;
            }

            // Advance one chunk
            chunk = m_array[++m_lastChunk];

            if ( chunk == null ) {

                // Hierarchical encapsulation
                if ( m_lastChunk == 1 << m_rebundleBits
                         && m_chunkBits < m_maxChunkBits ) {

                    // Should do all the work of both encapsulating
                    // existing data and establishing new sizes/offsets
                    m_innerFSB = new FastStringBuffer( this );
                }

                // Add a chunk.
                chunk = m_array[m_lastChunk] = new char[m_chunkSize];
            }

            m_firstFree = 0;
        }

        // Space exists in the chunk. Append the character.
        chunk[m_firstFree++] = value;
    }


    /**
     *  Append the contents of a String onto the FastStringBuffer, growing the
     *  storage if necessary. <p>
     *
     *  NOTE THAT after calling append(), previously obtained references to
     *  m_array[] may no longer be valid.
     *
     *@param  value  String whose contents are to be appended.
     */
    public final void append( String value ) {

        if ( value == null ) {
            return;
        }
        int strlen = value.length();

        if ( 0 == strlen ) {
            return;
        }

        int copyfrom = 0;
        char[] chunk = m_array[m_lastChunk];
        int available = m_chunkSize - m_firstFree;

        // Repeat while data remains to be copied
        while ( strlen > 0 ) {

            // Copy what fits
            if ( available > strlen ) {
                available = strlen;
            }

            value.getChars( copyfrom, copyfrom + available, m_array[m_lastChunk],
                    m_firstFree );

            strlen -= available;
            copyfrom += available;

            // If there's more left, allocate another chunk and continue
            if ( strlen > 0 ) {

                // Extend array?
                int i = m_array.length;

                if ( m_lastChunk + 1 == i ) {
                    char[][] newarray = new char[i + 16][];

                    System.arraycopy( m_array, 0, newarray, 0, i );

                    m_array = newarray;
                }

                // Advance one chunk
                chunk = m_array[++m_lastChunk];

                if ( chunk == null ) {

                    // Hierarchical encapsulation
                    if ( m_lastChunk == 1 << m_rebundleBits
                             && m_chunkBits < m_maxChunkBits ) {

                        // Should do all the work of both encapsulating
                        // existing data and establishing new sizes/offsets
                        m_innerFSB = new FastStringBuffer( this );
                    }

                    // Add a chunk.
                    chunk = m_array[m_lastChunk] = new char[m_chunkSize];
                }

                available = m_chunkSize;
                m_firstFree = 0;
            }
        }

        // Adjust the insert point in the last chunk, when we've reached it.
        m_firstFree += available;
    }


    /**
     *  Append the contents of a StringBuffer onto the FastStringBuffer, growing
     *  the storage if necessary. <p>
     *
     *  NOTE THAT after calling append(), previously obtained references to
     *  m_array[] may no longer be valid.
     *
     *@param  value  StringBuffer whose contents are to be appended.
     */
    public final void append( StringBuffer value ) {

        if ( value == null ) {
            return;
        }
        int strlen = value.length();

        if ( 0 == strlen ) {
            return;
        }

        int copyfrom = 0;
        char[] chunk = m_array[m_lastChunk];
        int available = m_chunkSize - m_firstFree;

        // Repeat while data remains to be copied
        while ( strlen > 0 ) {

            // Copy what fits
            if ( available > strlen ) {
                available = strlen;
            }

            value.getChars( copyfrom, copyfrom + available, m_array[m_lastChunk],
                    m_firstFree );

            strlen -= available;
            copyfrom += available;

            // If there's more left, allocate another chunk and continue
            if ( strlen > 0 ) {

                // Extend array?
                int i = m_array.length;

                if ( m_lastChunk + 1 == i ) {
                    char[][] newarray = new char[i + 16][];

                    System.arraycopy( m_array, 0, newarray, 0, i );

                    m_array = newarray;
                }

                // Advance one chunk
                chunk = m_array[++m_lastChunk];

                if ( chunk == null ) {

                    // Hierarchical encapsulation
                    if ( m_lastChunk == 1 << m_rebundleBits
                             && m_chunkBits < m_maxChunkBits ) {

                        // Should do all the work of both encapsulating
                        // existing data and establishing new sizes/offsets
                        m_innerFSB = new FastStringBuffer( this );
                    }

                    // Add a chunk.
                    chunk = m_array[m_lastChunk] = new char[m_chunkSize];
                }

                available = m_chunkSize;
                m_firstFree = 0;
            }
        }

        // Adjust the insert point in the last chunk, when we've reached it.
        m_firstFree += available;
    }


    /**
     *  Append part of the contents of a Character Array onto the
     *  FastStringBuffer, growing the storage if necessary. <p>
     *
     *  NOTE THAT after calling append(), previously obtained references to
     *  m_array[] may no longer be valid.
     *
     *@param  chars   character array from which data is to be copied
     *@param  start   offset in chars of first character to be copied,
     *      zero-based.
     *@param  length  number of characters to be copied
     */
    public final void append( char[] chars, int start, int length ) {

        int strlen = length;

        if ( 0 == strlen ) {
            return;
        }

        int copyfrom = start;
        char[] chunk = m_array[m_lastChunk];
        int available = m_chunkSize - m_firstFree;

        // Repeat while data remains to be copied
        while ( strlen > 0 ) {

            // Copy what fits
            if ( available > strlen ) {
                available = strlen;
            }

            System.arraycopy( chars, copyfrom, m_array[m_lastChunk], m_firstFree,
                    available );

            strlen -= available;
            copyfrom += available;

            // If there's more left, allocate another chunk and continue
            if ( strlen > 0 ) {

                // Extend array?
                int i = m_array.length;

                if ( m_lastChunk + 1 == i ) {
                    char[][] newarray = new char[i + 16][];

                    System.arraycopy( m_array, 0, newarray, 0, i );

                    m_array = newarray;
                }

                // Advance one chunk
                chunk = m_array[++m_lastChunk];

                if ( chunk == null ) {

                    // Hierarchical encapsulation
                    if ( m_lastChunk == 1 << m_rebundleBits
                             && m_chunkBits < m_maxChunkBits ) {

                        // Should do all the work of both encapsulating
                        // existing data and establishing new sizes/offsets
                        m_innerFSB = new FastStringBuffer( this );
                    }

                    // Add a chunk.
                    chunk = m_array[m_lastChunk] = new char[m_chunkSize];
                }

                available = m_chunkSize;
                m_firstFree = 0;
            }
        }

        // Adjust the insert point in the last chunk, when we've reached it.
        m_firstFree += available;
    }


    /**
     *  Append the contents of another FastStringBuffer onto this
     *  FastStringBuffer, growing the storage if necessary. <p>
     *
     *  NOTE THAT after calling append(), previously obtained references to
     *  m_array[] may no longer be valid.
     *
     *@param  value  FastStringBuffer whose contents are to be appended.
     */
    public final void append( FastStringBuffer value ) {

        // Complicating factor here is that the two buffers may use
        // different chunk sizes, and even if they're the same we're
        // probably on a different alignment due to previously appended
        // data. We have to work through the source in bite-sized chunks.
        if ( value == null ) {
            return;
        }
        int strlen = value.length();

        if ( 0 == strlen ) {
            return;
        }

        int copyfrom = 0;
        char[] chunk = m_array[m_lastChunk];
        int available = m_chunkSize - m_firstFree;

        // Repeat while data remains to be copied
        while ( strlen > 0 ) {

            // Copy what fits
            if ( available > strlen ) {
                available = strlen;
            }

            int sourcechunk = ( copyfrom + value.m_chunkSize - 1 )
                     >>> value.m_chunkBits;
            int sourcecolumn = copyfrom & value.m_chunkMask;
            int runlength = value.m_chunkSize - sourcecolumn;

            if ( runlength > available ) {
                runlength = available;
            }

            System.arraycopy( value.m_array[sourcechunk], sourcecolumn,
                    m_array[m_lastChunk], m_firstFree, runlength );

            if ( runlength != available ) {
                System.arraycopy( value.m_array[sourcechunk + 1], 0,
                        m_array[m_lastChunk], m_firstFree + runlength,
                        available - runlength );
            }

            strlen -= available;
            copyfrom += available;

            // If there's more left, allocate another chunk and continue
            if ( strlen > 0 ) {

                // Extend array?
                int i = m_array.length;

                if ( m_lastChunk + 1 == i ) {
                    char[][] newarray = new char[i + 16][];

                    System.arraycopy( m_array, 0, newarray, 0, i );

                    m_array = newarray;
                }

                // Advance one chunk
                chunk = m_array[++m_lastChunk];

                if ( chunk == null ) {

                    // Hierarchical encapsulation
                    if ( m_lastChunk == 1 << m_rebundleBits
                             && m_chunkBits < m_maxChunkBits ) {

                        // Should do all the work of both encapsulating
                        // existing data and establishing new sizes/offsets
                        m_innerFSB = new FastStringBuffer( this );
                    }

                    // Add a chunk.
                    chunk = m_array[m_lastChunk] = new char[m_chunkSize];
                }

                available = m_chunkSize;
                m_firstFree = 0;
            }
        }

        // Adjust the insert point in the last chunk, when we've reached it.
        m_firstFree += available;
    }


    /**
     *@param  start   Offset of first character in the range.
     *@param  length  Number of characters to send.
     *@return         true if the specified range of characters are all
     *      whitespace, as defined by XMLCharacterRecognizer. <p>
     *
     *      CURRENTLY DOES NOT CHECK FOR OUT-OF-RANGE.
     */
    public boolean isWhitespace( int start, int length ) {

        int sourcechunk = start >>> m_chunkBits;
        int sourcecolumn = start & m_chunkMask;
        int available = m_chunkSize - sourcecolumn;
        boolean chunkOK;

        while ( length > 0 ) {
            int runlength = ( length <= available ) ? length : available;

            if ( sourcechunk == 0 && m_innerFSB != null ) {
                chunkOK = m_innerFSB.isWhitespace( sourcecolumn, runlength );
            } else {
                chunkOK = org.apache.xml.utils.XMLCharacterRecognizer.isWhiteSpace(
                        m_array[sourcechunk], sourcecolumn, runlength );
            }

            if ( !chunkOK ) {
                return false;
            }

            length -= runlength;

            ++sourcechunk;

            sourcecolumn = 0;
            available = m_chunkSize;
        }

        return true;
    }


    /**
     *@param  start   Offset of first character in the range.
     *@param  length  Number of characters to send.
     *@return         a new String object initialized from the specified range
     *      of characters.
     */
    public String getString( int start, int length ) {
        tempBuf.setLength(0);
        return getString( tempBuf, start >>> m_chunkBits,
                start & m_chunkMask, length ).toString();
    }

    /**
     *  Gets the string attribute of the FastStringBuffer object
     *
     *@return    The string value
     */
    /*
     *  public StringBuffer getString() {
     *  StringBuffer buf = new StringBuffer( length() );
     *  return getString( buf, 0, length() );
     *  }
     */
    public StringBuffer getString() {
        tempBuf.setLength(0);
        return getString( tempBuf );
    }


    /**
     *  Gets the string attribute of the FastStringBuffer object
     *
     *@param  buf  Description of the Parameter
     *@return      The string value
     */
    public StringBuffer getString( StringBuffer buf ) {
        for ( int i = 0; i < m_lastChunk; i++ ) {
            if ( i == 0 && m_innerFSB != null ) {
                m_innerFSB.getString( buf );
            } else {
                buf.append( m_array[i], 0, m_chunkSize );
            }
        }
        if ( m_lastChunk == 0 && m_innerFSB != null ) {
            m_innerFSB.getString( buf );
        } else {
            buf.append( m_array[m_lastChunk], 0, m_firstFree );
        }
        return buf;
    }


    /**
     *  Description of the Method
     *
     *@param  newBuf  Description of the Parameter
     *@param  offset  Description of the Parameter
     */
    public void copyTo( char[] newBuf, int offset ) {
        int pos = offset;
        for ( int i = 0; i < m_lastChunk; i++ ) {
            if ( i == 0 && m_innerFSB != null ) {
                m_innerFSB.copyTo( newBuf, pos );
            } else {
                System.arraycopy( m_array[i], 0, newBuf,
                        pos, m_chunkSize );
            }
            pos += m_chunkSize;
        }
        if ( m_lastChunk == 0 && m_innerFSB != null ) {
            m_innerFSB.copyTo( newBuf, pos );
        } else {
            System.arraycopy( m_array[m_lastChunk], 0, newBuf, pos, m_firstFree );
        }
    }


    /**
     *  Gets the whiteSpace attribute of the FastStringBuffer class
     *
     *@param  ch  Description of the Parameter
     *@return     The whiteSpace value
     */
    public static boolean isWhiteSpace( char ch ) {
        return ( ch == 0x20 ) || ( ch == 0x09 ) || ( ch == 0xD ) || ( ch == 0xA );
    }


    /**
     *  Gets the normalizedString attribute of the FastStringBuffer object
     *
     *@param  mode  Description of the Parameter
     *@return       The normalizedString value
     */
    public String getNormalizedString( int mode ) {
        //StringBuffer buf = new StringBuffer();
        tempBuf.setLength(0);
        return getNormalizedString( tempBuf, mode ).toString();
    }


    /**
     *  Gets the normalizedString attribute of the FastStringBuffer object
     *
     *@param  sb    Description of the Parameter
     *@param  mode  Description of the Parameter
     *@return       The normalizedString value
     */
    public StringBuffer getNormalizedString( StringBuffer sb, int mode ) {
        int end = m_firstFree - 1;
        if ( ( mode & SUPPRESS_TRAILING_WS ) != 0 ) {
            while ( end > 0 && isWhiteSpace( m_array[m_lastChunk][end] ) ) {
                end--;
            }
        }
        int pos = 0;
        for ( int i = 0; i < m_lastChunk; i++ ) {
            if ( i == 0 && m_innerFSB != null ) {
                m_innerFSB.getNormalizedString( sb, mode );
            } else if ( i == 0 && ( mode & SUPPRESS_LEADING_WS ) != 0 ) {
                int j = 0;
                while ( j < m_chunkSize && isWhiteSpace( m_array[i][j] ) ) {
                    j++;
                }
                sb.append( m_array[i], j, m_chunkSize - j );
            } else {
                sb.append( m_array[i], 0, m_chunkSize );
            }
        }
        if ( m_lastChunk == 0 ) {
            if ( m_innerFSB != null ) {
                m_innerFSB.getNormalizedString( sb, mode );
            } else {
                if ( ( mode & SUPPRESS_LEADING_WS ) != 0 ) {
                    int j = 0;
                    while ( isWhiteSpace( m_array[m_lastChunk][j] ) &&
                            j <= end ) {
                        j++;
                    }
                    sb.append( m_array[m_lastChunk], j, end - j + 1 );
                } else {
                    sb.append( m_array[m_lastChunk], 0, end + 1 );
                }
            }
        } else {
            sb.append( m_array[m_lastChunk], 0, end + 1 );
        }
        return sb;
    }


    /**
     *@param  sb      StringBuffer to be appended to
     *@param  start   Offset of first character in the range.
     *@param  length  Number of characters to send.
     *@return         sb with the requested text appended to it
     */
    StringBuffer getString( StringBuffer sb, int start, int length ) {
        return getString( sb, start >>> m_chunkBits, start & m_chunkMask, length );
    }


    /**
     *  Internal support for toString() and getString(). PLEASE NOTE SIGNATURE
     *  CHANGE from earlier versions; it now appends into and returns a
     *  StringBuffer supplied by the caller. This simplifies m_innerFSB support.
     *  <p>
     *
     *  Note that this operation has been somewhat deoptimized by the shift to a
     *  chunked array, as there is no factory method to produce a String object
     *  directly from an array of arrays and hence a double copy is needed. By
     *  presetting length we hope to minimize the heap overhead of building the
     *  intermediate StringBuffer. <p>
     *
     *  (It really is a pity that Java didn't design String as a final subclass
     *  of MutableString, rather than having StringBuffer be a separate
     *  hierarchy. We'd avoid a <strong>lot</strong> of double-buffering.)
     *
     *@param  sb
     *@param  startChunk
     *@param  startColumn
     *@param  length
     *@return              the contents of the FastStringBuffer as a standard
     *      Java string.
     */
    StringBuffer getString( StringBuffer sb, int startChunk, int startColumn,
            int length ) {

        int stop = ( startChunk << m_chunkBits ) + startColumn + length;
        int stopChunk = stop >>> m_chunkBits;
        int stopColumn = stop & m_chunkMask;

        // Factored out
        //StringBuffer sb=new StringBuffer(length);
        for ( int i = startChunk; i < stopChunk; ++i ) {
            if ( i == 0 && m_innerFSB != null ) {
                m_innerFSB.getString( sb, startColumn, m_chunkSize - startColumn );
            } else {
                sb.append( m_array[i], startColumn, m_chunkSize - startColumn );
            }

            startColumn = 0;
            // after first chunk
        }

        if ( stopChunk == 0 && m_innerFSB != null ) {
            m_innerFSB.getString( sb, startColumn, stopColumn - startColumn );
        } else if ( stopColumn > startColumn ) {
            sb.append( m_array[stopChunk], startColumn, stopColumn - startColumn );
        }

        return sb;
    }


    /**
     *  Get a single character from the string buffer.
     *
     *@param  pos  character position requested.
     *@return      A character from the requested position.
     */
    public char charAt( int pos ) {
        int startChunk = pos >>> m_chunkBits;

        if ( startChunk == 0 && m_innerFSB != null ) {
            return m_innerFSB.charAt( pos & m_chunkMask );
        } else {
            return m_array[startChunk][pos & m_chunkMask];
        }
    }


    /**
     *  Encapsulation c'tor. After this is called, the source FastStringBuffer
     *  will be reset to use the new object as its m_innerFSB, and will have had
     *  its chunk size reset appropriately. IT SHOULD NEVER BE CALLED EXCEPT
     *  WHEN source.length()==1<<(source.m_chunkBits+source.m_rebundleBits)
     *  NEEDSDOC
     *
     *@param  source
     */
    private FastStringBuffer( FastStringBuffer source ) {

        // Copy existing information into new encapsulation
        m_chunkBits = source.m_chunkBits;
        m_maxChunkBits = source.m_maxChunkBits;
        m_rebundleBits = source.m_rebundleBits;
        m_chunkSize = source.m_chunkSize;
        m_chunkMask = source.m_chunkMask;
        m_array = source.m_array;
        m_innerFSB = source.m_innerFSB;

        // These have to be adjusted because we're calling just at the time
        // when we would be about to allocate another chunk
        m_lastChunk = source.m_lastChunk - 1;
        m_firstFree = source.m_chunkSize;

        // Establish capsule as the Inner FSB, reset chunk sizes/addressing
        source.m_array = new char[16][];
        source.m_innerFSB = this;

        // Since we encapsulated just as we were about to append another
        // chunk, return ready to create the chunk after the innerFSB
        // -- 1, not 0.
        source.m_lastChunk = 1;
        source.m_firstFree = 0;
        source.m_chunkBits += m_rebundleBits;
        source.m_chunkSize = 1 << ( source.m_chunkBits );
        source.m_chunkMask = source.m_chunkSize - 1;
    }
	/**
	 * @see java.lang.CharSequence#subSequence(int, int)
	 */
	public CharSequence subSequence(int start, int end) {
		return getString(start, end - start);
	}

}

