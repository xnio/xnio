#include "jni.h"

#include "jni_util.h"
#include "buffers.h"

#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>

#include <stdio.h>

JNIEXPORT jlong JNICALL method_buffer(read)(JNIEnv *env, jclass clazz, jint fd, jobject buffer) {

    struct buffer_iovec_info bii;
    struct iovec iov;

    if (! init_iov(env, buffer, &bii, &iov)) {
        return -1L;
    }

    jlong ret = (jlong) read(fd, iov.iov_base, iov.iov_len);
    if (ret == -1) {
        // todo - throw
        free_iov(env, &bii, false);
        return -1L;
    }
    free_iov(env, &bii, true);
    return ret;
}

JNIEXPORT jlong JNICALL method_buffer_offs_len(read)(JNIEnv *env, jclass clazz, jint fd, jobjectArray bufferArray, jint offs, jint len) {

    struct buffer_iovec_info iovi[len];
    struct iovec iov[len];

    for (int i = 0; i < len; i ++) {
        jobject buffer = (*env)->GetObjectArrayElement(env, bufferArray, i + offs);
        if (! init_iov(env, buffer, iovi + i, iov + i)) {
            printf("Fail in init iov\n");
            // todo - unwind & throw
            return -1L;
        }
    }

    jlong ret = (jlong) readv(fd, iov, len);
    if (ret == -1L) {
        perror("readv");
    }
    // todo - throw if -1L
    for (int i = 0; i < len; i ++) {
        free_iov(env, iovi + i, ret != -1);
    }
    return ret;
}

/* EOF */
