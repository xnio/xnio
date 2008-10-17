#ifndef JNI_UTIL_H
#define JNI_UTIL_H

#include "jni.h"

#define method(name) Java_org_jboss_xnio_posix_Posix_##name

extern void init_jni(JNIEnv *env);

extern void throw_ioe(JNIEnv *env, const char *msg, int err);

extern void init_close(JNIEnv *env);

#endif /* JNI_UTIL_H */
