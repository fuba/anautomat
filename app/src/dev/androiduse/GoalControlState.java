package dev.androiduse;

/** A single transport state shared by both goal composers. */
final class GoalControlState {
 enum Mode { IDLE, RUNNING, PAUSED, LIMIT }
 static Mode mode(boolean running,boolean paused,boolean limit){return running?Mode.RUNNING:paused?Mode.PAUSED:limit?Mode.LIMIT:Mode.IDLE;}
 private GoalControlState(){}
}
