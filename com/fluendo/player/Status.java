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
import java.net.*;
import java.util.*;
import com.fluendo.utils.*;

public class Status extends Component
{
  private int bufferPercent;
  private String message;
  private Rectangle r;
  private Component component;
  private Font font = new Font("SansSerif", Font.PLAIN, 10);

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
    g2.drawString(""+bufferPercent+"%", r.width-38, r.height-2);
    if (message != null) {
      g2.drawString(message, 2, r.height-2);
    }
    g2.drawImage(speakerImg,r.width-12,r.height-11,null);
    g.drawImage(img,r.x,r.y,null);
    img.flush();
  }

  public void setBufferPercent(int bp)
  {
    bufferPercent = bp;
    if (isVisible())
      component.repaint();
  }
  public void setMessage(String m)
  {
    message = m;
    if (isVisible())
      component.repaint();
  }
}
