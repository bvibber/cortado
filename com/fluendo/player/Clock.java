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

  public synchronized void pause() {
    if (!paused) {
      paused = true;
      lastMedia = getMediaTime();
      //System.out.println("pause "+lastMedia);
    }
    notifyAll();
  }

  public synchronized void play() {
    long now;
    long gap;
    long media;

    if (paused) {
      now = System.currentTimeMillis();
      if (startTime == 0) {
        startTime = now;
      }
      media = getMediaTime();
      gap = getMediaTime() - lastMedia;
    
      paused = false;
      //System.out.println("play "+startTime+" "+now+" "+gap+" "+lastMedia+" "+media);
      startTime += gap;  
    }
    notifyAll();
  }

  public synchronized long getElapsedTime() {
    return System.currentTimeMillis() - startTime;
  }

  public synchronized long getMediaTime() {
    return System.currentTimeMillis() - startTime + adjust;
  }

  public synchronized void updateAdjust(long newAdjust) {
    adjust += newAdjust;
    notifyAll();
  }

  public synchronized void setAdjust(long newAdjust) {
    adjust = newAdjust;
    notifyAll();
  }

  public long getAdjust() {
    return adjust;
  }

  public void waitForMediaTime(long time) throws InterruptedException {
    long now;
    long interval;

    synchronized (this) {
      while (paused) {
        wait ();
      }
    }
    
    while (true) {
      now = getMediaTime();
      interval = time - now;
      if (interval <= 0)
        break;

      synchronized (this) {
        try {
          //System.out.print("waiting now="+now+" time="+time+" interval="+interval+"...");
          wait (interval);
          //System.out.println("done");
	}
	catch (Exception e) { e.printStackTrace();}
      }
    }
  }
}

