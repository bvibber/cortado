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

  public long getStartTime() {
    return startTime;
  }

  public void setStartTime(long newStartTime) {
    startTime = newStartTime;
  }

  public long getElapsedTime() {
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
    
    while (true) {
      now = getMediaTime();
      interval = time - now;
      if (interval <= 0)
        break;

      synchronized (this) {
        try {
          //System.out.println("waiting now="+now+" time="+time+" interval="+interval);
          wait (interval);
          //System.out.println("waiting done");
	}
	catch (Exception e) { e.printStackTrace();}
      }
    }
  }
}

