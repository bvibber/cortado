/* Copyright (C) <2004> Wim Taymans <wim@fluendo.com>
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public
 * License along with this library; if not, write to the
 * Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 * Boston, MA 02111-1307, USA.
 */

package com.fluendo.jst;

public class Pad extends com.fluendo.jst.Object implements Runnable
{
  /* pad directions */
  public static final int UNKNOWN = 0;
  public static final int SRC = 1;
  public static final int SINK = 2;

  /* flow return values */
  public static final int OK = 0;
  public static final int NOT_LINKED = -1;
  public static final int WRONG_STATE = -2;
  public static final int UNEXPECTED = -3;
  public static final int NOT_NEGOTIATED = -4;
  public static final int ERROR = -5;
  public static final int NOT_SUPPORTED = -6;

  /* modes */
  public static final int MODE_NONE = 0;
  public static final int MODE_PUSH = 1;
  public static final int MODE_PULL = 2;

  protected Pad peer;
  protected int direction = UNKNOWN;
  protected boolean flushing;
  protected java.lang.Object streamLock =  new java.lang.Object();
  int mode;

  protected Caps caps;
  
  /* task stuff */
  private final int T_STOP = 0;
  private final int T_PAUSE = 1;
  private final int T_START = 2;
  private Thread thread;
  private int taskState;
  private java.lang.Object taskLock = new java.lang.Object();

  public boolean isFlowFatal (int ret) 
  {
    return ret <= UNEXPECTED;
  }
  public String getFlowName (int ret) {
    switch (ret) {
      case OK:
        return "ok";
      case NOT_LINKED:
        return "not-linked";
      case WRONG_STATE:
        return "wrong-state";
      case UNEXPECTED:
        return "unexpected";
      case NOT_NEGOTIATED:
        return "not-negotiated";
      case ERROR:
        return "error";
      case NOT_SUPPORTED:
        return "not-supported";
      default:
        return "unknown";
    }
  }

  public Pad(int direction) {
    this (direction, null);
  }
  public Pad(int direction, String name) {
    super(name);
    this.direction = direction;
  }

  public String toString () {
    return "Pad: "+parent.getName()+":"+getName()+" ["+super.toString()+"]";
  }

  public synchronized boolean link (Pad newPeer) {
    boolean res;

    /* already was connected */
    if (peer != null)
      return false;

    /* wrong direction */
    if (direction != SRC) 
      return false;

    synchronized (newPeer) {
      if (newPeer.direction != SINK)
	return false;

      /* peer was connected */
      if (newPeer.peer != null)
	return false;

      peer = newPeer;
      peer.peer = this;
    }
    return true;
  }

  public synchronized void unlink () {
    if (peer == null)
      return;

    if (direction == SRC) {
      peer.unlink ();
    }
    peer = null;
  }

  protected boolean eventFunc (Event event)
  {
    boolean result;

    switch (event.getType()) {
      case Event.FLUSH_START:
      case Event.FLUSH_STOP:
      case Event.EOS:
      case Event.SEEK:
      case Event.NEWSEGMENT:
      default:
        result = false;
        break;
    }
    return result;
  }

  public final boolean sendEvent (Event event) {
    boolean result;

    switch (event.getType()) {
      case Event.FLUSH_START:
        setFlushing (true);
        result = eventFunc (event);
        break;
      case Event.FLUSH_STOP:
        setFlushing (false);
        result = eventFunc (event);
        break;
      case Event.NEWSEGMENT:
      case Event.EOS:
        synchronized (streamLock) {
	  result = eventFunc (event);
	}
        break;
      case Event.SEEK:
        result = eventFunc (event);
        break;
      default:
        result = false;
        break;
    }
    return result;
  }

  public synchronized Caps getCaps () {
    return this.caps;
  }

  protected boolean setCapsFunc (Caps caps) {
    return true;
  }

  public boolean setCaps (Caps caps) {
    boolean res;
    res = setCapsFunc (caps);
    if (res) {
      this.caps = caps;
    }
    return res;
  }

  private final int chain (Buffer buffer) {
    synchronized (streamLock) {
      synchronized (this) {
        if (flushing) 
	  return WRONG_STATE;

	if (buffer.caps != null && buffer.caps != caps) {
	  if (!setCaps(buffer.caps)) {
	    buffer.free();
	    return NOT_NEGOTIATED;
	  }
	}
      }
      int res = chainFunc(buffer); 
      return res;
    }
  }

  protected int chainFunc (Buffer buffer)
  {
    return ERROR;
  }

  public final int push (Buffer buffer) {
    if (peer == null) {
      System.out.println ("no peer");
      return ERROR;
    }

    return peer.chain (buffer);
  }

  public final boolean pushEvent (Event event) {
    if (peer == null)
      return false;

    return peer.sendEvent (event);
  }

  public synchronized void setFlushing (boolean flush) {
    flushing = flush;
  }
  public synchronized boolean isFlushing () {
    return flushing;
  }

  protected boolean activateFunc (int mode)
  {
    return true;
  }

  public final boolean activate (int newMode)
  {
    boolean res;

    if (mode == newMode)
      return true;

    if (newMode == MODE_NONE) {
      setFlushing (true);
    }
    if ((res = activateFunc (newMode)) == false)
      return false;

    if (newMode != MODE_NONE) {
      setFlushing (false);
    }
    else {
      synchronized (streamLock) {
      }
    }
    mode = newMode;

    return res;
  }

  protected void taskFunc()
  {
  }

  public void run() {
    synchronized (streamLock) {
      while (taskState != T_STOP) {
        while (taskState == T_PAUSE) {
	  try {
	    streamLock.wait();
	  }
	  catch (InterruptedException ie) {}
	}
        if (taskState == T_STOP) 
	  break;

        taskFunc();
      }
    }
  }

  protected boolean startTask()
  {
    synchronized (streamLock) {
      taskState = T_START;
      if (thread == null) {
        thread = new Thread(this);
        thread.start();
      }
      streamLock.notify();
    }
    return true;
  }
  protected boolean pauseTask()
  {
    taskState = T_PAUSE;
    return true;
  }

  protected boolean stopTask()
  {
    taskState = T_STOP;
    synchronized (streamLock) {
      streamLock.notify();
    }
    try {
      thread.join();
    }
    catch (InterruptedException ie) {}
    thread = null;

    return true;
  }
}
