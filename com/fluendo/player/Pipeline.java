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
import com.fluendo.plugin.*;
import com.fluendo.utils.*;

public class Pipeline extends com.fluendo.jst.Element
{
  protected Clock clock;
  protected Clock fixedClock = null;
  protected Vector elements = new Vector();

  protected long streamTime;

  public Pipeline() {
    this (null);
  }

  public Pipeline(String name) {
    super (name);
    clock = new SystemClock(); 
  }

  public void useClock(Clock clock) {
    fixedClock = clock;
  }

  public boolean add(Element elem) {
    if (elem instanceof ClockProvider)
      clock = ((ClockProvider)elem).provideClock();

    elements.addElement (elem);
    elem.baseTime = baseTime;
    return true;
  }
  public boolean remove(Element elem) {
    boolean res;

    if ((res = elements.removeElement (elem))) {
      elem.setClock (null);
    }
    return res;
  }

  public int getState(int[] resState, int[] resPending, long timeout) {
    int res = SUCCESS;

    for (Enumeration e = elements.elements(); e.hasMoreElements();) {
      Element elem = (Element) e.nextElement();

      if ((res = elem.getState(resState, resPending, timeout)) != SUCCESS)
        break;
    }

    synchronized (stateLock) {
      switch (res) {
        case SUCCESS:
          commitState();
          break;
        case FAILURE:
          abortState();
          break;
        default:
          break;
      }
      if (resState != null)
        resState[0] = state;
      if (resPending != null)
        resPending[0] = pending;
    }

    return res;
  }

  protected int doChildStateChange()
  {
    for (Enumeration e = elements.elements(); e.hasMoreElements();) {
      Element elem = (Element) e.nextElement();

      elem.setClock (clock);
      elem.baseTime = baseTime;
    }

    return super.changeState();
  }

  public int changeState()
  {
    int transition = getTransition();
    int result;

    switch (transition) {
      case STOP_PAUSE:
        break;
      case PAUSE_PLAY:
        baseTime = clock.getTime() - streamTime;
        break;
      default:
        break;
    }
    result = doChildStateChange();

    switch (transition) {
      case STOP_PAUSE:
        streamTime = 0;
	break;
      case PLAY_PAUSE:
        streamTime = clock.getTime() - baseTime;
        break;
      case PAUSE_STOP:
        break;
      default:
        break;
    }

    return result;
  }

  public synchronized boolean seek(long offset)
  {
    setState (Element.PAUSE);
    streamTime = 0;
    getState (null, null, 0);
    setState (Element.PLAY);
    return true;
  }
}
