#ifndef BUFFERS_H
#define BUFFERS_H

#include "jni.h"

#include <sys/uio.h>

#include <stdbool.h>

struct buffer_iovec_info {
    jobject obj;
    jbyte *bytes;
    jboolean copied;
};

/*
 * One-time initialization.
 */
extern void init_buffers(JNIEnv *env);

/*
 * Initialize an iovec structure for a ByteBuffer.  If the buffer is a heap buffer, "obj" will be
 * initialized in order to facilitate the release of the byte array; otherwise the "obj" field of "iovec_info" will
 * be set to NULL.  The given iovec will be initialized with the proper pointer and length information.
 *
 * Returns "true" on success or "false" if an exception is thrown.
 */
extern bool init_iov(JNIEnv *env, jobject buffer, struct buffer_iovec_info *iovec_info, struct iovec *iov);

extern void free_iov(JNIEnv *env, struct buffer_iovec_info *iovec_info, bool commit);

#endif /* BUFFERS_H */
