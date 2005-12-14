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

import java.awt.*;
import java.awt.image.*;
import java.awt.event.*;
import java.net.*;
import java.util.*;
import com.fluendo.utils.*;

public class Status extends Component implements MouseListener, MouseMotionListener
{
  private int bufferPercent;
  private String message;
  private String error;
  private Rectangle r;
  private Component component;
  private Font font = new Font("SansSerif", Font.PLAIN, 10);
  private boolean haveAudio;
  private boolean havePercent;
  private boolean seekable;

  private Color button1Color;
  private Color button2Color;
  private static final int triangleX[] = { 4, 4, 9 };
  private static final int triangleY[] = { 3, 9, 6 };

  public static final int STATE_STOPPED = 0;
  public static final int STATE_PAUSED = 1;
  public static final int STATE_PLAYING = 2;

  private int state = STATE_STOPPED;

  private String speaker =
  "\0\0\0\0\0\357\0\0\357U\27" +
  "\36\0\0\0\0\357\357\0\0" +
  "\0\357U\30\0\0\0\357\0\357" +
  "\0\357\0\0\357\23\357" +
  "\357\357\0\34\357\0Z\357\0" +
  "\357\\\357\0)+F\357\0\0\357" +
  "\0\357r\357Ibz\221\357" +
  "\0\0\357\0\357r\357\357\357" +
  "\276\323\357\0Z\357\0\357" +
  "\\\0\0\0\357\357\357\0" +
  "\357\0\0\357\0\0\0\0\0\357" +
  "\357\0\0\0\357\\\0\0\0" +
  "\0\0\0\357\0\0\357\\\0\0";
  private Image speakerImg;

  private Vector listeners = new Vector();
  
  public Status(Component comp) {
    int[] pixels = new int[12*10];
    component = comp;

    for (int i=0; i<120; i++) {
      pixels[i] = 0xff000000 |
                  (speaker.charAt(i) << 16) |
                  (speaker.charAt(i) << 8) |
                  (speaker.charAt(i));
    }
    speakerImg = comp.getToolkit().createImage(new MemoryImageSource (12, 10, pixels, 0, 12));
    button1Color = Color.black;
    button2Color = Color.black;
  }

  public void addStatusListener(StatusListener l) {
    listeners.add (l);
  }
  public void removeStatusListener(StatusListener l) {
    listeners.remove (l);
  }
  public void notifyNewState(int newState) {
    for (Enumeration e = listeners.elements(); e.hasMoreElements();) {
      ((StatusListener)e.nextElement()).newState(newState);
    }
  }

  public void update(Graphics g) {
    paint(g);
  }

  public void paint(Graphics g) {
    if (!isVisible())
      return;

    r = getBounds();

    Image img = component.createImage(r.width, r.height); 
    Graphics g2 = img.getGraphics();

    g2.setColor(Color.darkGray);
    g2.setFont(font);
    g2.drawRect(0, 0, r.width-1, r.height-1);
    g2.setColor(Color.black);
    g2.fillRect(1, 1, r.width-2, r.height-2);
    g2.setColor(Color.white);
    if (havePercent) {
      g2.drawString(""+bufferPercent+"%", r.width-38, r.height-2);
    }
    if (seekable) {
      /* button 1 */
      g2.setColor(Color.darkGray);
      g2.drawRect(1, 1, 10, 10);
      g2.setColor(button1Color);
      g2.fillRect(2, 2, 9, 9);
      if (state == STATE_PLAYING) {
        g2.setColor(Color.darkGray);
        g2.fillRect(4, 4, 2, 5);
        g2.fillRect(7, 4, 2, 5);
      }
      else {
        g2.setColor(Color.darkGray);
        g2.fillPolygon(triangleX, triangleY, 3);
      }

      /* button 2 */
      g2.setColor(Color.darkGray);
      g2.drawRect(13, 1, 10, 10);
      g2.setColor(button2Color);
      g2.fillRect(14, 2, 9, 9);
      g2.setColor(Color.darkGray);
      g2.fillRect(16, 4, 5, 5);
      if (state == STATE_STOPPED) {
        if (message != null) {
          g2.setColor(Color.white);
          g2.drawString(message, 27, r.height-2);
        } 
      }
      else {
        g2.setColor(Color.darkGray);
        g2.drawRect(27, 2, r.width-45, 8);
      }
    }
    else {
      if (message != null) {
        g2.setColor(Color.white);
        g2.drawString(message, 2, r.height-2);
      } 
    }
    if (haveAudio) {
      g2.drawImage(speakerImg,r.width-12,r.height-11,null);
    }
    g.drawImage(img,r.x,r.y,null);
    img.flush();
  }

  public void setBufferPercent(int bp)
  {
    bufferPercent = bp;
    component.repaint();
  }
  public void setMessage(String m)
  {
    message = m;
    component.repaint();
  }
  public void setHaveAudio(boolean a)
  {
    haveAudio = a;
    component.repaint();
  }
  public void setHavePercent(boolean p)
  {
    havePercent = p;
    component.repaint();
  }
  public void setSeekable(boolean s)
  {
    seekable = s;
    component.repaint();
  }
  public void setState(int aState)
  {
    state = aState;
    component.repaint();
  }

  private boolean intersectButton1 (MouseEvent e) {
   return (e.getX() >= 0 && e.getX() <= 10 && e.getY() > 0 && e.getY() <= 10);
  }
  private boolean intersectButton2 (MouseEvent e) {
   return (e.getX() >= 12 && e.getX() <= 22 && e.getY() > 0 && e.getY() <= 10);
  }
  public void mouseClicked(MouseEvent e){}
  public void mouseEntered(MouseEvent e) {}
  public void mouseExited(MouseEvent e) {}
  public void mousePressed(MouseEvent e) {}
  public void mouseReleased(MouseEvent e)
  {
    if (seekable) {
      e.translatePoint (-1, -1);
      if (intersectButton1 (e)) {
        if (state == STATE_PLAYING) {
	  state = STATE_PAUSED;
	  notifyNewState (state);
	}
        else if (state == STATE_PAUSED) {
	  state = STATE_PLAYING;
	  notifyNewState (state);
	}
      }
      else if (intersectButton2 (e)) {
        state = STATE_STOPPED;
        notifyNewState (state);
      }
      component.repaint();
    }
  }
  public void mouseDragged(MouseEvent e){}
  public void mouseMoved(MouseEvent e)
  {
    if (seekable) {
      e.translatePoint (-1, -1);
      if (intersectButton1 (e)) {
        button1Color = Color.gray;
        button2Color = Color.black;
      }
      else if (intersectButton2 (e)) {
        button1Color = Color.black;
        button2Color = Color.gray;
      }
      else {
        button2Color = Color.black;
        button1Color = Color.black;
      }
      component.repaint();
    }
  }
}
