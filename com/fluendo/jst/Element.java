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

import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;
import com.fluendo.plugin.*;
import com.fluendo.utils.*;

public abstract class Element extends com.fluendo.jst.Object
{
  public static final int FLAG_IS_SINK = (com.fluendo.jst.Object.FLAG_LAST << 1);
  public static final int FLAG_LAST    = (com.fluendo.jst.Object.FLAG_LAST << 16);

  protected Vector pads = new Vector();
  protected java.lang.Object stateLock =  new java.lang.Object();
  private Vector padListeners = new Vector();

  protected Clock clock;
  protected Bus bus;
  protected long baseTime;

  /* states */
  public static final int NONE = 0;
  public static final int STOP = 1;
  public static final int PAUSE = 2;
  public static final int PLAY = 3;

  /* transition */
  public static final int STOP_PAUSE = (STOP << 3) | PAUSE;
  public static final int PAUSE_PLAY = (PAUSE << 3) | PLAY;
  public static final int PLAY_PAUSE = (PLAY << 3) | PAUSE;
  public static final int PAUSE_STOP = (PAUSE << 3) | STOP;

  /* state return values */
  public static final int FAILURE = 0;
  public static final int SUCCESS = 1;
  public static final int ASYNC = 2;
  public static final int NO_PREROLL = 3;

  /* current state, next and pending state */
  protected int currentState;
  protected int nextState;
  protected int pendingState;
  protected int lastReturn;

  protected Component component;
  
  public String getMime ()
  {
    return null;
  }
  public int typeFind (byte[] data, int offset, int length)
  {
    return -1;
  }

  public Element() {
    this(null);
  }
  public Element(String name) {
    super (name);
    currentState = STOP;
    nextState = NONE;
    pendingState = NONE;
    lastReturn = SUCCESS;
  }

  public String toString ()
  {
    return "Element: ["+getName()+"]";
  }

  public synchronized void setComponent(Component comp)
  {
    component = comp;
  }

  public synchronized void setClock (Clock newClock) {
    clock = newClock;
  }
  public synchronized Clock getClock () {
    return clock;
  }

  public synchronized void setBus (Bus newBus) {
    bus = newBus;
  }
  public synchronized Bus getBus () {
    return bus;
  }

  public synchronized void addPadListener(PadListener listener)
  {
    padListeners.addElement (listener);
  }
  public synchronized void removePadListener(PadListener listener)
  {
    padListeners.removeElement (listener);
  }
  private synchronized void doPadListeners(int method, Pad pad)
  {
    for (Enumeration e = padListeners.elements(); e.hasMoreElements();) {
      PadListener listener = (PadListener) e.nextElement();

      switch (method) {
        case 0:
	  listener.padAdded (pad);
	  break;
        case 1:
	  listener.padRemoved (pad);
	  break;
        case 2:
	  listener.noMorePads ();
	  break;
      }
    }
  }

  public synchronized Pad getPad(String name) {
    for (Enumeration e = pads.elements(); e.hasMoreElements();) {
      Pad pad = (Pad) e.nextElement();
      if (name.equals(pad.getName()))
        return pad;
    }
    return null; 
  }
  public synchronized boolean addPad(Pad newPad) {
    boolean res;

    if (newPad.setParent (this) == false)
      return false;

    pads.addElement (newPad);
    doPadListeners (0, newPad);

    return true;
  }
  public synchronized boolean removePad(Pad aPad) {
    if (aPad.getParent() != this)
      return false;
    aPad.unParent();
    pads.removeElement (aPad);
    doPadListeners (1, aPad);
    return true;
  }
  public synchronized void noMorePads() {
    doPadListeners (2, null);
  }

  public Enumeration enumPads() {
    return pads.elements();
  }

  public void postMessage (Message message) {
    Bus myBus;

    synchronized (this) {
      myBus = bus;
    }

    if (myBus != null) {
      
      myBus.post (message);
    }
  }

  public synchronized int getState(int[] resState, int[] resPending, long timeout) {
    if (lastReturn == ASYNC) {
      if (pendingState != NONE) {
        try {
          wait (timeout);
	}
	catch (InterruptedException e) {}
      }
    }

    if (resState != null)
      resState[0] = currentState;
    if (resPending != null)
      resPending[0] = pendingState;

    return lastReturn;
  }

  private boolean padsActivate (boolean active)
  {
    int mode = (active ? Pad.MODE_PUSH : Pad.MODE_NONE);
    boolean res = true;

    for (Enumeration e = pads.elements(); e.hasMoreElements();) {
      Pad pad = (Pad) e.nextElement();
      res &= pad.activate(mode);
      if (!res)
        return res;
    }
    return res; 
  }

  public int getStateNext (int current, int pending)
  {
    int sign, result;

    sign = pending - current;
    if (sign > 0) sign = 1;
    else if (sign < 0) sign = -1;
    else sign = 0;

    return current + sign;
  }

  public int getTransition (int current, int next)
  {
    return (current << 3) | next;
  }

  public int getTransitionCurrent (int transition)
  {
    return transition >> 3;
  }

  public int getTransitionNext (int transition)
  {
    return transition & 0x7;
  }

  public int commitState()
  {
    int ret;
    int transition = 0;
    Message msg = null;
    int next, current, pending;

    synchronized (this) {
      int oldState, oldNext;

      pending = pendingState;

      if (pending == NONE)
        return SUCCESS;
    
      oldState = currentState;
      oldNext = nextState;
      current = currentState = oldNext;

      if (pending == current) {
        pendingState = NONE;
        nextState = NONE;
        lastReturn = SUCCESS;

        notifyAll();
      }
      else {
        next = getStateNext (current, pending); 
        transition = getTransition (current, next);

	nextState = next;
      }
      msg = Message.newStateChanged (this, oldState, oldNext, pending);
    }

    if (msg != null) {
      postMessage (msg);
    }

    if (transition != 0) {
      ret = doChangeState (transition);
    }
    else {
      ret = SUCCESS;
    }

    return ret; 
  }

  public synchronized void abortState()
  {
    if (pendingState != NONE && lastReturn != FAILURE) {
      lastReturn = FAILURE;
      notifyAll();
    }
  }
  public void lostState()
  {
    boolean post = false;
    int current = 0;

    synchronized (this) {
      if (pendingState == NONE && lastReturn != FAILURE) {
        current = currentState;
        pendingState = nextState = currentState;
	lastReturn = ASYNC;
	post = true;
      }
    }
    if (post) {
      postMessage (Message.newStateChanged (this, current, current, current));
      postMessage (Message.newStateDirty (this));
    }
  }

  protected int changeState(int transition)
  {
    boolean res;
    int current, next;

    current = getTransitionCurrent (transition);
    next = getTransitionNext (transition);

    if (next == NONE || current == next)
      return lastReturn;

    switch (transition) {
      case STOP_PAUSE:
        res = padsActivate(true);
        break;
      case PAUSE_PLAY:
        res = true;
        break;
      case PLAY_PAUSE:
        res = true;
        break;
      case PAUSE_STOP:
        res = padsActivate(false);
        break;
      default:
        res = false;
    }
    if (res)
      return SUCCESS;
    else
      return FAILURE;
  }

  private int doChangeState(int transition)
  {
    int result;

    result = changeState (transition);

    switch (result) {
      case FAILURE:
	abortState();
	break;
      case SUCCESS:
      case NO_PREROLL:
        commitState();
        break;
      case ASYNC:
        break;
    }

    synchronized (this) {
      lastReturn = result;
    }
    return result;
  }

  public final int setState(int newState)
  {
    int result;
    int transition;
    int oldPending;

    synchronized (stateLock) {

      synchronized (this) {
        if (lastReturn == FAILURE) {
	  nextState = NONE;
	  pendingState = NONE;
	  lastReturn = SUCCESS;
	}

	oldPending = pendingState;

	pendingState = newState;

        if (oldPending != NONE) {
          /* upwards state change will happen ASYNC */
          if (oldPending <= newState) {
	    lastReturn = ASYNC;
	    return ASYNC;
	  }
          /* element is going to this state already */
          else if (nextState == newState) {
	    lastReturn = ASYNC;
	    return ASYNC;
	  }
          /* element was performing an ASYNC upward state change and
           * we request to go downward again. Start from the next pending
           * state then. */
          else if (nextState > newState && lastReturn == ASYNC) {
            currentState = nextState;
          }
        }
        nextState = getStateNext (currentState, newState);
        transition = getTransition (currentState, nextState);
      }
      result = doChangeState (transition);
    }
    return result;
  }
}