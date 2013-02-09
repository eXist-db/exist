/*
 * eXist Open Source Native XML Database Copyright (C) 2001-2005, Wolfgang M.
 * Meier (meier@ifs.tu-darmstadt.de)
 * 
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Library General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option) any
 * later version.
 * 
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Library General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU Library General Public License
 * along with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 * 
 * $Id$
 */
package org.exist.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.output.ByteArrayOutputStream;

public class Compressor {

    /**
     * The method <code>compress</code>
     *
     * @param whatToCompress a <code>byte[]</code> value
     * @return a <code>byte[]</code> value
     * @exception IOException if an error occurs
     */
    public static byte[] compress(byte[] whatToCompress) throws IOException {
        return compress(whatToCompress, whatToCompress.length);
    }
    
    /**
     * The method <code>compress</code>
     *
     * @param whatToCompress a <code>byte[]</code> value
     * @param length an <code>int</code> value
     * @return a <code>byte[]</code> value
     * @exception IOException if an error occurs
     */
    public static byte[] compress(byte[] whatToCompress, int length) throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final ZipOutputStream gzos = new ZipOutputStream(baos);
        gzos.setMethod(ZipOutputStream.DEFLATED);
        gzos.putNextEntry(new ZipEntry(length + ""));
        gzos.write(whatToCompress, 0, length);
        gzos.closeEntry();
        gzos.finish();
        gzos.close();
        return baos.toByteArray();
    }
    
    /**
     * The method <code>uncompress</code>
     *
     * @param whatToUncompress a <code>byte[]</code> value
     * @return a <code>byte[]</code> value
     * @exception IOException if an error occurs
     */
    public static byte[] uncompress(byte[] whatToUncompress)
	throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        uncompress(whatToUncompress, baos);
        return baos.toByteArray();
    }
    
    public static void uncompress(byte[] whatToUncompress, OutputStream os)
    throws IOException {
        final ByteArrayInputStream bais = new ByteArrayInputStream(whatToUncompress);
        final ZipInputStream gzis = new ZipInputStream(bais);
        final ZipEntry zipentry = gzis.getNextEntry();
        Integer.parseInt(zipentry.getName());
        final byte[] buf = new byte[512];
        int bread;
        while ((bread = gzis.read(buf)) != -1)
            os.write(buf, 0, bread);
        gzis.closeEntry();
        gzis.close();
    }
}

