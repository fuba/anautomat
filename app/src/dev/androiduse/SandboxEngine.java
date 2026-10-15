package dev.androiduse;

/** Shared limits; the interpreter is loaded only in the isolated service. */
final class SandboxEngine {
 static final int MAX_CODE=32768,MAX_INPUT=16384,MAX_OBSERVATION=131072,MAX_STATE=8192,MAX_OUTPUT=65536,MAX_LOG=8192;
 static final long MAX_MILLIS=2000,MAX_START_MILLIS=8000;
 private SandboxEngine(){}
}
