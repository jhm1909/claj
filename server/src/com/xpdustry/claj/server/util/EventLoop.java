package com.xpdustry.claj.server.util;

import arc.util.TaskQueue;


/** Simple threaded event loop system. */
public class EventLoop {
  protected final TaskQueue queue = new TaskQueue();
  protected Thread thread;

  public EventLoop() {
    this(false);
  }
  
  public EventLoop(boolean daemon) {
    thread = new Thread(this::mainLoop, "EventLoop");
    thread.setDaemon(daemon);
  }

  protected synchronized void mainLoop() {
    try {
      while (!thread.isInterrupted()) {
        wait();
        run();
      } 
    } catch (InterruptedException ignored) {}
  }
  
  /** Runs all awaiting tasks. */
  public void run() {
    queue.run();
  }
  
  /** Post a task in the event loop. */
  public synchronized void post(Runnable task) {
    if (thread.isInterrupted()) throw new IllegalStateException("event loop is has been stopped.");
    if (!thread.isAlive()) throw new IllegalStateException("event loop not started; please use #start() before.");
    
    queue.post(task);
    notify();
  }
  
  /** Starts the event loop thread. */
  public void start() {
    thread.start();
  }
  
  /** @return if event loop is running or not */
  public boolean running() {
    return thread.isAlive();
  }
  
  public void stop() {
    stop(false);
  }
  
  /** 
   * Requests the event loop to stop. <br>
   * Remaining tasks may not be runs after that.
   * @param wait waits for running tasks to be finished
   */
  public void stop(boolean wait) {
    thread.interrupt();
    if (wait) {
      try { thread.join(); } 
      catch (InterruptedException ignored) {}
    }
  }
}
