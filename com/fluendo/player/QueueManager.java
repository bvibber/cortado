/* Cortado - a video player java applet
 * Copyright (C) 2004 Fluendo S.L.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Street #330, Boston, MA 02111-1307, USA.
 */

package com.fluendo.player;

import java.util.*;

public class QueueManager {
  private static final int MAX_QUEUES = 4;
  private static Vector[] queues = new Vector[MAX_QUEUES];
  private static Object[] syncs = new Object[MAX_QUEUES];
  private static int[] sizes = new int[MAX_QUEUES];
  private static int numqueues = 0;

  public static int registerQueue(int maxSize) {
    int freequeue = numqueues;
    queues[freequeue] = new Vector();
    syncs[freequeue] = new Object();
    sizes[freequeue] = maxSize;
    numqueues++;
    return freequeue;
  }
  public static void reset() {
    numqueues = 0;
  }
  public static void adjustOthers(int id, int delta) {
    for (int i=0; i < numqueues; i++) {
      if (i == id) 
        continue;

      if (sizes[i] == Integer.MAX_VALUE ||
          sizes[i] < 1)
	return;
	
      synchronized (syncs[i]) {
        if (sizes[i] < queues[i].size()) {
          sizes[i] += delta;
          syncs[i].notify();
	}
      }
    }
  }
  public static void adjustThis(int id, int delta) {
    if (sizes[id] == Integer.MAX_VALUE ||
        sizes[id] < 1)
      return;
	
    synchronized (syncs[id]) {
      if (sizes[id] < queues[id].size()) {
        sizes[id] += delta;
        syncs[id].notify();
      }
    }
  }
  public static void enqueue(int id, Object object) {
    Object sync = syncs[id];
    Vector queue = queues[id];
    try {
      synchronized (sync) {
        while (queue.size() > sizes[id]) {
	  //System.out.println("queue "+id+" filled");
          sync.wait();
	  //System.out.println("queue "+id+" filled done");
        }
        queue.addElement(object);
        sync.notify();
      }
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  public static Object dequeue(int id) {
    Object sync = syncs[id];
    Vector queue = queues[id];
    Object result = null;
    //System.out.println("sync "+sync+" queue "+queue);
    try {
      synchronized (sync) {
        while (queue.size() == 0) {
	  //System.out.println("queue "+id+" empty");
	  adjustOthers(id, 1);
	  //System.out.println("others adjusted");
          sync.wait();
	  //System.out.println("queue "+id+" empty done");
        }
        result = queue.elementAt(0);
        queue.removeElementAt(0);
	//adjustThis(id, -1);
        sync.notify();
      }
    }
    catch (Throwable e) {
      e.printStackTrace();
    }
    return result;
  }
  public static void dumpStats() {
    System.out.print("queues:");
    for (int i=0; i< numqueues; i++) {
      System.out.print(" [id: "+i+" "+queues[i].size()+" "+sizes[i]+"]");
    }
    System.out.println();
  }
}
