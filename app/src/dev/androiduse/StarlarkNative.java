package dev.androiduse;

/** Loaded exclusively by the disposable sandbox service. UTF-8 JSON is the ABI. */
final class StarlarkNative {
 static {System.loadLibrary("starlark");}
 static native byte[] execute(byte[] request);
 static void ensureLoaded(){}
 private StarlarkNative(){}
}
