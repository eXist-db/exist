package org.exist.http.servlets;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.ParameterBlock;
import java.io.BufferedOutputStream;
import org.apache.commons.io.output.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.media.jai.ImageLayout;
import javax.media.jai.JAI;
import javax.media.jai.ParameterBlockJAI;
import javax.media.jai.PlanarImage;
import javax.media.jai.RenderedOp;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.exist.collections.Collection;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.NativeBroker;
import org.exist.storage.txn.Txn;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.util.URIUtils;

import com.sun.media.jai.codec.FileSeekableStream;
import com.sun.media.jai.codec.ImageCodec;
import com.sun.media.jai.codec.ImageEncoder;
import com.sun.media.jai.codec.JPEGEncodeParam;

/**
 * General purpose image scaling and cropping servlet. The output image can be cached to
 * the file system.
 * 
 * Any URL handled by the servlet is parsed as follows:
 * 
 * action/path/to/image?parameters
 * 
 * "action" can be either "scale" or "crop".
 * 
 * "path/to/image" is the relative path to the source image. The image name does not need to be
 * complete. The servlet will search the directory for images <b>containing</b> the given string 
 * in their name.
 * 
 * 
 * Configuration parameters in web.xml:
 * 
 * base-dir: the base directory which will be searched for images. Image paths are resolved
 * relative to this directory.
 * 
 * output-dir: if caching is enabled, this is the directory for the cached images or tiles.
 * 
 * mime-type: the mime-type to use for output. Either image/jpeg or image/png. Other formats are
 * not supported.
 * 
 * caching: yes or no. Should images/tiles be cached on the file system?
 * 
 * @author wolf
 *
 */
public class ScaleImageJAI extends HttpServlet {

	private final static int MEM_MAX = 8 * 1024 * 1024;
	
	private Pattern URL_PATTERN = Pattern.compile("^/?([^/]+)/(.*)$");
	
	private Storage store;
	
	private File outputDir;
	private String defaultMimeType;
	private boolean caching = true;
	
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		
		String baseDirStr = config.getInitParameter("base");
		if (baseDirStr == null)
			baseDirStr = ".";
		if (baseDirStr.startsWith("xmldb:")) {
			store = new DBStorage(baseDirStr);
		} else {
			File baseDir = getAbsolutePath(baseDirStr);
			store = new FileSystemStorage(baseDir);
		}
		
		String outputDirStr = config.getInitParameter("output-dir");
		if (outputDirStr == null)
			outputDirStr = "scaled";
		
		outputDir = getAbsolutePath(outputDirStr);
		if (!outputDir.exists())
			outputDir.mkdirs();
		
		log("baseDir = " + baseDirStr);
		log("outputDir = " + outputDir);
		
		defaultMimeType = config.getInitParameter("mime-type");
		if (defaultMimeType == null)
			defaultMimeType = "image/jpeg";
		String cacheStr = config.getInitParameter("caching");
		if (cacheStr != null)
			caching = cacheStr.equalsIgnoreCase("yes") || cacheStr.equalsIgnoreCase("true");
	}

	private File getAbsolutePath(String dirStr) {
		File dir = new File(dirStr);
		if (!dir.isAbsolute()) {
			String path = getServletConfig().getServletContext().getRealPath(".");
			return new File(path, dirStr);
		}
		return dir;
	}
	
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String filePath = request.getPathInfo();
        if (filePath.startsWith("/"))
        	filePath = filePath.substring(1);
        filePath = URIUtils.urlDecodeUtf8(filePath);
        
        String action = "scale";
        Matcher matcher = URL_PATTERN.matcher(filePath);
        if (!matcher.matches()) {
        	throw new ServletException("Bad URL format: " + filePath);
        }
        action = matcher.group(1);
        filePath = matcher.group(2);
        
        File file = store.getFile(filePath);
    	
    	log("action: " + action + " path: " + file.getAbsolutePath());
    	
        String name = file.getName();
        File dir = file.getParentFile();
        
        file = findFile(dir, name);
        
        if (file != null && !(file.canRead() && file.isFile())) {
    		log("Cannot read image file: " + file.getAbsolutePath());
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        if (file == null && "crop".equals(action)) {
        	log("Image file not found.");
        	response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        
        // determine mime type for generated image
        String mimeParam = request.getParameter("type");
        if (mimeParam == null)
        	mimeParam = defaultMimeType;
        
        boolean doCache = caching;
        String cacheParam = request.getParameter("cache");
        if (cacheParam != null)
        	doCache = cacheParam.equalsIgnoreCase("yes") || cacheParam.equalsIgnoreCase("true");
        
        MimeType mime;
        if (file == null)
        	mime = MimeTable.getInstance().getContentType("image/png");
        else
        	mime = MimeTable.getInstance().getContentType(mimeParam);
        response.setContentType(mime.getName());
        
		if ("scale".equals(action)) {
			float size = getParameter(request, "s");
			if (file != null) {
				File scaled = getFile(dir, file, mime,
						size < 0 ? "" : Integer.toString((int) size));
				log("thumb = " + scaled.getAbsolutePath());
				if (useScaled(file, scaled)) {
					streamScaled(scaled, response.getOutputStream());
				} else {
					PlanarImage image = loadImage(file);
					image = scale(image, size);
					writeToResponse(response, mime, scaled, image, doCache);
				}
			} else {
				BufferedImage image = new BufferedImage((int)size, (int)size, BufferedImage.TYPE_INT_ARGB);
//				Graphics2D graphics = image.createGraphics();
//				Color color = new Color(0x00FFFFFF, true);
//				graphics.setColor(color);
//				graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
//				graphics.dispose();
				writeToResponse(response, mime, null, image, false);
			}
		} else if ("crop".equals(action)) {
			float x = getParameter(request, "x");
			float y = getParameter(request, "y");
			float width = getParameter(request, "w");
			float height = getParameter(request, "h");
			StringBuilder suffix = new StringBuilder();
			suffix.append("x").append((int) x).append("y").append((int) y)
					.append("+").append((int) width).append("y")
					.append((int) height);
			File scaled = getFile(dir, file, mime, suffix.toString());
			log("thumb = " + scaled.getAbsolutePath());
			if (useScaled(file, scaled)) {
				streamScaled(scaled, response.getOutputStream());
			} else {
				PlanarImage image = loadImage(file);
				image = crop(image, x, y, width, height);
				writeToResponse(response, mime, scaled, image.getAsBufferedImage(), doCache);
			}
		}
		response.flushBuffer();
    }

	private void writeToResponse(HttpServletResponse response, MimeType mime,
			File scaled, RenderedImage bufferedImage, boolean cache) throws IOException {
		boolean writeOk = cache ? writeScaled(bufferedImage, scaled, mime) : false;
		if (writeOk)
			streamScaled(scaled, response.getOutputStream());
		else {
			BufferedOutputStream os = new BufferedOutputStream(
					response.getOutputStream(), 512);
			writeImage(bufferedImage, os, mime);
			os.flush();
		}
	}

	private float getParameter(HttpServletRequest request, String name) throws ServletException {
		String param = request.getParameter(name);
		if (param != null) {
			try {
				return Float.parseFloat(param);
			} catch (NumberFormatException e) {
				throw new ServletException(
						"Illegal value specified for width: " + param);
			}
		}
		return -1;
	}
	
    private RenderedOp loadImage(File file) throws IOException {
    	if (file == null)
    		return null;
        FileSeekableStream fss = new FileSeekableStream(file);
        return JAI.create("stream", fss);
    }

    private void writeImage(RenderedImage image, OutputStream os, MimeType mime) throws IOException {
        if ("image/png".equals(mime.getName())) {
            JAI.create("encode", image, os, "PNG", null);
        } else {
            JPEGEncodeParam params = new JPEGEncodeParam();
            ImageEncoder encoder = ImageCodec.createImageEncoder("JPEG", os, params);
            encoder.encode(image);
        }
    }

    protected PlanarImage crop(PlanarImage image, float x, float y, float width, float height) {
    	// Create a ParameterBlock with information for the cropping.
    	ParameterBlockJAI pb = new ParameterBlockJAI("crop");  
    	pb.addSource(image);
    	pb.setParameter("x", x);
    	pb.setParameter("y", y);
    	pb.setParameter("width", width);
    	pb.setParameter("height", height);

    	// Create the output image by cropping the input image.
    	return JAI.create("crop", pb);
    	// A cropped image will have its origin set to the (x,y) coordinates,
    	// and with the display method we use it will cause bands on the top
    	// and left borders. A simple way to solve this is to shift or
    	// translate the image by (-x,-y) pixels.
//    	pb = new ParameterBlock();  
//    	pb.addSource(output);  
//    	pb.add(-x);  
//    	pb.add(-y);  
    	
    	// Create the output image by translating itself.
//    	return JAI.create("translate",pb,null);
    }
    
    public PlanarImage scale(PlanarImage image, double edgeLength)
    {
    	if (edgeLength <= 0)
    		return image;
        int height = image.getHeight();
        int width = image.getWidth();
        boolean tall = (height > width);
        double modifier = edgeLength / (double) (tall ? height : width);
        log("modifier = " + modifier + "; edgeLength = " + edgeLength + "; height = " + height);
        if (modifier > 1.0)
            return image;
        ImageLayout layout = new ImageLayout();
        layout.setTileHeight(MEM_MAX);
        layout.setTileWidth(MEM_MAX);
        
        RenderingHints qualityHints = new RenderingHints(RenderingHints.KEY_RENDERING,
            RenderingHints.VALUE_RENDER_QUALITY);
        qualityHints.put(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        qualityHints.put(JAI.KEY_IMAGE_LAYOUT, layout);
        
        ParameterBlock params = new ParameterBlock();
        params.addSource(image);

        params.add(modifier);//x scale factor
        params.add(modifier);//y scale factor
        params.add(qualityHints);

        return JAI.create("SubsampleAverage", params);
     }

    private File getFile(File dir, File file, MimeType mime, String suffix) {
    	String dirName = store.getRelativePath(dir);
    	
        File scaledDir = new File(outputDir, dirName);
        if (!scaledDir.exists())
            scaledDir.mkdirs();
        String name = file.getName();
        int p = name.lastIndexOf('.');
        if (p > 0) {
            name = name.substring(0, p);
        }

        StringBuilder nameBuilder = new StringBuilder();
        nameBuilder.append(name);
        if (suffix != null)
        	nameBuilder.append('-').append(suffix);
        nameBuilder.append(MimeTable.getInstance().getPreferredExtension(mime)); 
        return new File(scaledDir, nameBuilder.toString());
    }
    
    private boolean useScaled(File image, File scaled) {
        if (!(scaled.exists() && scaled.canRead()))
            return false;
        return scaled.lastModified() >= image.lastModified();
    }

    private boolean writeScaled(RenderedImage image, File scaled, MimeType mime) {
        try {
            OutputStream os = new FileOutputStream(scaled);
            writeImage(image, os, mime);
            os.flush();
            os.close();
            return true;
        } catch (IOException e) {
        	log(e.getMessage(), e);
            return false;
        }
    }

    private void streamScaled(File thumb, OutputStream os) throws IOException {
        InputStream is = new FileInputStream(thumb);
        byte[] buf = new byte[128];
        int b;
        while ((b = is.read(buf)) > -1) {
            os.write(buf, 0, b);
        }
        is.close();
        os.flush();
    }
    
    private File findFile(File dir, String name) {
    	ImageFilter filter = new ImageFilter();
    	filter.setSearchString(name);
    	File[] files = dir.listFiles(filter);
    	if (files != null && files.length > 0)
    		return files[0];
    	return null;
    }
    
    private class ImageFilter implements FileFilter {

    	private String searchString;
    	
    	public void setSearchString(String str) {
    		this.searchString = str;
    	}
    	
		@Override
		public boolean accept(File pathname) {
			return (pathname.getName().contains(searchString));
		}
    }
    
    private static interface Storage {
    	
    	public File getFile(String path);
    	
    	public String getRelativePath(File dir);
    }
    
    private static class FileSystemStorage implements Storage {
    	
    	private File baseDir;

		public FileSystemStorage(File baseDir) {
    		this.baseDir = baseDir;
    	}
		
		@Override
		public File getFile(String path) {
			return new File(baseDir, path);
		}
		
		public String getRelativePath(File dir) {
			return
				dir.getAbsolutePath().substring(baseDir.getAbsolutePath().length());
		}
    }
    
    private class DBStorage implements Storage {
    	
    	private File baseDir;
    	
    	public DBStorage(String baseCollection) throws ServletException {
    		BrokerPool pool = null;
    		DBBroker broker = null;
    		try {
    			pool = BrokerPool.getInstance();
    			broker = pool.get(pool.getSecurityManager().getGuestSubject());
    			
    			XmldbURI uri = XmldbURI.xmldbUriFor(baseCollection);
    			this.baseDir = ((NativeBroker)broker).getCollectionBinaryFileFsPath(uri.toCollectionPathURI());
    			log("baseDir = " + baseDir.getAbsolutePath());
    			
    		} catch (Exception e) {
    			throw new ServletException("Unable to access image collection: " + baseCollection, e);
    		} finally {
    			if (pool != null)
    				pool.release(broker);
    		}
    	}
    	
    	public String getRelativePath(File dir) {
			return
				dir.getAbsolutePath().substring(baseDir.getAbsolutePath().length());
		}

		@Override
		public File getFile(String path) {
			if (!baseDir.canRead())
				return null;
			path = URIUtils.urlEncodePartsUtf8(path);
			return new File(baseDir, path);
		}
    }
}