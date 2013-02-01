package org.exist.xmldb;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.apache.xmlrpc.XmlRpcException;
import org.apache.commons.io.output.ByteArrayOutputStream;

import org.exist.security.Permission;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.util.EXistInputSource;
import org.exist.util.VirtualTempFile;
import org.xml.sax.InputSource;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;

public abstract class AbstractRemoteResource
	implements EXistResource, ExtendedResource, Resource
{
	protected XmldbURI path = null ;
	protected String mimeType=null;
	protected RemoteCollection parent;
	protected VirtualTempFile vfile=null;
	protected VirtualTempFile contentVFile=null;
	protected InputSource inputSource = null;
	protected boolean isLocal=false;
	protected long contentLen = 0L;
	protected Permission permissions = null;
	
	protected Date dateCreated= null;
	protected Date dateModified= null;
	
	public AbstractRemoteResource(RemoteCollection parent,XmldbURI documentName)
		throws XMLDBException
	{
		this.parent = parent;
		if (documentName.numSegments()>1) {
			this.path = documentName;
		} else {
			this.path = parent.getPathURI().append(documentName);
		}
	}
    
        @Override
	protected void finalize()
		throws Throwable
	{
		freeResources();
		super.finalize();
	}
	
        @Override
	public void freeResources() {
		vfile = null;
		inputSource = null;
		if(contentVFile!=null) {
			contentVFile.delete();
			contentVFile=null;
		}
		isLocal=true;
	}
	
	protected Properties getProperties() {
		return parent.properties;
	}

	/* (non-Javadoc)
	 * @see org.xmldb.api.base.Resource#getContent()
	 */
	public Object getContent()
		throws XMLDBException
	{
		Object res=getExtendedContent();
		// Backward compatibility
		if(isLocal)  return res;
		if(res!=null) {
			if(res instanceof File) {
				return readFile((File)res);
			} else if(res instanceof InputSource) {
				return readFile((InputSource)res);
			}
		}
		
		return res;
	}
	
	/* (non-Javadoc)
	 * @see org.xmldb.api.base.Resource#getContent()
	 */
	// Backward compatibility
	protected byte[] getData()
		throws XMLDBException
	{
		Object res=getExtendedContent();
		if(res!=null) {
			if(res instanceof File) {
				return readFile((File)res);
			} else if(res instanceof InputSource) {
				return readFile((InputSource)res);
			} else if(res instanceof String) {
				try {
					return ((String)res).getBytes("UTF-8");
				} catch(UnsupportedEncodingException uee) {
					throw new XMLDBException(ErrorCodes.VENDOR_ERROR, uee.getMessage(), uee);
				}
			}
		}
		
		return (byte[])res;
	}
	
	public long getContentLength()
		throws XMLDBException
	{
		return contentLen;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xmldb.EXistResource#getCreationTime()
	 */
	public Date getCreationTime()
		throws XMLDBException
	{
		return dateCreated;
	}
	
	public long getExtendedContentLength()
		throws XMLDBException
	{
		return contentLen;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xmldb.EXistResource#getLastModificationTime()
	 */
	public Date getLastModificationTime()
		throws XMLDBException
	{
		return dateModified;
	}
	
	/* (non-Javadoc)
	* @see org.exist.xmldb.EXistResource#getMimeType()
	*/
	public String getMimeType() {
		return mimeType;
	}
	
	/* (non-Javadoc)
	 * @see org.xmldb.api.base.Resource#getParentCollection()
	 */
	public Collection getParentCollection()
		throws XMLDBException
	{
		return parent;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xmldb.EXistResource#getPermissions()
	 */
	public Permission getPermissions() {
		return permissions;
	}
	
	protected boolean setContentInternal(Object value)
		throws XMLDBException
	{
		freeResources();
		boolean wasSet=false;
		if(value instanceof VirtualTempFile) {
		    vfile = (VirtualTempFile)value;
		    // Assuring the virtual file is close state
		    try {
		    	vfile.close();
		    } catch(IOException ioe) {
		    	// IgnoreIT(R)
		    }
		    setExtendendContentLength(vfile.length());
		    wasSet=true;
		} else if(value instanceof File) {
			vfile = new VirtualTempFile((File) value);
			setExtendendContentLength(vfile.length());
			wasSet=true;
		} else if (value instanceof InputSource) {
			inputSource = (InputSource) value;
		    wasSet=true;
		} else if(value instanceof byte[]) {
			vfile = new VirtualTempFile((byte[])value);
			setExtendendContentLength(vfile.length());
			wasSet=true;
		} else if(value instanceof String) {
			try {
				vfile = new VirtualTempFile(((String)value).getBytes("UTF-8"));
				setExtendendContentLength(vfile.length());
				wasSet=true;
			} catch(UnsupportedEncodingException uee) {
				throw new XMLDBException(ErrorCodes.INVALID_RESOURCE,"input value cannot be translated to UTF-8",uee);
			}
		}
		
		return wasSet;
	}
	
	protected void setExtendendContentLength(long len) {
		this.contentLen = len;
	}
	
	public void setContentLength(int len) {
		this.contentLen = len;
	}
	
	public void setContentLength(long len) {
		this.contentLen = len;
	}
	
	/* (non-Javadoc)
	* @see org.exist.xmldb.EXistResource#setMimeType(java.lang.String)
	*/
	public void setMimeType(String mime) {
		this.mimeType = mime;
	}
	
	public void setPermissions(Permission perms) {
		permissions = perms;
	}
	
	public void getContentIntoAFile(File localfile)
		throws XMLDBException
	{
		FileOutputStream fos=null;
		BufferedOutputStream bos=null;
		try {
			fos=new FileOutputStream(localfile);
			bos=new BufferedOutputStream(fos);
			
			getContentIntoAStream(bos);
		} catch (IOException ioe) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, ioe.getMessage(), ioe);
		} finally {
			if(bos!=null) {
				try {
					bos.close();
				} catch(IOException ioe) {
					// IgnoreIT(R)
				}
			}
			if(fos!=null) {
				try {
					fos.close();
				} catch(IOException ioe) {
					// IgnoreIT(R)
				}
			}
		}
	}
	
    protected void getRemoteContentIntoLocalFile(final OutputStream os, final boolean isRetrieve, final int handle, final int pos) throws XMLDBException {
        Properties properties = getProperties();
        if(properties == null) {
            properties = new Properties();
        }
        
        final String command;
        final List<Object> params = new ArrayList<Object>();
        if(isRetrieve) {
            command = "retrieveFirstChunk";
            params.add(new Integer(handle));
            params.add(new Integer(pos));
        } else {
            command = "getDocumentData";
            params.add(path.toString());
        }
        params.add(properties);
        
        try {
            final VirtualTempFile vtmpfile = new VirtualTempFile();
            vtmpfile.setTempPrefix("eXistARR");
            vtmpfile.setTempPostfix(getResourceType().equals("XMLResource")?".xml":".bin");

            Map<?,?> table = (Map<?,?>) parent.getClient().execute(command, params);
            
            final String method;
            final boolean useLongOffset;
            if(table.containsKey("supports-long-offset") && (Boolean)(table.get("supports-long-offset"))) {
                useLongOffset = true;
                method = "getNextExtendedChunk";
            } else {
                useLongOffset = false;
                method = "getNextChunk";
            }
            
            long offset = ((Integer)table.get("offset")).intValue();
            byte[] data = (byte[])table.get("data");
            final boolean isCompressed = properties.getProperty(EXistOutputKeys.COMPRESS_OUTPUT, "no").equals("yes");
                
            // One for the local cached file
            Inflater dec = null;
            byte[] decResult = null;
            int decLength;
            if(isCompressed) {
                dec = new Inflater();
                decResult = new byte[65536];
                dec.setInput(data);
                do {
                    decLength = dec.inflate(decResult);
                    vtmpfile.write(decResult,0,decLength);
                    // And other for the stream where we want to save it!
                    if(os != null) {
                        os.write(decResult, 0, decLength);
                    }
                } while(decLength == decResult.length || !dec.needsInput());
                
            } else {
                vtmpfile.write(data);
                // And other for the stream where we want to save it!
                if(os != null) {
                    os.write(data);
                }
            }
                
            while(offset > 0) {
                params.clear();
                params.add(table.get("handle"));
                params.add(useLongOffset?Long.toString(offset):new Integer((int)offset));
                table = (Map<?,?>) parent.getClient().execute(method, params);
                offset = useLongOffset?new Long((String)table.get("offset")).longValue():((Integer)table.get("offset")).longValue();
                data = (byte[])table.get("data");

                // One for the local cached file
                if(isCompressed) {
                    dec.setInput(data);
                    do {
                        decLength = dec.inflate(decResult);
                        vtmpfile.write(decResult,0,decLength);
                        // And other for the stream where we want to save it!
                        if(os != null) {
                            os.write(decResult, 0, decLength);
                        }
                    } while(decLength == decResult.length || !dec.needsInput());
                } else {
                    vtmpfile.write(data);
                    // And other for the stream where we want to save it!
                     if(os != null) {
                        os.write(data);
                    }
                }
            }
            
            if(dec != null) {
                dec.end();
            }

            isLocal = false;
            contentVFile = vtmpfile;
        } catch(final XmlRpcException xre) {
                throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, xre.getMessage(), xre);
        } catch(final IOException ioe) {
                throw new XMLDBException(ErrorCodes.VENDOR_ERROR, ioe.getMessage(), ioe);
        } catch(final DataFormatException dfe) {
                throw new XMLDBException(ErrorCodes.VENDOR_ERROR, dfe.getMessage(), dfe);
        } finally {
            if(contentVFile!=null) {
                try {
                    contentVFile.close();
                } catch(final IOException ioe) {
                    //IgnoreIT(R)
                }
            }
        }
    }

	protected static InputStream getAnyStream(Object obj)
		throws XMLDBException
	{
		InputStream bis=null;
		
		if(obj instanceof String) {
			try {
				bis=new ByteArrayInputStream(((String)obj).getBytes("UTF-8"));
			} catch(UnsupportedEncodingException uee) {
				throw new XMLDBException(ErrorCodes.VENDOR_ERROR,uee.getMessage(),uee);
			}
		} else if(obj instanceof byte[]) {
			bis=new ByteArrayInputStream((byte[])obj);
		} else {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR,"don't know how to handle value of type " + obj.getClass().getName());
		}
		
		return bis;
	}
	
	protected void getContentIntoAStreamInternal(OutputStream os, Object obj, boolean isRetrieve, int handle, int pos)
		throws XMLDBException
	{
		if(vfile!=null || contentVFile!=null || inputSource!=null || obj!=null) {
			InputStream bis=null;
			try {
				// First, the local content, then the remote one!!!!
				if(vfile!=null) {
					bis=vfile.getByteStream();
				} else if(inputSource!=null) {
					bis=inputSource.getByteStream();
				} else if(obj!=null) {
					bis=getAnyStream(obj);
				} else {
					bis=contentVFile.getByteStream();
				}
				int readed;
				byte buffer[]=new byte[65536];
				while((readed=bis.read(buffer))!=-1) {
					os.write(buffer,0,readed);
				}
			} catch(IOException ioe) {
				throw new XMLDBException(ErrorCodes.VENDOR_ERROR,ioe.getMessage(),ioe);
			} finally {
				if(inputSource!=null) {
					if(bis!=null) {
						// As it comes from an input source, we cannot blindly close it,
						// but at least let's reset it! (if it is possible)
						if(bis.markSupported()) {
							try {
								bis.reset();
							} catch(IOException ioe) {
								//IgnoreIT(R)
							}
						}
					}
				} else {
					if(bis!=null) {
						try {
							bis.close();
						} catch(IOException ioe) {
							//IgnoreIT(R)
						}
					}
				}
			}
		} else {
			// Let's fetch it, and save just in time!!!
			getRemoteContentIntoLocalFile(os,isRetrieve,handle,pos);
		}
	}

	protected Object getExtendedContentInternal(Object obj, boolean isRetrieve, int handle, int pos)
		throws XMLDBException
	{
		if(obj != null)
			return obj;
		
		if(vfile!=null)
			return vfile.getContent();
		if(inputSource!=null)
			return inputSource;
		
		if(contentVFile==null)
			getRemoteContentIntoLocalFile(null,isRetrieve,handle,pos);
		
		return contentVFile.getContent();
	}

	protected InputStream getStreamContentInternal(Object obj, boolean isRetrieve, int handle, int pos)
		throws XMLDBException
	{
		InputStream retval=null;
		
		if(vfile!=null) {
			try {
				retval=vfile.getByteStream();
			} catch(IOException fnfe) {
				throw new XMLDBException(ErrorCodes.VENDOR_ERROR, fnfe.getMessage(), fnfe);
			}
		} else if(inputSource!=null) {
			retval=inputSource.getByteStream();
		} else if(obj!=null) {
			retval=getAnyStream(obj);
		} else {
			// At least one value, please!!!
			if(contentVFile==null)
				getRemoteContentIntoLocalFile(null,isRetrieve,handle,pos);
			
			try {
				retval=contentVFile.getByteStream();
			} catch(IOException fnfe) {
				throw new XMLDBException(ErrorCodes.VENDOR_ERROR, fnfe.getMessage(), fnfe);
			}
		}
		
		return retval;
	}

	protected long getStreamLengthInternal(Object obj)
		throws XMLDBException
	{
		long retval=-1;
		if(vfile!=null) {
			retval=vfile.length();
		} else if(inputSource!=null && inputSource instanceof EXistInputSource) {
			retval=((EXistInputSource)inputSource).getByteStreamLength();
		} else if(obj!=null) {
			if(obj instanceof String) {
				try {
					retval=((String)obj).getBytes("UTF-8").length;
				} catch(UnsupportedEncodingException uee) {
					throw new XMLDBException(ErrorCodes.VENDOR_ERROR,uee.getMessage(),uee);
				}
			} else if(obj instanceof byte[]) {
				retval=((byte[])obj).length;
			} else {
				throw new XMLDBException(ErrorCodes.VENDOR_ERROR,"don't know how to handle value of type " + obj.getClass().getName());
			}
		} else if(contentVFile!=null) {
			retval=contentVFile.length();
		} else {
			Properties properties = getProperties();
			List<Object> params = new ArrayList<Object>();
			params.add(path.toString());
			params.add(properties);
			try {
				Map<?,?> table = (Map<?,?>) parent.getClient().execute("describeResource", params);
				if(table.containsKey("content-length-64bit")) {
					Object o = table.get("content-length-64bit");
					if(o instanceof Long) {
						retval = ((Long)o).longValue();
					} else {
						retval = Long.parseLong((String)o);
					}
				} else {
					retval=((Integer)table.get("content-length")).intValue();
				}
			} catch (XmlRpcException xre) {
				throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, xre.getMessage(), xre);
			}
		}
		
		return retval;
	}


	
	private static byte[] readFile(File file)
		throws XMLDBException
	{
		String errmsg="file "+ file.getAbsolutePath();
                InputStream is = null;
		try {
                        is = new FileInputStream(file);
			
                        return readFile(is,errmsg);

		} catch (FileNotFoundException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR,
				errmsg + " could not be found", e);
		}
                finally
                {
                    if(is != null)
                    {
                        try
                        {
                            is.close();
                        }
                        catch(IOException ioe)
                        {
                            //ignore(ioe);
                        }
                    }
                }
	}

	private static byte[] readFile(InputSource is)
		throws XMLDBException
	{
		String retval="<streamunknown>";
		if(is instanceof EXistInputSource) {
			retval=((EXistInputSource)is).getSymbolicPath();
		} 
		return readFile(is.getByteStream(),"input source "+retval);
	}

	private static byte[] readFile(InputStream is,String errmsg)
		throws XMLDBException
	{
		if(errmsg==null)
			errmsg="stream";
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream(2048);
			byte[] temp = new byte[1024];
			int count = 0;
			while((count = is.read(temp)) > -1) {
				bos.write(temp, 0, count);
			}
			return bos.toByteArray();
		} catch (IOException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR,
				"IO exception while reading " + errmsg, e);
		}
	}
	
	protected void setDateCreated(Date dateCreated) {
		this.dateCreated = dateCreated;
	}
	
	protected void setDateModified(Date dateModified) {
		this.dateModified = dateModified;
	}
}
