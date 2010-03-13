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
import com.fluendo.utils.*;

import java.text.*;
import java.awt.font.*;

class FancyTextRenderer implements TextRenderer {
  public void renderText(Graphics g, Rectangle region, Font font, String text)
  {
      /* This path uses API calls that were not present in Java 1.1 */
      Graphics2D g2 = (Graphics2D)g;

      g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON); 

      AttributedString atext = new AttributedString(text, font.getAttributes());
      AttributedCharacterIterator text_it = atext.getIterator();
      int text_end = text_it.getEndIndex();

      FontRenderContext frc = g2.getFontRenderContext();
      LineBreakMeasurer lbm = new LineBreakMeasurer(text_it, frc);
      float dy = 0.0f;
      //float shadow_dx = Math.max(font_size * 0.075f, 1.0f), shadow_dy = Math.max(font_size * 0.075f, 1.0f);
      //int shadow_dx = (int)(Math.max(font_size * 0.075f, 2)+0.5f), shadow_dy = (int)(Math.max(font_size * 0.075f, 2)+0.5f);
      int shadow_dx=1,shadow_dy=1;
      while (lbm.getPosition() < text_end) {
        TextLayout layout = lbm.nextLayout(region.width);
        dy += layout.getAscent();
        float tw = layout.getAdvance();
        float tx = region.x+((region.width-tw)/2);

        g2.setColor(Color.black);
        layout.draw(g2, tx+shadow_dx, region.y+dy);
        layout.draw(g2, tx-shadow_dx, region.y+dy);
        layout.draw(g2, tx, region.y+dy-shadow_dy);
        layout.draw(g2, tx, region.y+dy+shadow_dy);
        g2.setColor(Color.white);
        layout.draw(g2, tx, region.y+dy);

        dy += layout.getDescent() + layout.getLeading();
      }
    }
}
