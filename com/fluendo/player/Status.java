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
import java.net.*;
import java.util.*;
import com.fluendo.utils.*;

public class Status extends Component
{
  private int bufferPercent;
  private String message;
  private Rectangle r = new Rectangle();
  private Component component;
  private Font font = new Font("SansSerif", Font.PLAIN, 10);
  
  public Status(Component comp) {
    component = comp;
  }

  public void update(Graphics g) {
    paint(g);
  }

  public void paint(Graphics g) {
    if (!isVisible())
      return;

    getBounds(r);

    Image img = component.createImage(r.width, r.height); 
    Graphics g2 = img.getGraphics();

    g2.setColor(Color.darkGray);
    g2.setFont(font);
    g2.drawRect(0, 0, r.width-1, r.height-1);
    g2.setColor(Color.black);
    g2.fillRect(1, 1, r.width-2, r.height-2);
    g2.setColor(Color.white);
    g2.drawString(""+bufferPercent+"%", r.width-30, r.height-2);
    if (message != null) {
      g2.drawString(message, 2, r.height-2);
    }
    g.drawImage(img,r.x,r.y,null);
    img.flush();
  }

  public void setBufferPercent(int bp)
  {
    bufferPercent = bp;
  }
  public void setMessage(String m)
  {
    message = m;
  }
}
