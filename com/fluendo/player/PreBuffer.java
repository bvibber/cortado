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

public class PreBuffer extends InputStream implements Runnable {
  private InputStream inputStream;
  private int bufferSize;
  private short[] buffer;
  private int in;
  private int out;
  private Reader reader;
  private Thread thread;
  private boolean stopping = false;
  private int filled = 0;
  private int low;
  private int high;
  private PreBufferNotify notify;
  private boolean eos;
  private boolean readerBlocked;
  private boolean writerBlocked;

  private int state = PreBufferNotify.STATE_START;

  public PreBuffer (InputStream is, int bufSize, int bufLow, int bufHigh, PreBufferNotify pbn) {
    inputStream = is;
    bufferSize = bufSize;
    buffer = new short[bufferSize];
    in = -1;
    out = 0;
    notify = pbn;
    filled = 0;
    low = (bufSize * bufLow/100);
    if (low <= 0)
      low = 1;
    high = (bufSize * bufHigh/100);
    if (high >= bufSize)
      high = bufSize-1;
    eos = false;
    thread = new Thread (this);
    thread.start();
  }

  public void stop() {
    stopping = true;
    thread.interrupt();
    try {
      thread.join();
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void run() {
    try {
      realRun();
    }
    catch (Throwable t) {
      Cortado.shutdown(t); 
    }
  }

  private void realRun() {
    System.out.println("entering preroll thread");
    while (!stopping) {
      try {
        int b = inputStream.read();
        receive (b);
	if (b < 0) {
	  eos = true;
	  break;
	}
      }
      catch (Exception e) {
        e.printStackTrace();
	stopping = true;
      }
    }
    System.out.println("exit preroll thread");
  }

  public synchronized boolean isEmpty() {
    return filled < low;
  }

  public synchronized boolean isFilled() {
    return filled >= high;
  }

  public synchronized int getFilled() {
    return filled;
  }

  public synchronized void startBuffer() {
    state = PreBufferNotify.STATE_BUFFER;
  }

  public synchronized void receive(int b) {
    while (in == out) {
      if (notify != null)
        notify.preBufferNotify (PreBufferNotify.STATE_OVERFLOW);

      try {
        writerBlocked = true;
	wait ();
        writerBlocked = false;
      }
      catch (InterruptedException ie) {
        if (stopping)
          return;
        ie.printStackTrace();
      }
      if (stopping)
        return;
    }
    if (in < 0) {
      in = 0;
      out = 0;
      filled = 1;
    }
    else {
      filled++;
    }
    buffer[in++] = (short) b;
    if (in >= buffer.length) {
      in = 0;
    }
    if (state == PreBufferNotify.STATE_BUFFER) {
      if (isFilled()) {
        state = PreBufferNotify.STATE_PLAYBACK;
	if (notify != null)
	  notify.preBufferNotify (state);
      }
    }
    if (readerBlocked)
      notify();
  }

  public synchronized int read() {
    /* buffer empty */
    while ((state == PreBufferNotify.STATE_BUFFER  && in != out)|| in < 0) {
      if (eos)
        return -1;

      try {
        readerBlocked = true;
	wait ();
        readerBlocked = false;
      }
      catch (InterruptedException ie) {
        if (stopping)
          return -1;
        ie.printStackTrace();
      }
      if (stopping)
        return -1;
    }
    int ret = buffer[out++];

    if (out >= buffer.length) {
      out = 0;
    }
    if (in == out) {
      in = -1;
      filled = 0;
    }
    else {
      filled--;
    }
    if (!eos && state == PreBufferNotify.STATE_PLAYBACK) {
      if (isEmpty()) {
        state = PreBufferNotify.STATE_BUFFER;
	if (notify != null)
	  notify.preBufferNotify (state);
      }
    }
    if (writerBlocked)
      notify();

    return ret;
  }
}
