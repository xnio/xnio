#include "jni.h"

#include "buffers.h"
#include "jni_util.h"

#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <string.h>

JNIEXPORT void JNICALL method(init)(JNIEnv *env, jclass clazz) {
    init_buffers(env);
}

JNIEXPORT jlong JNICALL method(test)(JNIEnv *env, jclass clazz, jobject buffer) {

    struct buffer_iovec_info bii;
    struct iovec iov;

    if (! init_iov(env, buffer, &bii, &iov)) {
        return -1;
    }
    int fd = open("/tmp/test.txt", O_RDONLY);
    if (fd == -1) {
        perror("open");
        free_iov(env, &bii, false);
        return -1L;
    }
    jlong ret = (jlong) read(fd, iov.iov_base, iov.iov_len);
    if (ret == -1) {
        perror("read");
        free_iov(env, &bii, false);
        close(fd);
        return -1L;
    }
    close(fd);
    free_iov(env, &bii, true);
    return ret;
}

static jclass class_IOException;

void init_jni(JNIEnv *env) {
    class_IOException = (*env)->FindClass(env, "java/io/IOException");
}

void throw_ioe(JNIEnv *env, const char *msg, int err) {
    int l = strlen(msg);
    char buf[256 + l];
    memcpy(buf, msg, l);
    strerror_r(err, buf + l, 255);
    buf[l + 255] = 0;
    (*env)->ThrowNew(env, class_IOException, msg);
}


/* EOF */
