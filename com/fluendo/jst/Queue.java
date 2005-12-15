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

import java.util.*;
import com.fluendo.utils.*;

public class Queue extends Element 
{
  private Vector queue = new Vector();
  private static final int MAX_SIZE = 500;
  private int srcResult = Pad.WRONG_STATE;

  private boolean isFilled() {
    return queue.size() > MAX_SIZE;
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
        res = push((Buffer)obj);
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
	  }
          res = startTask();
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
	     srcpad.startTask();
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
}
