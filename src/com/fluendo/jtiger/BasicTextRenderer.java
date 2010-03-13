/* JTiger
 * Copyright (C) 2008 ogg.k.ogg.k <ogg.k.ogg.k@googlemail.com>
 *
 * Parts of JTiger are based on code by Wim Taymans <wim@fluendo.com>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public License
 * as published by the Free Software Foundation; either version 2 of
 * the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Library General Public License for more details.
 * 
 * You should have received a copy of the GNU Library General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */

package com.fluendo.jtiger;

import java.awt.*;

class BasicTextRenderer implements TextRenderer {
  public void renderText(Graphics g, Rectangle region, Font font, String text)
  {
      g.setFont(font);
      FontMetrics fm = g.getFontMetrics();
      int tw = fm.stringWidth(text);
      // int shadow_dx = (int)(Math.max(font_size * 0.075f, 2)+0.5f), shadow_dy = (int)(Math.max(font_size * 0.075f, 2)+0.5f);
      int shadow_dx=1,shadow_dy=1;
      int tx = region.x+(region.width-tw)/2;
      int ty = region.y;

      g.setColor(Color.black);
      g.drawString(text, tx+shadow_dx, ty);
      g.drawString(text, tx-shadow_dx, ty);
      g.drawString(text, tx, ty-shadow_dy);
      g.drawString(text, tx, ty+shadow_dy);

      g.setColor(Color.white);
      g.drawString(text, tx, ty);
  }
}
