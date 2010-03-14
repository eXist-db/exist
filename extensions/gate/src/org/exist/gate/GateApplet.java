package org.exist.gate;

import java.applet.Applet;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.ProxyHost;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.InputStreamRequestEntity;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.exist.gate.desktop.Desktop;

public class GateApplet extends Applet {

	private static final long serialVersionUID = -8952536002584984227L;
	
	private long period = 1000;
	
	private HttpClient http = new HttpClient();
	
	public GateApplet(){
		super();
		String proxyHost = System.getProperty( "http.proxyHost") ; 
        if (proxyHost != null && !proxyHost.equals("")) {
        	ProxyHost proxy = new ProxyHost(proxyHost, Integer.parseInt(System.getProperty("http.proxyPort")));
            http.getHostConfiguration().setProxyHost(proxy);
        }
	}
	
	public void manage(String downloadFrom, String uploadTo){
		
		GetMethod get = new GetMethod(downloadFrom); 
		
		try {
			
			http.executeMethod(get);
			
			long contentLength = ((HttpMethodBase) get).getResponseContentLength();
	        if (contentLength < Integer.MAX_VALUE) {
	        	
	        	InputStream is = get.getResponseBodyAsStream();
	        	
	    		String prefix =  new File(downloadFrom).getName() + ".";
	    		
	    		File tmp = File.createTempFile(prefix, null);
	    		OutputStream os = new FileOutputStream(tmp);
	    		
	    		byte[] data = new byte[1024];
	    		int read = 0;
	    		while((read = is.read(data)) > -1) {
	    			os.write(data, 0, read);
	    		}
	    		os.flush();
	    		is.close();
	    		os.close();
	    		
	    		Listener listener = new Listener(tmp, uploadTo);
	    		
	    		Timer timer = new Timer();
	    		timer.schedule(listener, period, period);
	    		
	    		Desktop desktop = Desktop.getDesktop();
	    		desktop.open(tmp);
	    		
	        }
	        
        } catch (HttpException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			get.releaseConnection();
		}
		
	}
    
	private class Listener extends TimerTask{
		
		private long lastModified;
		private String uploadTo;
		private File file;
		
		public Listener(File file, String uploadTo){
			this.file = file;
			this.uploadTo = uploadTo;
			this.lastModified = file.lastModified();
		}
		
		private boolean isModified(){
			return file.lastModified() > this.lastModified;
		}
		
		public void run() {
			
			if (isModified()){
				
				this.lastModified = file.lastModified();
				
				PutMethod put = new PutMethod(uploadTo);
				
				try {
					
					InputStream is = new FileInputStream(file);
					RequestEntity entity = new InputStreamRequestEntity(is);
					put.setRequestEntity(entity);
					http.executeMethod(put);
					is.close();
					
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					put.releaseConnection();
				}
				
			}
			
		}
		
	}
	
}
