#include "jni.h"

#include "jni_util.h"
#include "buffers.h"

#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <errno.h>
#include <stdio.h>

JNIEXPORT jlong JNICALL method(write)(JNIEnv *env, jclass clazz, jint fd, jobject buffer) {

    struct buffer_iovec_info bii;
    struct iovec iov;

    if (! init_iov(env, buffer, &bii, &iov)) {
        return -1L;
    }

    jlong ret = (jlong) read(fd, iov.iov_base, iov.iov_len);
    if (ret == -1) {
        int err = errno;
        free_iov(env, &bii, false);
        throw_ioe(env, "write() failed", err);
        return -1L;
    }
    free_iov(env, &bii, true);
    return ret;
}

JNIEXPORT jlong JNICALL method(writev)(JNIEnv *env, jclass clazz, jint fd, jobjectArray bufferArray, jint offs, jint len) {

    struct buffer_iovec_info iovi[len];
    struct iovec iov[len];

    for (int i = 0; i < len; i ++) {
        jobject buffer = (*env)->GetObjectArrayElement(env, bufferArray, i + offs);
        if (! init_iov(env, buffer, iovi + i, iov + i)) {
            int err = errno;
            throw_ioe(env, "write() failed", err);
            // todo - unwind with frees
            return -1L;
        }
    }

    jlong ret = (jlong) writev(fd, iov, len);
    for (int i = 0; i < len; i ++) {
        free_iov(env, iovi + i, false);
    }
    if (ret == -1L) {
        throw_ioe(env, "write() failed", errno);
    }
    return ret;
}

/* EOF */
