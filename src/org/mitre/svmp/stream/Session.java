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

import android.util.Log;

public class Session {
	String id, clientIP;
	StreamServer ss;
	long time;
	Process p = null;

	private native int InitUnixSockClient(String path);
	private native int InitTCPSockClient(String path, String port);
	private native int SockClientWrite(int fd,RTSPEventMessage event);	
	private native int SockClientClose(int fd);	

	private int sockfd;
	private static final String TAG = "RTSP-SESSION";
	private String SDP;	

	private String fbstream_host = "192.168.100.50";
	private String fbstream_port = "4321";
	//private String fb_graphics_device = "/dev/graphics/fb0";
	//private String fb_audio_device = "/system/audio_loop";
	private String fb_graphics_device = "/tmp/svmp.graphics_fb.shm";
	private String fb_audio_device = "/tmp/svmp.audio_loop";
			
	
	public String getSDP() {	    
		    if (SDP == null)
		    	retrieveSDP();
		    Log.e(TAG,"SDP received: "+ SDP);
			return SDP;		
	}
	int audio=0, video=0;
	
	boolean started = false;

	static {
		System.loadLibrary("rtsp_jni");
	}

	
	public Session(StreamServer ss, String id) {
		this.id = id;
		this.ss = ss;
		this.sockfd = 0;
		this.SDP=null;
		retrieveSDP();
		time = System.currentTimeMillis();
	}

	// make sure this Session hasn't timed out; remove it if it has
	public boolean validate() {
		if(started)
			return true;
		
	/*
		if(Math.abs(time - System.currentTimeMillis()) > 30 * 1000) {	// more than thirty seconds old 
			ss.removeSession(this);
			return false;
		}
	*/
		return true;
	}

	public String path() {
		return id+".sdp";
	}

	public String getID() {
		return id;
	}

	public void start(String clientIP) {
		started = true;
		this.clientIP = clientIP;
				
	}
	
	public void stop() {		
		Log.e(TAG,"stopping fbstream with: ip: " + clientIP + " video: " +video +" audio: " + audio );
		RTSPEventMessage evt = new RTSPEventMessage();
		evt.cmd = RTSPEventMessage.STOP;
		SockClientWrite(this.sockfd,evt);
		SockClientClose(this.sockfd);
		this.sockfd=0;
	}
	
	public void setupPort(String track, int port) {
		if(track.equals("trackID=97")) { // audio
			audio = port;
		} else if(track.equals("trackID=96")) { // video
			video = port;
		} else {
			System.err.println("Track provided for setup not known! '"+track+"'");
		}
	}

	public void play() {
		if (this.sockfd == 0)
			//this.sockfd = InitUnixSockClient("/dev/socket/rtsp_command");
			this.sockfd = InitTCPSockClient(fbstream_host, fbstream_port);

		Log.e(TAG,"starting fbstream with: ip: " + clientIP + " video: " +video +" audio: " + audio );
		RTSPEventMessage evt = new RTSPEventMessage();
		evt.cmd = RTSPEventMessage.START;
		evt.Gdev = fb_graphics_device;
		evt.Adev = fb_audio_device;
		evt.IP = clientIP;
		evt.vidport = video;
		evt.audport = audio;
		SockClientWrite(sockfd,evt);
	}
	
	public void retrieveSDP() {
		if (this.sockfd == 0)
			//this.sockfd = InitUnixSockClient("/dev/socket/rtsp_command");
			this.sockfd = InitTCPSockClient(fbstream_host, fbstream_port);
		Log.e(TAG,"sockfd =" +this.sockfd);

		Log.e(TAG,"getting SDP from fbstream");
		RTSPEventMessage evt = new RTSPEventMessage();
		evt.cmd = RTSPEventMessage.PLAYSDP;
		// SDP is read in JNI function
		SockClientWrite(sockfd,evt); 		 
		SDP=evt.SDP;		
	}
}
