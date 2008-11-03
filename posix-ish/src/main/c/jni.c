#include "jni.h"

#include "buffers.h"
#include "jni_util.h"

#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <string.h>
#include <stdio.h>
#include <errno.h>
#include <poll.h>

JNIEXPORT void JNICALL method(init)(JNIEnv *env, jclass clazz) {
    init_buffers(env);
    init_close(env); // last because it throws an exception
}

static void do_throw_msg(JNIEnv *env, const char *class_name, const char *msg, int err) {
    jclass class = (*env)->FindClass(env, class_name);
    int l = strlen(msg);
    char buf[256 + l];
    memcpy(buf, msg, l);
    buf[l] = ':';
    buf[l + 1] = ' ';
    strerror_r(err, buf + l + 2, 253);
    buf[l + 253] = 0;
    if (class) {
        (*env)->ThrowNew(env, class, buf);
    }
}

static void do_throw(JNIEnv *env, const char *class_name) {
    jclass class = (*env)->FindClass(env, class_name);
    if (class) {
        (*env)->ThrowNew(env, class, NULL);
    }
}

static const char *ClosedChannelException = "java/nio/channels/ClosedChannelException";
static const char *IOException = "java/io/IOException";
static const char *BindException = "java/net/BindException";
static const char *ConnectException = "java/net/ConnectException";
static const char *NoRouteToHostException = "java/net/NoRouteToHostException";
static const char *PortUnreachableException = "java/net/PortUnreachableException";

void throw_ioe(JNIEnv *env, const char *msg, int err) {
    switch (err) {
        case EBADF:
        case EPIPE:
            do_throw(env, ClosedChannelException);
            break;
        case EADDRINUSE:
        case EADDRNOTAVAIL:
            do_throw_msg(env, BindException, msg, errno);
            break;
        case ECONNABORTED:
        case EISCONN:
            do_throw_msg(env, ConnectException, msg, errno);
            break;
        case EHOSTUNREACH:
            do_throw_msg(env, NoRouteToHostException, msg, errno);
            break;
        case ECONNREFUSED:
            do_throw_msg(env, PortUnreachableException, msg, errno);
            break;
        case ECONNRESET:
            // todo: dedicated exception type for connection reset
            do_throw_msg(env, IOException, msg, errno);
            break;
        default:
            do_throw_msg(env, IOException, msg, errno);
            break;
    }
}

/* EOF */
