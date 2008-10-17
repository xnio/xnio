#include "jni.h"

#include "jni_util.h"

#include <sys/types.h>
#include <sys/socket.h>
#include <errno.h>
#include <unistd.h>

static int pcfd;

void init_close(JNIEnv *env) {
    int fds[2];
    if (socketpair(AF_UNIX, SOCK_STREAM, 0, fds) == -1) {
        throw_ioe(env, "Error initializing XNIO pre-close file descriptor (socketpair)", errno);
        return;
    }
    if (close(fds[1]) == -1) {
        // unlikely to work, but we need to avoid a leak
        close(fds[0]);
        throw_ioe(env, "Error initializing XNIO pre-close file descriptor (close)", errno);
        return;
    }
    pcfd = fds[0];
}


JNIEXPORT void JNICALL method(preClose)(JNIEnv *env, jclass clazz, jint fd) {
    if (dup2(pcfd, fd) == -1) {
        throw_ioe(env, "preClose failed on dup2", errno);
        return;
    }
}

JNIEXPORT void JNICALL method(close)(JNIEnv *env, jclass clazz, jint fd) {
    if (close(fd) == -1) {
        throw_ioe(env, "close failed", errno);
        return;
    }
}



/* EOF */
