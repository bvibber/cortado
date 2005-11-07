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

package com.fluendo.plugin;

import java.util.*;
import com.fluendo.jst.*;
import com.fluendo.utils.*;

public abstract class Sink extends Element
{
  private java.lang.Object prerollLock = new java.lang.Object();
  private boolean isEOS;
  private boolean flushing;
  private boolean havePreroll;
  private boolean needPreroll;
  private Clock.ClockID clockID;
  private long prerollTime;
  
  public long getPrerollTime () {
    synchronized (prerollLock) {
      return prerollTime;
    }
  }
  protected Pad sinkpad = new Pad(Pad.SINK, "sink") {
    private int finishPreroll(Buffer buf)
    {
      synchronized (prerollLock) {
        int res = OK;
	Sink sink = (Sink) parent;

	if (isFlushing())
	  return WRONG_STATE;

        if (needPreroll) {
	  prerollTime = buf.timestamp;

	  havePreroll = true;
          preroll (buf);

	  boolean postPause = false;
	  boolean postPlaying = false;
	  int current, next, pending;

	  synchronized (sink) {
	    current = currentState;
	    next = nextState;
	    pending = pendingState;

	    switch (pending) {
	      case PLAY:
	        needPreroll = false;
		postPlaying = true;
		break;
	      case PAUSE:
	        needPreroll = true;
		postPause = true;
		break;
	      case STOP:
	        havePreroll = false;
	        needPreroll = false;
	        return WRONG_STATE;
	    }
	    if (pendingState != NONE) {
	      currentState = pending;
	      pendingState = NONE;
	      nextState = NONE;
	      lastReturn = SUCCESS;
	    }
	  }

	  if (postPause)
	    postMessage (Message.newStateChanged (this, current, next, NONE));
	  if (postPlaying)
	    postMessage (Message.newStateChanged (this, next, pending, NONE));

	  if (postPause || postPlaying)
	    postMessage (Message.newStateDirty (this));

	  synchronized (sink) {
	    sink.notifyAll();
	  }

	  if (needPreroll) {
	    needPreroll = false;
	    try {
	      prerollLock.wait();
	    }
	    catch (InterruptedException ie) {}

	    havePreroll = false;
	  }
	}
	if (isFlushing())
	  return WRONG_STATE;

	return res;
      }
    }

    protected boolean eventFunc (Event event)
    {
      Sink sink = (Sink) parent;
      doEvent(event);

      switch (event.getType()) {
        case Event.FLUSH_START:
	  synchronized (sink) {
	    sink.flushing = true;
	    if (clockID != null) {
	      clockID.unschedule();
	    }
	  }
	  synchronized (prerollLock) {
	    sink.isEOS = false;
	    needPreroll = true;
	    prerollLock.notify();
	    havePreroll = false;
	  }
	  synchronized (streamLock) {
	    Debug.log(Debug.DEBUG, this+" flushed "+havePreroll+" "+needPreroll);
	    lostState();
	  }
	  break;
        case Event.FLUSH_STOP:
	  synchronized (streamLock) {
	    sink.flushing = false;
	  }
	  break;
        case Event.NEWSEGMENT:
	  break;
        case Event.EOS:
	  break;
	default:
	  break;
      }

      return true;
    }
  
    protected int chainFunc (Buffer buf)
    {
      int res;
      int status;

      if ((res = finishPreroll(buf)) != Pad.OK)
        return res;

      status = doSync(buf);
      switch (status) {
        case Clock.EARLY:
        case Clock.OK:
          res = render (buf);
	  break;
	default:
	  res = Pad.OK;
	  break;
      }
      buf.free();

      return res;
    }

    protected boolean setCapsFunc (Caps caps)
    {
      boolean res;
      Sink sink = (Sink) parent;
      
      res = sink.setCapsFunc (caps);

      return res;
    }
  };

  protected int preroll (Buffer buf) {
    return Pad.OK;
  }

  protected boolean doEvent(Event event)
  {
    return true;
  }

  protected int doSync(Buffer buf) {
    int ret;
    long time;
    Clock.ClockID id = null;

    synchronized (this) {
      if (flushing)
        return Clock.UNSCHEDULED;

      time = buf.timestamp;
      if (time == -1)
        return Clock.OK;

      time += baseTime;

      if (clock != null)
        id = clockID = clock.newSingleShotID (time);
    }
    
    if (id != null) {
      ret = id.waitID();
    }
    else
      ret = Clock.OK;

    synchronized (this) {
      clockID = null;
    }
    return ret;
  }
  protected boolean setCapsFunc (Caps caps) {
    return true;
  }

  protected int render (Buffer buf) {
    return Pad.OK;
  }

  public Sink () {
    super ();
    addPad (sinkpad);
    setFlag (Element.FLAG_IS_SINK);
  }

  public boolean sendEvent (Event event) {
    return sinkpad.pushEvent (event);
  }

  protected int changeState (int transition) {
    int result = SUCCESS;
    int presult;

    switch (transition) {
      case STOP_PAUSE:
	this.isEOS = false;
        synchronized (prerollLock) {
          needPreroll = true;
          havePreroll = false;
        }
        result = ASYNC;
        break;
      case PAUSE_PLAY:
        synchronized (prerollLock) {
          if (havePreroll) {
            needPreroll = false;
	    prerollLock.notify();
	  }
	  else {
            needPreroll = false;
	  }
	}
        break;
      default:
        break;
    }

    presult = super.changeState(transition);
    if (presult == FAILURE)
      return presult;

    switch (transition) {
      case PLAY_PAUSE:
      {
        boolean isEOS;

        /* unlock clock */
        synchronized (this) {
	  if (clockID != null) {
	    clockID.unschedule();
	  }
	  isEOS = this.isEOS;
	}
        synchronized (prerollLock) {
	  if (!havePreroll && !isEOS) {
	    needPreroll = true;
	    result = ASYNC;
	  }
	}
        break;
      }
      case PAUSE_STOP:
      {
        synchronized (prerollLock) {
	  if (havePreroll) {
	    prerollLock.notify();
	  }
	  needPreroll = false;
	  havePreroll = false;
	}
	this.isEOS = false;
        break;
      }
      default:
        break;
    }

    return result;
  }
}
