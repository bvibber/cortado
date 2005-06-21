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

	if (isFlushing())
	  return FLUSHING;

        if (needPreroll) {
	  prerollTime = buf.timestamp;
	  System.out.println("Preroll "+buf+" timestamp: "+prerollTime);
	  commitState();
	  havePreroll = true;
	  needPreroll = false;
          preroll (buf);
	  try {
	    prerollLock.wait();
	  }
	  catch (InterruptedException ie) {}

	  havePreroll = false;
	}
	if (isFlushing())
	  return FLUSHING;

	return res;
      }
    }

    protected boolean eventFunc (Event event)
    {
      doEvent(event);

      switch (event.getType()) {
        case Event.FLUSH_START:
	  synchronized (parent) {
	    ((Sink)parent).isEOS = false;
	    if (clockID != null) {
	      clockID.unschedule();
	    }
	  }
	  synchronized (prerollLock) {
	    needPreroll = true;
	    prerollLock.notify();
	    havePreroll = false;
	  }
	  synchronized (streamLock) {
	    Debug.log(Debug.DEBUG, this+" flushed "+havePreroll+" "+needPreroll);
	  }
	  synchronized (stateLock) {
	    lostState();
	  }
	  break;
        case Event.FLUSH_END:
	  break;
        case Event.DISCONT:
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

      if ((res = finishPreroll(buf)) != OK)
        return res;

      doSync(buf);

      res = render (buf);

      buf.free();

      return res;
    }

    protected boolean setCapsFunc (Caps caps)
    {
      boolean res;
      
      if ((res = setCaps(caps))) {
        super.setCapsFunc (caps);
      }
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

  protected void doSync(Buffer buf) {
    int ret;
    long time;
    Clock.ClockID id = null;

    synchronized (this) {
      time = buf.timestamp;
      if (time == -1)
        return;

      time += baseTime;

      if (clock != null)
        id = clockID = clock.newSingleShotID (time);
    }
    
    if (id != null)
      ret = id.waitID();
    else
      ret = Clock.OK;

    synchronized (this) {
      clockID = null;
    }
  }
  protected boolean setCaps (Caps caps) {
    return true;
  }

  protected int render (Buffer buf) {
    return Pad.OK;
  }

  public Sink () {
    super ();
    addPad (sinkpad);
  }

  protected int changeState () {
    int result = SUCCESS;
    int transition;

    transition = getTransition();
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

    super.changeState();

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
