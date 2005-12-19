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

public class Queue extends Element 
{
  private static final int DEFAULT_MAX_BUFFERS = 50;
  private static final int DEFAULT_MAX_SIZE = -1;
  private static final boolean DEFAULT_IS_BUFFER = false;
  private static final int DEFAULT_LOW_PERCENT = 10;
  private static final int DEFAULT_HIGH_PERCENT = 70;

  private Vector queue = new Vector();
  private int srcResult = Pad.WRONG_STATE;
  private int size;
  private boolean buffering;

  private int maxBuffers = DEFAULT_MAX_BUFFERS;
  private int maxSize = DEFAULT_MAX_SIZE;
  private boolean isBuffer = DEFAULT_IS_BUFFER;
  private int lowPercent = DEFAULT_LOW_PERCENT;
  private int highPercent = DEFAULT_HIGH_PERCENT;

  private boolean isFilled() {
    if (maxSize != -1) {
      return size >= maxSize;
    }
    else {
      return queue.size() > maxBuffers;
    }
  }
  private boolean isEmpty() {
    return queue.size() == 0;
  }

  private void clearQueue ()
  {
    for (Enumeration e = queue.elements(); e.hasMoreElements();) {
      java.lang.Object obj = e.nextElement();
      if (obj instanceof Buffer)
        ((Buffer)obj).free();
    }
    queue.setSize(0);
    size = 0;
    buffering = true;
  }

  private void updateBuffering () {
    if (!isBuffer || srcResult != Pad.OK)
      return;

    /* figure out the percentage we are filled */
    int percent = size * 100 / maxSize;
    if (percent > 100)
      percent = 100;

    if (buffering) {
      if (percent >= highPercent) {
        buffering = false;
        srcpad.startTask();
      }
      postMessage (Message.newBuffering (this, buffering, percent));
    }
    else {
      if (percent < lowPercent) {
        buffering = true;
        srcpad.pauseTask();
      }
    }
  }

  private boolean start() {
    boolean res;

    if (!isBuffer) {
      buffering = false;
      res = srcpad.startTask();
    }
    else {
      buffering = true;
      postMessage (Message.newBuffering (this, true, 0)); 
      res = true;
    }
    return res;
  }
  
  private boolean stop() {
    boolean res;

    if (!isBuffer) {
      buffering = false;
      res = srcpad.stopTask();
    }
    else {
      buffering = true;
      res = true;
    }
    return res;
  }
  
  private Pad srcpad = new Pad(Pad.SRC, "src") {
    protected void taskFunc() {
      java.lang.Object obj;
      int res;
      
      synchronized (queue) {
        if (srcResult != OK)
	  return;
	  
	while (isEmpty()) {
          try {
            queue.wait();
	    if (srcResult != OK) 
	      return;
	  }
	  catch (InterruptedException ie) {}
	}
	obj = queue.remove(queue.size()-1);
        queue.notifyAll();
      }

      if (obj instanceof Event) {
        pushEvent((Event)obj);
	res = OK;
      }
      else {
        Buffer buf = (Buffer) obj;

	size -= buf.length;

        res = push(buf);
      }
      synchronized (queue) {
        if (res != OK) {
	  srcResult = res;
	  if (isFlowFatal (res)) {
	    postMessage (Message.newStreamStatus (parent, "fatal flow error: "+getFlowName (res)));
            pushEvent(Event.newEOS());
	  }
	  pauseTask();
        }
	updateBuffering ();
        queue.notifyAll();
      }
    }

    protected boolean activateFunc (int mode)
    {
      boolean res = true;

      switch (mode) {
        case MODE_NONE:
	  synchronized (queue) {
	    clearQueue();
	    srcResult = WRONG_STATE;
	    queue.notifyAll();
	  }
          res = stopTask();
          break;
        case MODE_PUSH:
	  synchronized (queue) {
	    srcResult = OK;
	    /* if we buffer, we start when we are hitting the
	     * high watermark */
	    if (!isBuffer) {
              res = startTask();
	    }
	    else {
	      buffering = true;
	      postMessage (Message.newBuffering (this, true, 0)); 
	    }
	  }
          break;
        default:
	  synchronized (queue) {
	    srcResult = WRONG_STATE;
	  }
          res = false;
          break;
      }
      return res;
    }
  };
	  

  private Pad sinkpad = new Pad(Pad.SINK, "sink") {
    protected boolean eventFunc (Event event) {
      switch (event.getType()) {
        case Event.FLUSH_START:
	   srcpad.pushEvent (event);
	   synchronized (queue) {
	     srcResult = WRONG_STATE;
	     queue.notifyAll();
	   }
	   synchronized (streamLock) {
	     Debug.log(Debug.INFO, "synced "+this);
	   }
	   srcpad.pauseTask();
	   break;
        case Event.FLUSH_STOP:
	   srcpad.pushEvent (event);

	   synchronized (streamLock) {
	     synchronized (queue) {
	       clearQueue ();
	       srcResult = OK;
	       queue.notifyAll();
	     }
	     if (!isBuffer) {
	       srcpad.startTask();
	     }
	     else {
	       buffering = true;
	       postMessage (Message.newBuffering (this, true, 0)); 
	     }
	   }
	   break;
	default:
	   synchronized (streamLock) {
             synchronized (queue) {
               queue.add(0, event);
               queue.notifyAll();
	     }
	   }
	   break;
      }
      return true;
    }

    protected int chainFunc (Buffer buf) {
      synchronized (queue) {
        if (srcResult != OK) {
	  buf.free();
	  return srcResult;
	} 

	while (isFilled()) {
          try {
            queue.wait();
	    if (srcResult != OK) {
	      buf.free();
	      return srcResult;
	    }
	  }
	  catch (InterruptedException ie) {
	    ie.printStackTrace();
	    buf.free();
	    return WRONG_STATE;
	  }
	}
	size += buf.length;
	updateBuffering();

        queue.add(0, buf);
        queue.notify();
      }
      return OK;
    }
  };

  public Queue() {
    super();
    addPad (srcpad);
    addPad (sinkpad);
  }

  public String getName ()
  {
    return "queue";
  }

  public boolean setProperty (String name, java.lang.Object value) {
    if (name.equals("maxBuffers"))
      maxBuffers = Integer.valueOf(value.toString()).intValue();
    else if (name.equals("maxSize")) 
      maxSize = Integer.valueOf(value.toString()).intValue();
    else if (name.equals("isBuffer"))
      isBuffer = String.valueOf(value).equalsIgnoreCase("true");
    else if (name.equals("lowPercent"))
      lowPercent = Integer.valueOf(value.toString()).intValue();
    else if (name.equals("highPercent"))
      highPercent = Integer.valueOf(value.toString()).intValue();
    else
      return false;

    return true;
  }

  public java.lang.Object getProperty (String name) {
    if (name.equals("maxBuffers"))
      return new Integer (maxBuffers);
    else if (name.equals("maxSize"))
      return new Integer (maxSize);
    else if (name.equals("isBuffer")) 
      return (isBuffer ? "true" : "false");
    else if (name.equals("lowPercent"))
      return new Integer (lowPercent);
    else if (name.equals("highPercent"))
      return new Integer (highPercent);

    return null;
  }
}
