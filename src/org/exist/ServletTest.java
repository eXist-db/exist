
package org.exist;

import java.net.*;
import java.io.*;

public class ServletTest {

	public static void main(String args[]) {
		try {
			String line;
			String uri = "http://localhost:8088/" + args[0];
			URL url = new URL(uri);
			HttpURLConnection con = (HttpURLConnection)url.openConnection();
			con.setRequestMethod("PUT");
			con.setDoOutput(true);
			
			BufferedReader fin =
				new BufferedReader(new FileReader(args[0]));
			PrintStream out = new PrintStream(con.getOutputStream());
			while((line = fin.readLine()) != null)
				out.println(line);
			con.connect();
			BufferedReader in = 
				new BufferedReader(new InputStreamReader(con.getInputStream()));
			while((line = in.readLine()) != null)
				System.out.println(line);
		} catch(Exception e) {
			System.err.println(e);
		}
	}
}
			
