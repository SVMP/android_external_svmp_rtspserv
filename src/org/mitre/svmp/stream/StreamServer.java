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

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.TreeMap;

import android.util.Log;


public class StreamServer {
	private static final String TAG = "RTSP-StreamServer";
	private TreeMap<String, Session> sessions = new TreeMap<String, Session>();
	private ServerSocket rtspSocket;
	
	private String streamDescription = "";

	
	public StreamServer() throws IOException {
		addSession();	
		
		rtspSocket = new ServerSocket(5544);
	}
	
	public String getStreamDescription(String baseSDP) {
		return (baseSDP+streamDescription).replace("\r\n","\n").replace("\n", "\r\n");
	}
	
	private void addSession() {
		String str = "rtsp";
		Log.e(TAG,"adding new session : rtsp");
		Session sess = new Session(this,str);
		sessions.put(str, sess);
	}
	
	
	public void listen() throws IOException {
		Socket s;
		while((s = rtspSocket.accept()) != null) {
			new RTSPThread(s,this).start();
		}
	}
	
	public static void main(String[] args) {
		try {
			new StreamServer().listen();
		} catch(IOException e) {
			System.err.println("StreamServer crashed");
			e.printStackTrace();
			System.exit(1);
		}
	}

	public void removeSession(Session session) {
		sessions.put(session.getID(), null);
	}

	public Session openSession(String id) {
		Session sess = sessions.get(id);
		
				
		if(sess == null)
			return null;
		
		this.streamDescription=sess.getSDP();
		
		if(!sess.validate()){
			Log.e(TAG,"Validation error!");
			addSession();
		}
		
		Log.e(TAG,"Session found!");
			
		return sess;
	}
}
