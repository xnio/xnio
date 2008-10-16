#include "jni.h"

#include <sys/uio.h>
#include <stdio.h>
#include "buffers.h"

static jmethodID mid_ByteBuffer_array;
static jmethodID mid_ByteBuffer_arrayOffset;
static jmethodID mid_ByteBuffer_position;
static jmethodID mid_ByteBuffer_remaining;

// TODO: do we need to hold a monitor for this?
void init_buffers(JNIEnv *env) {
    jclass cl_ByteBuffer;
    cl_ByteBuffer = (*env)->FindClass(env, "java/nio/ByteBuffer");
    mid_ByteBuffer_array = (*env)->GetMethodID(env, cl_ByteBuffer, "array", "()[B");
    mid_ByteBuffer_arrayOffset = (*env)->GetMethodID(env, cl_ByteBuffer, "arrayOffset", "()I");
    mid_ByteBuffer_position = (*env)->GetMethodID(env, cl_ByteBuffer, "position", "()I");
    mid_ByteBuffer_remaining = (*env)->GetMethodID(env, cl_ByteBuffer, "remaining", "()I");
}

bool init_iov(JNIEnv *env, jobject buffer, struct buffer_iovec_info *iovec_info, struct iovec *iov) {
    void *bufptr;
    jint len;
    if ((bufptr = (*env)->GetDirectBufferAddress(env, buffer)) == NULL) {
        // heap buffer

        jobject array;
        jint offset;
        jboolean iscopy;
        jbyte *bytes;

        array = (*env)->CallObjectMethod(env, buffer, mid_ByteBuffer_array);
        if (array == NULL) {
            // exception thrown...
            return false;
        }
        offset = (*env)->CallIntMethod(env, buffer, mid_ByteBuffer_arrayOffset) + (*env)->CallIntMethod(env, buffer, mid_ByteBuffer_position);
        len = (*env)->CallIntMethod(env, buffer, mid_ByteBuffer_remaining);
        bytes = (*env)->GetByteArrayElements(env, array, &iscopy);
        iovec_info->obj = array;
        iovec_info->bytes = bytes;
        iovec_info->copied = iscopy;
        bufptr = bytes + offset;
    } else {
        // direct buffer
        jint offset;

        offset = (*env)->CallIntMethod(env, buffer, mid_ByteBuffer_position);
        len = (*env)->CallIntMethod(env, buffer, mid_ByteBuffer_remaining);
        bufptr += offset;
        iovec_info->obj = NULL;
    }
    iov->iov_base = bufptr;
    iov->iov_len = len;
    return true;
}

void free_iov(JNIEnv *env, struct buffer_iovec_info *iovec_info, bool commit) {
    if (iovec_info->obj) {
        (*env)->ReleaseByteArrayElements(env, iovec_info->obj, iovec_info->bytes, commit ? 0 : iovec_info->copied ? JNI_ABORT : 0);
    }
}



/* EOF */
