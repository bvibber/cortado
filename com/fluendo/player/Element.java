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
  protected Vector pads = new Vector();
  protected java.lang.Object stateLock =  new java.lang.Object();
  private Vector padListeners = new Vector();

  protected Clock clock;
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
  public static final int SUCCESS = 1;
  public static final int ASYNC = 0;
  public static final int FAILURE = -1;

  /* current state and pending state */
  protected int state;
  protected int pending;
  protected boolean error;

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
    state = STOP;
    pending = NONE;
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
	  listener.newPad (pad);
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

  public void postMessage (java.lang.Object message) {
    System.out.println ("Element "+this+" posted message "+message);
  }

  public int getState(int[] resState, int[] resPending, long timeout) {
    int result;

    synchronized (stateLock) {
      if (error)
        return FAILURE;

      if (pending == NONE)
        result = SUCCESS;
      else {
        try {
          stateLock.wait (timeout);
	}
	catch (InterruptedException e) {}
	
        if (error)
          return FAILURE;

	if (pending == NONE)
	  result = SUCCESS;
	else 
	  result = ASYNC;
      }
    }

    if (resState != null)
      resState[0] = state;
    if (resPending != null)
      resPending[0] = pending;

    return result;
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

  public void commitState()
  {
    synchronized (stateLock) {
      if (pending != NONE) {
        state = pending;
	pending = NONE;
	error = false;
	stateLock.notifyAll();
      }
    }
  }
  public void abortState()
  {
    synchronized (stateLock) {
      if (pending != NONE && !error) {
	error = true;
	stateLock.notifyAll();
      }
    }
  }
  public void lostState()
  {
    synchronized (stateLock) {
      if (pending == NONE) {
        pending = state;
	stateLock.notifyAll();
      }
    }
  }

  public int getTransition ()
  {
    int result;

    synchronized (stateLock) {
      result = (state << 3) | pending;
    }
    return result;
  }

  protected int changeState()
  {
    boolean res;
    int transition;

    transition = getTransition();

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

  public final int setState(int newState)
  {
    int result;

    synchronized (stateLock) {
      error = false;

      /* need intermediate state? */
      if ((state == STOP && newState == PLAY) ||
          (state == PLAY && newState == STOP)) {
	if ((result = setState (PAUSE)) != SUCCESS)
	  return result;
      }
      pending = newState;

      if ((result = changeState ()) != SUCCESS) {
        if (result == FAILURE)
	  abortState();
      }
      else {
        commitState();
      }
    }
    return result;
  }
}
