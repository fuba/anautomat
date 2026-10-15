// JNI is deliberately confined to byte arrays. Each array length comes from the
// VM; the Go entry point copies it before parsing. No addresses enter Starlark.
#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include "_cgo_export.h"

JNIEXPORT jbyteArray JNICALL
Java_dev_androiduse_StarlarkNative_execute(JNIEnv *env, jclass cls, jbyteArray request) {
    (void)cls;
    if (request == NULL) return NULL;
    jsize length = (*env)->GetArrayLength(env, request);
    if (length < 1 || length > 400000) return NULL;
    jbyte *bytes = (*env)->GetByteArrayElements(env, request, NULL);
    if (bytes == NULL) return NULL;
    char *output = executeJSON((char *)bytes, length);
    (*env)->ReleaseByteArrayElements(env, request, bytes, JNI_ABORT);
    if (output == NULL) return NULL;
    size_t count = strlen(output);
    if (count > 65536) { free(output); return NULL; }
    jbyteArray result = (*env)->NewByteArray(env, (jsize)count);
    if (result != NULL) (*env)->SetByteArrayRegion(env, result, 0, (jsize)count, (const jbyte *)output);
    free(output);
    return result;
}
