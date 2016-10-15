package org.exist.http.servlets;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.ParameterBlock;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
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

import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.NativeBroker;
import org.exist.util.FileUtils;
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
	
	private Path outputDir;
	private String defaultMimeType;
	private boolean caching = true;

	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		
		String baseDirStr = config.getInitParameter("base");
		if (baseDirStr == null)
			baseDirStr = ".";
		if (baseDirStr.startsWith("xmldb:")) {
			store = new DBStorage(baseDirStr);
		} else {
			Path baseDir = getAbsolutePath(baseDirStr);
			store = new FileSystemStorage(baseDir);
		}
		
		String outputDirStr = config.getInitParameter("output-dir");
		if (outputDirStr == null)
			outputDirStr = "scaled";
		
		outputDir = getAbsolutePath(outputDirStr);
		if (!Files.exists(outputDir)) {
			try {
				Files.createDirectories(outputDir);
			} catch(final IOException e) {
				throw new ServletException(e);
			}
		}
		
		log("baseDir = " + baseDirStr);
		log("outputDir = " + outputDir);
		
		defaultMimeType = config.getInitParameter("mime-type");
		if (defaultMimeType == null)
			defaultMimeType = "image/jpeg";
		String cacheStr = config.getInitParameter("caching");
		if (cacheStr != null)
			caching = cacheStr.equalsIgnoreCase("yes") || cacheStr.equalsIgnoreCase("true");
	}

	private Path getAbsolutePath(String dirStr) {
		Path dir = Paths.get(dirStr);
		if (!dir.isAbsolute()) {
			String path = getServletConfig().getServletContext().getRealPath(".");
			return Paths.get(path, dirStr);
		}
		return dir;
	}

	@Override
	protected void doGet(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
        String filePath = request.getPathInfo();
        if (filePath.startsWith("/")) {
			filePath = filePath.substring(1);
		}
        filePath = URIUtils.urlDecodeUtf8(filePath);
        
        String action = "scale";
        Matcher matcher = URL_PATTERN.matcher(filePath);
        if (!matcher.matches()) {
        	throw new ServletException("Bad URL format: " + filePath);
        }
        action = matcher.group(1);
        filePath = matcher.group(2);
        
        Path file = store.getFile(filePath);
    	
    	log("action: " + action + " path: " + file.toAbsolutePath());
    	
        String name = FileUtils.fileName(file);
        Path dir = file.getParent();
        
        file = findFile(dir, name);
        
        if (file != null && !(Files.isReadable(file) && Files.isRegularFile(file))) {
    		log("Cannot read image file: " + file.toAbsolutePath());
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
				Path scaled = getFile(dir, file, mime,
						size < 0 ? "" : Integer.toString((int) size));
				log("thumb = " + scaled.toAbsolutePath());
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
			Path scaled = getFile(dir, file, mime, suffix.toString());
			log("thumb = " + scaled.toAbsolutePath());
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
			Path scaled, RenderedImage bufferedImage, boolean cache) throws IOException {
		boolean writeOk = cache ? writeScaled(bufferedImage, scaled, mime) : false;
		if (writeOk) {
			streamScaled(scaled, response.getOutputStream());
		} else {
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
	
    private RenderedOp loadImage(final Path file) throws IOException {
    	if (file == null) {
			return null;
		}
        final FileSeekableStream fss = new FileSeekableStream(file.toFile());
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

    private Path getFile(final Path dir, final Path file, final MimeType mime, final String suffix) throws IOException {
    	String dirName = store.getRelativePath(dir);
    	
        final Path scaledDir = outputDir.resolve(dirName);
        if (!Files.exists(scaledDir)) {
			Files.createDirectories(scaledDir);
		}

        String name = FileUtils.fileName(file);
        int p = name.lastIndexOf('.');
        if (p > 0) {
            name = name.substring(0, p);
        }

        final StringBuilder nameBuilder = new StringBuilder();
        nameBuilder.append(name);
        if (suffix != null) {
			nameBuilder.append('-').append(suffix);
		}
        nameBuilder.append(MimeTable.getInstance().getPreferredExtension(mime)); 
        return scaledDir.resolve(nameBuilder.toString());
    }
    
    private boolean useScaled(final Path image, final Path scaled) throws IOException {
        if (!(Files.exists(scaled) && Files.isReadable(scaled))) {
			return false;
		}

        return Files.getLastModifiedTime(scaled).compareTo(Files.getLastModifiedTime(image)) >= 0;
    }

    private boolean writeScaled(final RenderedImage image, final Path scaled, final MimeType mime) {
        try(final OutputStream os = Files.newOutputStream(scaled)) {
            writeImage(image, os, mime);
            return true;
        } catch (final IOException e) {
        	log(e.getMessage(), e);
            return false;
        }
    }

    private void streamScaled(final Path thumb, final OutputStream os) throws IOException {
		Files.copy(thumb, os);
    }
    
    private Path findFile(final Path dir, final String name) throws IOException {
    	final List<Path> files = FileUtils.list(dir, imageFilter(name));
    	if (files != null && !files.isEmpty()) {
			return files.get(0);
		}
    	return null;
    }

    private static Predicate<Path> imageFilter(final String searchString) {
		return path -> FileUtils.fileName(path).contains(searchString);
	}
    
    private interface Storage {
    	Path getFile(String path);
		String getRelativePath(Path dir);
    }
    
    private static class FileSystemStorage implements Storage {
    	private final Path baseDir;

		public FileSystemStorage(final Path baseDir) {
    		this.baseDir = baseDir;
    	}
		
		@Override
		public Path getFile(final String path) {
			return baseDir.resolve(path);
		}

		@Override
		public String getRelativePath(Path dir) {
			return dir.toAbsolutePath().toString().substring(baseDir.toAbsolutePath().toString().length());
		}
    }
    
    private class DBStorage implements Storage {
    	private Path baseDir;
    	
    	public DBStorage(final String baseCollection) throws ServletException {
    		try {
    			final BrokerPool pool = BrokerPool.getInstance();

    			try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getGuestSubject()))) {
					XmldbURI uri = XmldbURI.xmldbUriFor(baseCollection);
					this.baseDir = ((NativeBroker) broker).getCollectionBinaryFileFsPath(uri.toCollectionPathURI());
					log("baseDir = " + baseDir.toAbsolutePath());
				}
    			
    		} catch (Exception e) {
    			throw new ServletException("Unable to access image collection: " + baseCollection, e);
    		}
    	}

    	@Override
    	public String getRelativePath(final Path dir) {
			return dir.toAbsolutePath().toString().substring(baseDir.toAbsolutePath().toString().length());
		}

		@Override
		public Path getFile(String path) {
			if (!Files.isReadable(baseDir)) {
				return null;
			}
			path = URIUtils.urlEncodePartsUtf8(path);
			return baseDir.resolve(path);
		}
    }
}