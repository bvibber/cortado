/* Jheora
 * Copyright (C) 2004 Fluendo S.L.
 *  
 * Written by: 2004 Wim Taymans <wim@fluendo.com>
 *   
 * Many thanks to 
 *   The Xiph.Org Foundation http://www.xiph.org/
 * Jheora was based on their Theora reference decoder.
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

package com.fluendo.jheora;

import java.awt.*;
import java.awt.image.*;

public class YUVBuffer 
{
  public int y_width;
  public int y_height;
  public int y_stride;

  public int uv_width;
  public int uv_height;
  public int uv_stride;

  public short[] data;
  public int y_offset;
  public int u_offset;
  public int v_offset;

  private int[] pixels;
  private int pix_size;
  private MemoryImageSource source;
  private Image image;

  public static final int MODE_NEW = 0;
  public static final int MODE_ANIMATED = 1;
  public static final int MODE_COPY = 2;

  private int mode = MODE_COPY;

  private void prepareRGBData (Toolkit toolkit, int x, int y, int width, int height)
  {
    int size = width * height;

    if (size != pix_size) {
      pixels = new int[size];
      pix_size = size;
      if (mode == MODE_NEW || mode == MODE_ANIMATED) {
        source = new MemoryImageSource (width, height, ColorModel.getRGBdefault(), pixels, 0, width);

        if (mode == MODE_ANIMATED) {
          source.setAnimated(true);
          source.setFullBufferUpdates(true);

          System.out.println("created image source");
          if (toolkit != null) {
            image = toolkit.createImage (source);
	    if (image != null)
              System.out.println("created image");
          }
	}
      }
    }
    YUVtoRGB(x, y, width, height);
  }

  public int[] getAsRGBData ()
  {
    prepareRGBData(null, 0, 0, y_width, y_height);

    return pixels;
  }

  public void setMode (int mode) {
    this.mode = mode;
  }

  public Image getAsImage (Toolkit toolkit, int x, int y, int width, int height)
  {
    prepareRGBData(toolkit, x, y, width, height);

    switch (mode) {
      case MODE_ANIMATED:
        source.newPixels(x, y, width, height, true);
        break;
      case MODE_NEW:
        image = toolkit.createImage (source);
        break;
      case MODE_COPY:
        int[] newPixels = new int[pixels.length];
        System.arraycopy (pixels, 0, newPixels, 0, pixels.length);
        MemoryImageSource newSource = 
    		new MemoryImageSource (width, height, ColorModel.getRGBdefault(), newPixels, 0, width);
        image = toolkit.createImage (newSource);
        break;
      default:
        break;
    }
    return image;
  }

  private static final int VAL_RANGE = 256;
  private static final int SHIFT = 16;

  private static final int CR_FAC = (int) (1.402 * (1<<SHIFT));
  private static final int CB_FAC = (int) (1.772 * (1<<SHIFT));
  private static final int CR_DIFF_FAC = (int) (0.71414 * (1<<SHIFT));
  private static final int CB_DIFF_FAC = (int) (0.34414 * (1<<SHIFT));

  private static int[] r_tab = new int[VAL_RANGE * 3];
  private static int[] g_tab = new int[VAL_RANGE * 3];
  private static int[] b_tab = new int[VAL_RANGE * 3];

  static {
    SetupRgbYuvAccelerators ();
  }

  private void Raw () 
  {
    int off, y = 0;

    off = y_offset;
    for (int i=0; i<y_height; i++) {
      for (int j=0; j<y_width; j++) {
        int pixel = data[off+j];
        pixels[y++] = (pixel<<24) | (pixel<<16) | (pixel<<8) | pixel;
      }
      off += y_stride;
    }
    off = u_offset;
    for (int i=0; i<y_height/2; i++) {
      for (int j=0; j<y_width/2; j++) {
        int pixel = data[off+j];
        pixels[y++] = (pixel<<24) | (pixel<<16) | (pixel<<8) | pixel;
      }
      off += uv_stride;
    }
    off = v_offset;
    for (int i=0; i<y_height/2; i++) {
      for (int j=0; j<y_width/2; j++) {
        int pixel = data[off+j];
        pixels[y++] = (pixel<<24) | (pixel<<16) | (pixel<<8) | pixel;
      }
      off += uv_stride;
    }
  }

  private void YUVtoRGB (int x, int y, int width, int height) 
  {
    int UFactor;
    int VFactor;
    int YVal;
    int GFactor;

    // Set up starting values for YUV pointers
    int YPtr = y_offset + x + y*(y_stride);
    int YPtr2 = YPtr + y_stride;
    int UPtr = u_offset + x/2 + (y/2)*(uv_stride);
    int VPtr = v_offset + x/2 + (y/2)*(uv_stride);
    int RGBPtr = 0;
    int RGBPtr2 = width;

    // Set the line step for the Y and UV planes and YPtr2
    int YStep = y_stride*2 - (width/2)*2;
    int UVStep = uv_stride - (width/2);
    int RGBStep = width;

    for (int i=0; i < height / 2; i++)
    {
      for (int j=0; j < width / 2; j++) {
	// groups of four pixels
	UFactor = data[UPtr++] - 128;
	VFactor = data[VPtr++] - 128;

	GFactor = UFactor * CR_DIFF_FAC + VFactor * CB_DIFF_FAC - (VAL_RANGE<<SHIFT);
	UFactor = UFactor * CR_FAC + (VAL_RANGE<<SHIFT);
	VFactor = VFactor * CB_FAC + (VAL_RANGE<<SHIFT);

	YVal = data[YPtr] << SHIFT;
        pixels[RGBPtr] = r_tab[(YVal + VFactor)>>SHIFT] |
                         b_tab[(YVal + UFactor)>>SHIFT] |
                         g_tab[(YVal - GFactor)>>SHIFT];

	YVal = data[YPtr+1] << SHIFT;
        pixels[RGBPtr+1] = r_tab[(YVal + VFactor)>>SHIFT] |
                           b_tab[(YVal + UFactor)>>SHIFT] |
                           g_tab[(YVal - GFactor)>>SHIFT];

	YVal = data[YPtr2] << SHIFT;
        pixels[RGBPtr2] = r_tab[(YVal + VFactor)>>SHIFT] |
                          b_tab[(YVal + UFactor)>>SHIFT] |
                          g_tab[(YVal - GFactor)>>SHIFT];

	YVal = data[YPtr2+1] << SHIFT;
        pixels[RGBPtr2+1] = r_tab[(YVal + VFactor)>>SHIFT] |
                            b_tab[(YVal + UFactor)>>SHIFT] |
                            g_tab[(YVal - GFactor)>>SHIFT];

	YPtr+=2;
	YPtr2+=2;
	RGBPtr+=2;
	RGBPtr2+=2;
      }

      // Increment the various pointers
      YPtr += YStep;
      YPtr2 += YStep;
      UPtr += UVStep;
      VPtr += UVStep;
      RGBPtr += RGBStep;
      RGBPtr2 += RGBStep;
    }
  }

  private static final short clamp255(int val) {
    val -= 255;
    val = -(255+((val>>(31))&val));
    return (short) -((val>>31)&val);
  }

  private static void SetupRgbYuvAccelerators ()
  {
    int i;

    for( i = 0; i < VAL_RANGE * 3; i++) {
      r_tab[i] = clamp255(i-VAL_RANGE) << 16;
      g_tab[i] = clamp255(i-VAL_RANGE) << 8;
      b_tab[i] = clamp255(i-VAL_RANGE) | 0xff000000;
    }
  }
}
