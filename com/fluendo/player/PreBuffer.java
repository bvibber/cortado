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

import java.io.*;
import java.util.*;
import com.fluendo.utils.*;

public class PreBuffer extends InputStream implements Runnable {
  private InputStream inputStream;
  private int bufferSize;
  private byte[] buffer;
  private int in;
  private int out;
  private Reader reader;
  private Thread thread;
  private boolean stopping = false;
  private int low;
  private int high;
  private PreBufferNotify notify;
  private boolean eos;
  private boolean readerBlocked;
  private boolean writerBlocked;
  private long received;
  private long receiveStart;
  private long consumed;
  private static int SEGSIZE = 1024;
  private int segment;
  private byte[] temp = new byte[1];

  private int state = PreBufferNotify.STATE_START;

  /* 
   *
   *  +---------------------------+
   *  |  !        !               |
   *  +---------------------------+
   *     ^        ^
   *     in       out
   *
   * in:  pointer in buffer where writing happens
   * out: pointer where reading happens.
   *
   * conditions:
   * 
   * full:   in == out
   * empty:  in == -1
   *
   * free:      (out + bufSize - in) % bufSize    (amount available for writing)
   * available: (in + bufSize - out) % bufSize    (amount available for reading)
   *
   */ 

  public PreBuffer (InputStream is, int bufSize, int bufLow, int bufHigh, PreBufferNotify pbn) {
    inputStream = is;
    in = -1;
    out = 0;
    notify = pbn;
    low = (bufSize * bufLow/100);
    if (low <= 0)
      low = 1;
    high = (bufSize * bufHigh/100);
    if (high >= bufSize)
      high = bufSize-1;
    eos = false;
    segment = Math.max (SEGSIZE, low);

    bufferSize = bufSize;
    buffer = new byte[bufferSize];
    
    thread = new Thread (this);
    thread.start();
    receiveStart = 0;
  }

  public int free ()
  {
    synchronized (this) {
      if (in == -1)
        return bufferSize;
      return (out +  bufferSize - in) % bufferSize;
    }
  }
  public int available ()
  {
    synchronized (this) {
      if (in == -1)
        return 0;
      if (in == out)
        return bufferSize;
      return (in +  bufferSize - out) % bufferSize;
    }
  }

  public void stop() {
    stopping = true;
    try {
      thread.interrupt();
    }
    catch (Exception e) { }
    try {
      thread.join();
    }
    catch (Exception e) {}
  }

  public void run() {
    Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
    try {
      realRun();
    }
    catch (Throwable t) {
      Cortado.shutdown(t); 
    }
  }

  public boolean isEmpty() {
    return available() < low;
  }

  public boolean isFilled() {
    return available() >= high;
  }

  public synchronized int getFilled() {
    return available();
  }

  public synchronized long getReceived() {
    return received;
  }

  public synchronized double getReceiveSpeed() {
    if (receiveStart == 0)
      return 0.0;

    long time = System.currentTimeMillis() - receiveStart;
    return ((double)received) / time;
  }

  public synchronized double getConsumeSpeed() {
    if (receiveStart == 0)
      return 0.0;

    long time = System.currentTimeMillis() - receiveStart;
    return ((double)consumed) / time;
  }

  private void checkFilled()
  {
    if (state == PreBufferNotify.STATE_BUFFER) {
      if (isFilled()) {
        state = PreBufferNotify.STATE_PLAYBACK;
        if (notify != null)
  	  notify.preBufferNotify (state);
      }
    }
  }
  private void checkEmpty()
  {
    if (!eos && state == PreBufferNotify.STATE_PLAYBACK) {
      if (isEmpty()) {
        state = PreBufferNotify.STATE_BUFFER;
    	if (notify != null)
	  notify.preBufferNotify (state);
      }
    }
  }

  public synchronized void startBuffer() {
    Debug.log(Debug.INFO, "start buffer..");
    state = PreBufferNotify.STATE_BUFFER;
    received = 0;
    receiveStart = System.currentTimeMillis();
  }

  private void realRun() {
    Debug.log(Debug.INFO, "entering preroll thread");
    while (!stopping) {
      try {
	int len1, len2;
	int newIn;
	int ret1, ret2;

        synchronized (this) {
          while (free () < segment) {
	    if (notify != null)
              notify.preBufferNotify (PreBufferNotify.STATE_OVERFLOW);
	    writerBlocked = true;
	    wait();
	    writerBlocked = false;
	  }
	  if (in < 0)
	    newIn = out;
	  else
	    newIn = in;

	  if (newIn + segment > bufferSize) {
	    len1 = bufferSize - newIn;
	    len2 = segment - len1;
	  }
	  else {
	    len1 = segment;
	    len2 = 0;
	  }
        }

	ret1 = inputStream.read(buffer, newIn, len1);
	if (len2 > 0 && ret1 == len1)
	  ret2 = inputStream.read(buffer, 0, len2);
	else
	  ret2 = 0;
	  
	if (ret1 < 0) {
	  eos = true;
	  Debug.log(Debug.INFO, "writer EOS");
	  break;
	}
	int ret;
	if (ret2 <= 0) {
	  ret = ret1;
	}
	else {
	  ret = ret1 + ret2;
	}

	synchronized (this) {
          received += ret;
	  in = (newIn + ret) % bufferSize;

	  checkFilled();

	  if (readerBlocked)
	    notify ();
	}
      }
      catch (Exception e) {
        if (!stopping) {
          e.printStackTrace();
	  stopping = true;
	}
      }
    }
    Debug.log(Debug.INFO, "exit preroll thread");
  }

  public int read() {
    int ret = read (temp, 0, 1);
    if (ret > 0) {
      ret = (temp[0] + 256);   
    }
    return ret;
  }

  public int read(byte[] res, int offset, int len) {
    int len2, len1;

    synchronized (this) {
      int avail = available();

      if (eos) {
        if (avail == 0) {
	  Debug.log(Debug.INFO, "reader EOS");
	  return -1;
	}
        len = Math.min (avail, len);
      }

      while (avail < len) {
        try {
	  Debug.log(Debug.DEBUG, "read: wait available "+avail+" need "+len);
	  readerBlocked = true;
          wait();
	  readerBlocked = false;
	  Debug.log(Debug.DEBUG, "read: wait done available "+avail+" need "+len);
	}
	catch (InterruptedException ie) { }
        avail = available();
      }
      /* split up copy if it crosses the buffer boundary */
      if (out + len > bufferSize) {
        len1 = bufferSize - out;
	len2 = len - len1;
      }
      else {
        len1 = len;
	len2 = 0;
      }
    }

    System.arraycopy (buffer, out, res, offset, len1);
    if (len2 > 0)
      System.arraycopy (buffer, 0, res, offset+len1, len2);

    synchronized (this) {
      out = (out + len) % bufferSize;

      checkEmpty();

      if (writerBlocked)
        notify();
      consumed += len;
    }

    return len;
  }

  public final void dumpStats()
  {
    Debug.log(Debug.DEBUG, "buffer: [in:"+getReceived()+
                      ", in-speed:"+getReceiveSpeed() +
                      ", avail:"+available()+"/"+bufferSize+"]");
  }
}
