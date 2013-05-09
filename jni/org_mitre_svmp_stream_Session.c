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
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <sys/time.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <netinet/in.h>
#include <netdb.h>
#include <unistd.h>
#include <errno.h>
#define LOG_TAG "svmp_rtsp_jni"
#include <utils/Log.h>
#include "org_mitre_svmp_stream_Session.h"

struct svmp_fbstream_event_t {
	int cmd;
	long sessid; /* future use */
};
 
/* sent to initialize a new stream */
struct svmp_fbstream_init_t {
	char Gdev[20];
	char Adev[20];
	char IP[16];
	int vidport;
	int audport;
};

#define START 1
#define PLAY  2
#define PAUSE 3
#define STOP  4
#define PRINTSDP 5


/*
 *
 *12/05/2012
 *This is the UNIX socket JNI code. We connect to an existing UNIX socket that was initialized in init.rc
 *
 */

jint Java_org_mitre_svmp_stream_Session_InitUnixSockClient( JNIEnv* env, jobject thiz,jstring jpath)
{
	int clifd, i,n;
	struct sockaddr_un addr;
	struct sockaddr_un  cli_addr, serv_addr;
	socklen_t servlen;
	const char *path=(*env)->GetStringUTFChars( env, jpath , NULL );
	
	bzero((char *) &serv_addr, sizeof(serv_addr));
	serv_addr.sun_family = AF_UNIX;
 	// pass the path in as an argument
	strcpy(serv_addr.sun_path, path);
	servlen=strlen(serv_addr.sun_path) + 
		                      sizeof(serv_addr.sun_family);

	if ((clifd = socket(AF_UNIX,SOCK_STREAM,0)) < 0){
		LOGD("error opening socket :%s\n", strerror(errno));
		return -1;
	}

	if (connect(clifd, (struct sockaddr *) &serv_addr, servlen) < 0) {
		LOGD("error with connect():%s\n", strerror(errno));
		return -1;
        }
	(*env)->ReleaseStringUTFChars(env, jpath, path);
	LOGD("clifd is %d\n",clifd);

	return clifd;
}

jint Java_org_mitre_svmp_stream_Session_InitTCPSockClient( JNIEnv* env, jobject thiz,jstring jpath, jstring jport)
{
	int clifd, i,n,s;
	struct addrinfo hints;
	struct addrinfo *result, *rp;
	struct sockaddr_un  cli_addr, serv_addr;

	const char *host=(*env)->GetStringUTFChars( env, jpath , NULL );
	const char *port=(*env)->GetStringUTFChars( env, jport , NULL );
	
	bzero((char *) &hints, sizeof(hints));
	hints.ai_family = AF_INET;
	hints.ai_socktype = SOCK_STREAM;
	hints.ai_flags = 0;
	hints.ai_protocol = 0;

	s = getaddrinfo(host, port, &hints, &result);
	if (s != 0) {
		LOGD("getaddrinfo: %s\n", gai_strerror(s));
		return -1;
	}

	for (rp = result; rp != NULL; rp = rp->ai_next) {
		clifd = socket(rp->ai_family, rp->ai_socktype, rp->ai_protocol);
		if (clifd < 0) {
			LOGD("error opening socket: %s\n", strerror(errno));
			continue;
		}
		if (connect(clifd, rp->ai_addr, rp->ai_addrlen) < 0)
			LOGD("error with connect(): %s\n", strerror(errno));
		else
			break;

		close(clifd);
	}

	if (rp == NULL) {
		LOGD("Could not connect to %s:%s\n", host, port);
		return -1;
	}

	(*env)->ReleaseStringUTFChars(env, jpath, host);
	(*env)->ReleaseStringUTFChars(env, jport, port);
	LOGD("clifd is %d\n",clifd);

	freeaddrinfo(result);

	return clifd;
}

/*
 * Class:     org_mitre_svmp_stream_Session
 * Method:    SockClientWrite
 * Signature: (ILorg/mitre/svmp/protocol/SVMPSensorEventMessage;)I
 */
JNIEXPORT jint JNICALL Java_org_mitre_svmp_stream_Session_SockClientWrite
  (JNIEnv* env, jobject thiz, jint fd, jobject EventObj) 
{
    int bytes=0;
    int CMD;
	int ssize = sizeof(struct svmp_fbstream_event_t);
	struct svmp_fbstream_event_t *evt = (struct svmp_fbstream_event_t *)malloc(ssize);
	jfieldID fid;
	jstring jstr;
	const char *str;
	jclass EventClass = (*env)->GetObjectClass(env, EventObj);
	if (EventClass == NULL){
	         LOGD("Class not found!\n");
	         return -1;
	} 

	// cmd
	fid = (*env)->GetFieldID(env, EventClass, "cmd", "I");
	if (fid == NULL ) {
		LOGD("get cmd error!\n");
	}
	evt->cmd = (*env)->GetIntField(env, EventObj, fid);

	
	// sessid 
	fid = (*env)->GetFieldID(env, EventClass, "sessid", "J");
	evt->sessid = (*env)->GetLongField(env, EventObj, fid);
	// Adev
	//LOGD("Sending: type:%d,accuracy: %d,timestamp:%ld,value[0]%d,value[1]%d,value[2]%d\n",evt->type,evt->accuracy, evt->timestamp,evt->value[0],evt->value[1],evt->value[2]);

	// finally write to socket..
	int err = write(fd,(char*)evt,ssize);
	if ( err < 1) 
		LOGD("error with write,():%s, fd is %d\n", strerror(errno),fd);
	LOGD("wrote  %d bytes on socket, fd %d\n",err, fd );

	CMD=evt->cmd;
	free(evt);
	/*check to see if we need to send an init message */
	if (CMD == START) {
		int ssize = sizeof(struct svmp_fbstream_init_t);
		struct svmp_fbstream_init_t *fbinit = (struct svmp_fbstream_init_t *)malloc(ssize);
		// Gdev
		fid = (*env)->GetFieldID(env, EventClass, "Gdev",
				"Ljava/lang/String;");
		jstr = (*env)->GetObjectField(env, EventObj, fid);
		str = (*env)->GetStringUTFChars(env, jstr, NULL);
		strncpy(fbinit->Gdev,str,sizeof(fbinit->Gdev));
		(*env)->ReleaseStringUTFChars(env, jstr, str);
		// Adev
		fid = (*env)->GetFieldID(env, EventClass, "Adev",
				"Ljava/lang/String;");
		jstr = (*env)->GetObjectField(env, EventObj, fid);
		str = (*env)->GetStringUTFChars(env, jstr, NULL);
		strncpy(fbinit->Adev,str,sizeof(fbinit->Adev));
		(*env)->ReleaseStringUTFChars(env, jstr, str);
		// IP
		fid = (*env)->GetFieldID(env, EventClass, "IP",
				"Ljava/lang/String;");
		jstr = (*env)->GetObjectField(env, EventObj, fid);
		str = (*env)->GetStringUTFChars(env, jstr, NULL);
		strncpy(fbinit->IP,str,sizeof(fbinit->IP));
		(*env)->ReleaseStringUTFChars(env, jstr, str);
		// vidport
		fid = (*env)->GetFieldID(env, EventClass, "vidport", "I");
		fbinit->vidport = (*env)->GetIntField(env, EventObj, fid);
		// audport
		fid = (*env)->GetFieldID(env, EventClass, "audport", "I");
		fbinit->audport = (*env)->GetIntField(env, EventObj, fid);

		// finally write to socket..
		int err = write(fd,(char*)fbinit,ssize);
		if ( err < 1) 
			LOGD("error with write,():%s, fd is %d\n", strerror(errno),fd);
		LOGD("wrote  %d bytes on socket, fd %d\n",err, fd );
		free(fbinit);
	}else if (CMD == PRINTSDP) {
		// read results from Socket and place in evt->SDP 
		int ssize;
		char *buf;
		// block until response
		// read size of the string
		int err = read(fd,&ssize,sizeof(int));
		LOGD("sizeof SDP is %d\n", ssize);
		buf = (char *)malloc(2*ssize);
		err = 0;
		do {
			err += read(fd,(char*)buf+err,ssize);
		}while(err < ssize);
		LOGD("received size of %d, strlen of %d\n", err,strlen(buf));

		//err += read(fd,(char*)buf,ssize);
		// copy to 
		/* Look for the instance field s in cls */
		fid = (*env)->GetFieldID(env, EventClass, "SDP",
				"Ljava/lang/String;");
		// make sure string is null terminated
		buf[ssize]='\0';
		jstr = (*env)->NewStringUTF(env, buf);
		LOGD("SDP is %s\n", buf);
		(*env)->SetObjectField(env, EventObj, fid, jstr);
		free(buf);
	}

	return bytes;
}
/* Close connection */
jint Java_org_mitre_svmp_stream_Session_SockClientClose( JNIEnv* env,
						jobject thiz, jint fd )
{
	return close (fd);
}
