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

  private boolean isFilled() {
    return queue.size() > MAX_SIZE;
  }
  private boolean isEmpty() {
    return queue.size() == 0;
  }
  
  private Pad srcpad = new Pad(Pad.SRC, "src") {
    protected void taskFunc() {
      java.lang.Object obj;
      int res;
      
      synchronized (queue) {
	while (isEmpty()) {
          try {
            queue.wait();
	    if (sinkpad.isFlushing()) 
	      return;
	  }
	  catch (InterruptedException ie) {}
	}
	obj = queue.remove(queue.size()-1);
        queue.notify();
      }

      if (obj instanceof Event) {
        pushEvent((Event)obj);
      }
      else {
        if ((res = push((Buffer)obj)) != OK) {
	  pauseTask();
        }
      }
    }

    protected boolean activateFunc (int mode)
    {
      boolean res = true;

      switch (mode) {
        case MODE_NONE:
          res = stopTask();
          break;
        case MODE_PUSH:
          res = startTask();
          break;
        default:
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
	     queue.notifyAll();
	   }
	   synchronized (streamLock) {
	     synchronized (queue) {
	       queue.setSize(0);
	       queue.notifyAll();
	     }
	     srcpad.pauseTask();
	   }
	   break;
        case Event.FLUSH_END:
	   srcpad.pushEvent (event);
	   srcpad.startTask();
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
	while (isFilled()) {
          try {
            queue.wait();
	    if (isFlushing())
	      return FLUSHING;
	  }
	  catch (InterruptedException ie) {}
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
