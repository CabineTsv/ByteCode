#pragma once
#include <stdint.h>
typedef int32_t jint; typedef int64_t jlong; typedef int32_t jsize; typedef uint16_t jchar; typedef uint8_t jboolean;
class _jobject {}; typedef _jobject* jobject; typedef jobject jclass; typedef jobject jstring;
struct _jmethodID; typedef _jmethodID* jmethodID;
struct JNIEnv {
    jsize GetStringLength(jstring); void GetStringRegion(jstring, jsize, jsize, jchar*);
    jstring NewString(const jchar*, jsize); jclass GetObjectClass(jobject);
    jmethodID GetMethodID(jclass, const char*, const char*); void CallVoidMethod(jobject, jmethodID, ...);
    jboolean ExceptionCheck(); void ExceptionClear(); void DeleteLocalRef(jobject);
};
#define JNIEXPORT __attribute__((visibility("default")))
#define JNICALL
