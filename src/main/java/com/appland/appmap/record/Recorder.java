package com.appland.appmap.record;

import com.appland.appmap.output.v1.CodeObject;
import com.appland.appmap.output.v1.Event;
import com.appland.appmap.record.RecordingSession.Metadata;
import com.appland.appmap.util.Logger;

import java.io.IOException;
import java.util.Stack;

/**
 * Recorder is a singleton responsible for managing recording sessions and routing events to any
 * active session. It also maintains a code object tree containing every known package/class/method.
 */
public class Recorder {
  private static final String ERROR_SESSION_PRESENT = "an active recording session already exists";
  private static final String ERROR_NO_SESSION = "there is no active recording session";

  private static final Recorder instance = new Recorder();

  private final ActiveSession activeSession = new ActiveSession();
  private final CodeObjectTree globalCodeObjects = new CodeObjectTree();
  private final ThreadLocal<ThreadState> threadState = ThreadLocal.withInitial(ThreadState::new);

  /**
   * Keep track of what's going on in the current thread.
   */
  static class ThreadState {
    // Provides the last event on the current thread, which is used in some cases to
    // update the event post facto.
    Event lastEvent;
    // Avoid accepting new events on a thread that's already processing an event.
    boolean isProcessing;
    Stack<Event> callStack = new Stack<>();
  }

  static class ActiveSession {
    private RecordingSession activeSession = null;

    synchronized RecordingSession get() throws ActiveSessionException {
      if (activeSession == null) {
        throw new ActiveSessionException(ERROR_NO_SESSION);
      }

      return activeSession;
    }

    boolean exists() {
      return activeSession != null;
    }

    synchronized RecordingSession release() throws ActiveSessionException {
      if (activeSession == null) {
        throw new ActiveSessionException(ERROR_NO_SESSION);
      }

      RecordingSession result = activeSession;
      activeSession = null;
      return result;
    }

    synchronized void set(RecordingSession session) throws ActiveSessionException {
      if (activeSession != null) {
        throw new ActiveSessionException(ERROR_SESSION_PRESENT);
      }

      activeSession = session;
    }
  }

  /**
   * Get the global Recorder instance.
   *
   * @return The global recorder instance
   */
  public static Recorder getInstance() {
    return Recorder.instance;
  }

  private Recorder() {
  }

  /**
   * Start a recording session.
   *
   * @param metadata Recording metadata to be written
   * @throws ActiveSessionException If a session is already in progress
   */
  public void start(Metadata metadata) throws ActiveSessionException {
    RecordingSession session = new RecordingSession();
    activeSession.set(session);
    session.start(metadata);
  }

  public boolean hasActiveSession() {
    return activeSession.exists();
  }

  public Recording checkpoint() {
    return activeSession.get().checkpoint();
  }

  /**
   * Stops the active recording session and obtains the result.
   *
   * @return Recording of the current session.
   * @throws ActiveSessionException If no recording session is in progress or the session cannot be
   *                                stopped.
   */
  public Recording stop() throws ActiveSessionException {
    return activeSession.release().stop();
  }

  /**
   * Record an {@link Event} to the active session.
   *
   * @param event The event to be recorded.
   */
  public void add(Event event) {
    if (!activeSession.exists()) {
      return;
    }

    ThreadState ts = threadState.get();

    // We don't want re-entrant events on the same thread.
    if ( ts.isProcessing ) {
      return;
    }

    ts.isProcessing = true;
    try {
      if ( event.event.equals("call") ) {
        ts.callStack.push(event);
      } else if ( event.event.equals("return") ) {
        if ( ts.callStack.isEmpty() ) {
          Logger.println("Discarding 'return' event because the call stack is empty for this thread");
          return;
        }

        // To whom it may concern:
        //
        // You may be tempted to try and track the caller Event using a local variable in the
        // generated code for each hooked function. It would be cleaner and more reliable than
        // tracking a call stack here. However, due to issues with Javassist and the JVM,
        // I (KEG) was not able to find a way to declare, initialize, set, and pass an Event that would
        // work with exception handling and finally clauses.
        Event caller = ts.callStack.pop();
        event.parentId = caller.id;
        event.threadId = caller.threadId;
      } else {
        throw new IllegalArgumentException("Event should be 'call' or 'return', got " + event.event);
      }

      // This is the line that can generate re-entrant events, apparently.
      event.freeze();

      ts.lastEvent = event;

      activeSession.get().add(event);
    } finally {
      ts.isProcessing = false;
    }
  }

  /**
   * Register a {@link CodeObject}, allowing it to propagate to an output's Class Map if referenced
   * in an event.
   *
   * @param codeObject The code object to be registered
   */
  public void registerCodeObject(CodeObject codeObject) {
    synchronized (globalCodeObjects) {
      globalCodeObjects.add(codeObject);
    }
  }

  public CodeObjectTree getRegisteredObjects() {
    return this.globalCodeObjects;
  }

  /**
   * Gets the last call event for this thread.
   */
  public Event getLastCallEvent() {
    ThreadState ts = threadState.get();
    return ts.callStack.isEmpty() ? null : ts.callStack.peek();
  }

  /**
   * Retrieve the last event (of any kind) recorded for this thread.
   */
  public Event getLastEvent() {
    return threadState.get().lastEvent;
  }

  /**
   * Record the execution of a Runnable and return the scenario data as a String
   */
  public Recording record(Runnable fn) throws ActiveSessionException {
    this.start(new Metadata());
    fn.run();
    return this.stop();
  }

  /**
   * Record the execution of a Runnable and write the scenario to a file
   */
  public void record(String name, Runnable fn) throws ActiveSessionException, IOException {
    final String fileName = name.replaceAll("[^a-zA-Z0-9-_]", "_");
    final Metadata metadata = new Metadata();
    metadata.scenarioName = name;

    this.start(metadata);
    fn.run();
    Recording recording = this.stop();
    recording.moveTo(fileName + ".appmap.json");
  }
}
