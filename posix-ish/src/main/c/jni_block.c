#include "jni.h"

#include "jni_util.h"
#include "buffers.h"

#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <signal.h>
#include <pthread.h>

#ifndef POLLRDHUP
#define POLLRDHUP 0
#endif

JNIEXPORT void JNICALL method(block)(JNIEnv *env, jclass clazz, jint fd, jboolean reads, jboolean writes) {
    sigset_t blocked, oldset;
    int res;
    sigemptyset(&blocked);
    sigaddset(&blocked, SIGIO);
    if ((res = pthread_sigmask(SIG_BLOCK, &blocked, &oldset))) {
        throw_ioe(env, "signal mask change failed", res);
        return;
    }
    sigdelset(&oldset, SIGIO);
    struct pollfd pfd = {
        .fd = fd,
        .events = (reads ? POLLIN | POLLRDHUP : 0) | (writes ? POLLOUT | POLLHUP | POLLERR : 0),
    };
    // todo - check interrupt status...
    if (ppoll(&pfd, 1, NULL, &oldset) == -1) {
        throw_ioe(env, "poll failed", errno);
    }
}

JNIEXPORT void JNICALL method(unblockThread)(JNIEnv *env, jclass clazz, jlong threadId) {
    pthread_t actual_id;
    memcpy(&actual_id, &threadId, sizeof(pthread_t));
    pthread_kill(actual_id, SIGIO);
}