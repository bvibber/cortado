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

  public long getMediaTime() {
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

