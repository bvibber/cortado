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

public class Clock {
  private long adjust;
  private long startTime;
  private long lastMedia;
  private boolean paused = true;
  private ClockProvider provider;

  public Clock()
  {
    provider = new SystemClock();
  }

  public synchronized void pause() {
    if (!paused) {
      paused = true;
      lastMedia = getMediaTime() - adjust;
      //System.out.println("pause "+lastMedia);
      notifyAll();
    }
  }

  public synchronized void setProvider (ClockProvider prov)
  {
    provider = prov;
  }

  public synchronized void play() {
    if (paused) {
      long now;
      long gap;
      long media;
      
      now = provider.getTime();
      if (startTime == 0) {
        startTime = now;
      }
      media = getMediaTime() - adjust;
      gap = media - lastMedia;
    
      paused = false;
      //System.out.println("play "+startTime+" "+now+" "+gap+" "+lastMedia+" "+media);
      startTime += gap;  
      notifyAll();
    }
  }

  public synchronized long getElapsedTime() {
    return provider.getTime() - startTime;
  }

  public synchronized long getMediaTime() {
    return provider.getTime() - startTime + adjust;
  }

  public synchronized void updateAdjust(long newAdjust) {
    //System.out.println("clock update adjust "+newAdjust);
    adjust += newAdjust;
    notifyAll();
  }

  public synchronized void setAdjust(long newAdjust) {
    //System.out.println("clock set adjust "+newAdjust);
    adjust = newAdjust;
    notifyAll();
  }

  public long getAdjust() {
    return adjust;
  }

  public synchronized void checkPlay () throws InterruptedException {
    while (paused) {
      wait ();
    }
  }

  public boolean waitForMediaTime(long time) throws InterruptedException {
    long now;
    long interval;
    boolean in_time = false;

    checkPlay();
    
    while (true) {
      now = getMediaTime();
      interval = time - now;
      if (interval <= 0) {
        //System.out.println("shortcut now="+now+" time="+time+" interval="+interval);
        return in_time;
      }
      in_time = true;

      synchronized (this) {
        //System.out.println("waiting now="+now+" time="+time+" interval="+interval+"...");
        wait (interval);
      }
    }
  }
}

