/*
 * Copyright 2012-2013 The MITRE Corporation, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this work except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.mitre.svmp.stream;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.Map;
import java.util.TreeMap;

import android.util.Log;

public class RTSPThread extends Thread {
	private static final String TAG = "RTSP-THREAD";

	Socket sock;
	Session sess;
	StreamServer srv;	

	private String streamSourceIP = "192.168.100.50";
	private String clientIP = null;
	private String baseDescription = "";
	
	
	public RTSPThread(Socket s, StreamServer srv) throws SocketException {
		System.out.println("Got connection!");
		this.sock = s;
		s.setSoTimeout(1000*120);	// 120 seconds = 2 minutes before timeout
		this.srv = srv;		

		if (streamSourceIP == null)
			streamSourceIP = sock.getLocalAddress().getHostAddress();

		baseDescription = "v=0\n" +
		"o=mocsi 1234567890 1 IN IP4 "+streamSourceIP+"\n" +
        //"o=mocsi 1234567890 1 IN IP4 "+"192.168.42.108"+"\n" +
		"s=MOCSI SVMP Thin Client RTP Stream\n" +
		"i=Thin client video\n" +
		"t=0 0\n" +
		"a=range:npt=now-\n" +
		"c=IN IP4 "+streamSourceIP+"\n";
		
		clientIP = sock.getInetAddress().getHostAddress();		
		Log.e(TAG,"starting session with IP: " + clientIP);
		sess = srv.openSession("rtsp");	
		if (sess == null)
			Log.e(TAG,"srv.openSession returned null");
		sess.start(clientIP);
		Log.e(TAG,"session established:"+sess.getID());
		
	}
	
	private TreeMap<String,String> responseHeaders(TreeMap<String, String> headers) {
		TreeMap<String,String> responseHeaders = new TreeMap<String, String>();
		
		responseHeaders.put("Server", "SVMPStreamServer/1.0.0 (MITRE MOCSI)");
		
		if(headers.get("cseq") != null)
			responseHeaders.put("CSeq", headers.get("cseq"));
		
		if(sess!=null)
			responseHeaders.put("Session", sess.getID());
		
		return responseHeaders;
	}
	
	private void writeHeaders(TreeMap<String, String> headers, Writer w) throws IOException {
		for(Map.Entry<String,String> entry : headers.entrySet()) {
			String key = entry.getKey(), value = entry.getValue();
			
			w.write(key+": "+value+"\r\n");
		}
	}
	
	public void run() {
		
		try {
			BufferedReader r = new BufferedReader(new InputStreamReader(sock.getInputStream()));
			OutputStreamWriter w = new OutputStreamWriter(sock.getOutputStream());
			while(true) {
				handleRequest(r, w);
			}
		} catch(IOException e) {
			System.err.println("IOException in RTSPThread!");
			e.printStackTrace();
		} catch(Exception e) {
			System.err.println("Exception in RTSPThread!");
			e.printStackTrace();
		}
		
	
		
		
		try {
			sock.close();
		} catch (IOException e) {
			System.err.println("Couldn't close socket!");
			e.printStackTrace();
		}
	}
	
	private void handleRequest(BufferedReader r, OutputStreamWriter w) throws Exception {		
		String request = r.readLine();
		String[] requestParams = null;
		if ( request != null ) {
			System.err.println("request: " + request);
			request = request.trim();
			requestParams = request.split(" ");
		}
		
		if(request == null ) {
			throw new Exception("Bad request ");
		}
		
		String method = requestParams[0].toUpperCase();
		String url = requestParams[1];
		String protocol = requestParams[2];
		
		if(!protocol.equals("RTSP/1.0")) {
			throw new Exception("Bad protocol '"+request+"'("+requestParams.length+")");
		}
		
		String path = url.substring(7);
		path = path.substring(path.indexOf("/")+1);
		
		while(path.indexOf("/")==0)
			path = path.substring(1);
				
		String track = null;
		if(path.indexOf("/")!=-1) {
			track = path.substring(path.indexOf("/")+1);
			path = path.substring(0, path.indexOf("/"));
		}
		
		if(path.length() > 4 && path.substring(path.length()-4).equalsIgnoreCase(".sdp"))
			path = path.substring(0, path.length()-4);
		
		boolean notFound = false;
	/*	
		if(sess == null) {
			//sess = srv.openSession(path);							
			sess = srv.openSession("rtsp");							
			if(sess != null){
				Log.e(TAG,"Opening session "+sess.getID());
				String addr = sock.getInetAddress().getHostAddress();
				Log.e(TAG,"starting session with IP: " + addr);
				sess.start(addr);
			}
			else { 
				Log.e(TAG,"openSession returns Session not found! for "+ path);
				notFound = true;
			}
		}// else if(!sess.getID().equals(path)) {
			//notFound = true;
		//}
*/		
		String line;
		TreeMap<String, String> headers = new TreeMap<String, String>();
		while((line = r.readLine()) != null) {			
			line = line.trim();
			if(line.length()<1)
				break;
			
			int c = line.indexOf(':');
			String key = line.substring(0,c).toLowerCase();
			String value = line.substring(c+1).trim();
			
			headers.put(key, value);
		}
				
		if(notFound) {
			Log.e(TAG,"Session Not Found: sending 404 "+path+" - "+track);
			w.write("RTSP/1.0 404 Not Found\r\n\r\n");
			return;
		}
		
		TreeMap<String, String> responseHeaders = responseHeaders(headers);
		
		Log.e(TAG,"Responding "+method+", "+path);
		
		if(method.equals("DESCRIBE")) {
			String description = srv.getStreamDescription(baseDescription);
			if(!description.substring(description.length()-2).equals("\r\n"))
				description+="\r\n";
			
			Log.e(TAG,description.length()+" Moo! "+description.getBytes().length);
			Log.e(TAG,"setting Content-Base to /"+path+"/");
			
			responseHeaders.put("Content-Length", ""+description.length());
			responseHeaders.put("Content-Base", "/"+path+"/");
			responseHeaders.put("Content-Type", "application/sdp");
			
			w.write("RTSP/1.0 200 OK\r\n");
			writeHeaders(responseHeaders, w);
			w.write("\r\n");
			w.write(description);
			
			Log.e(TAG,"description: "+description);
		} else if(method.equals("OPTIONS")) {
			responseHeaders.put("Public", "DESCRIBE, SETUP, PLAY, OPTIONS, TEARDOWN");

			w.write("RTSP/1.0 200 OK\r\n");
			writeHeaders(responseHeaders, w);
			w.write("\r\n");
		} else if(method.equals("SETUP")) {
			if(headers.get("transport") == null) {
				w.write("RTSP/1.0 405 Not Allowed\r\n\r\n");
				return;
			}
			
			String transport = headers.get("transport").toLowerCase();
			
			if(transport.indexOf("client_port=")==-1) {
				w.write("RTSP/1.0 405 Not Allowed\r\n\r\n");
				return;
			}
			
			String clientPort = transport.substring(transport.indexOf("client_port=")+"client_port=".length());
			if(clientPort.indexOf(";") != -1)
				clientPort = clientPort.substring(0,clientPort.indexOf(";"));
			
			String[] s = clientPort.split("-");
			if(s.length!=2) {
				w.write("RTSP/1.0 405 Not Allowed\r\n\r\n");
				return;
			}
			
			int rtp, rtcp;
			try {
				rtp = Integer.parseInt(s[0]);
				rtcp = Integer.parseInt(s[1]);
			} catch(NumberFormatException e) {
				w.write("RTSP/1.0 405 Not Allowed\r\n\r\n");
				return;
			}
			
			if(rtp+1 != rtcp || rtp % 2 != 0)
				Log.e(TAG,"Non-standard RTP ports "+rtp+"-"+rtcp);
			
			if(rtp+1 != rtcp) {
				w.write("RTSP/1.0 405 Not Allowed\r\n\r\n");
				return;
			}	
			
			Log.e(TAG,"Got ports "+rtp+", "+rtcp+" on "+track);
			sess.setupPort(track,rtp);
		
			responseHeaders.put("Transport", transport+";server_port=10000-10001;source="+sock.getLocalAddress().getHostAddress());
			//responseHeaders.put("Transport", transport+";server_port=10000-10001;source="+IP);
			
			w.write("RTSP/1.0 200 OK\r\n");
			writeHeaders(responseHeaders, w);
			w.write("\r\n");
		
		} else if(method.equals("PLAY")) {
			Log.e(TAG,"Playing.");
			
			sess.play();
			
			responseHeaders.put("Range", "npt=now-");

			w.write("RTSP/1.0 200 OK\r\n");
			writeHeaders(responseHeaders, w);
			w.write("\r\n");
		} else if(method.equals("TEARDOWN")) {
			sess.stop();
			
			w.write("RTSP/1.0 200 OK\r\n");
			writeHeaders(responseHeaders, w);
			w.write("\r\n");				
		} else {
			w.write("RTSP/1.0 501 Not Implemented\r\n\r\n");
			Log.e(TAG,"Bad method "+method);
		}
		
		w.flush();
	}
}
