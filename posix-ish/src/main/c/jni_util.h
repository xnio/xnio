#ifndef JNI_UTIL_H
#define JNI_UTIL_H

#include "jni.h"

#define method(name) Java_org_jboss_xnio_posix_Posix_##name

#define method_buffer(name) Java_org_jboss_xnio_posix_Posix_##name##__ILjava_nio_ByteBuffer_2

#define method_buffer_offs_len(name) Java_org_jboss_xnio_posix_Posix_##name##__I_3Ljava_nio_ByteBuffer_2II

extern void init_jni(JNIEnv *env);

extern void throw_ioe(JNIEnv *env, const char *msg, int err);

#endif /* JNI_UTIL_H */
