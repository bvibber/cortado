/********************************************************************
 *                                                                  *
 * THIS FILE IS PART OF THE OggTheora SOFTWARE CODEC SOURCE CODE.   *
 * USE, DISTRIBUTION AND REPRODUCTION OF THIS LIBRARY SOURCE IS     *
 * GOVERNED BY A BSD-STYLE SOURCE LICENSE INCLUDED WITH THIS SOURCE *
 * IN 'COPYING'. PLEASE READ THESE TERMS BEFORE DISTRIBUTING.       *
 *                                                                  *
 * THE Theora SOURCE CODE IS COPYRIGHT (C) 2002-2003                *
 * by the Xiph.Org Foundation http://www.xiph.org/                  *
 *                                                                  *
 ********************************************************************

  function:
  last mod: $Id: theora.h,v 1.18 2004/03/09 06:18:44 msmith Exp $

 ********************************************************************/

package com.fluendo.jtheora;

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

  private void prepareRGBData ()
  {
    int size = y_width * y_height;
    if (size != pix_size) {
      pixels = new int[size];
      pix_size = size;
    }
    YUVtoRGB();
  }

  public int[] getAsRGBData ()
  {
    prepareRGBData();
    return pixels;
  }

  public Image getAsImage (Toolkit toolkit)
  {
    prepareRGBData();

    MemoryImageSource source =
      new MemoryImageSource (y_width, y_height, pixels, 0, y_width);

    return toolkit.createImage (source);
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

  private void YUVtoRGB () 
  {
    int UFactor;
    int VFactor;
    int YVal;
    int RVal;
    int BVal;

    // Set up starting values for YUV pointers
    int YPtr = y_offset;
    int YPtr2 = y_offset + y_stride;
    int UPtr = u_offset;
    int VPtr = v_offset;
    int RGBPtr = 0;
    int RGBPtr2 = y_width;

    // Set the line step for the Y and UV planes and YPtr2
    int YStep = y_stride*2 - (y_width/2)*2;
    int UVStep = uv_stride - (y_width/2);
    int RGBStep = y_width;

    for (int i=0; i < y_height / 2; i++)
    {
      for (int j=0; j < y_width / 2; j++) {
	// groups of four pixels
	UFactor = data[UPtr++] - 128;
	VFactor = data[VPtr++] - 128;

	RVal = UFactor * CR_DIFF_FAC;
	BVal = VFactor * CB_DIFF_FAC;

	UFactor *= CR_FAC;
	VFactor *= CB_FAC;

	YVal = (data[YPtr] + VAL_RANGE) << SHIFT;
        pixels[RGBPtr] = r_tab[(YVal + VFactor)>>SHIFT] |
                         b_tab[(YVal + UFactor)>>SHIFT] |
                         g_tab[(YVal - RVal - BVal)>>SHIFT];

	YVal = (data[YPtr+1] + VAL_RANGE) << SHIFT;
        pixels[RGBPtr+1] = r_tab[(YVal + VFactor)>>SHIFT] |
                           b_tab[(YVal + UFactor)>>SHIFT] |
                           g_tab[(YVal - RVal - BVal)>>SHIFT];

	YVal = (data[YPtr2] + VAL_RANGE) << SHIFT;
        pixels[RGBPtr2] = r_tab[(YVal + VFactor)>>SHIFT] |
                          b_tab[(YVal + UFactor)>>SHIFT] |
                          g_tab[(YVal - RVal - BVal)>>SHIFT];

	YVal = (data[YPtr2+1] + VAL_RANGE) << SHIFT;
        pixels[RGBPtr2+1] = r_tab[(YVal + VFactor)>>SHIFT] |
                            b_tab[(YVal + UFactor)>>SHIFT] |
                            g_tab[(YVal - RVal - BVal)>>SHIFT];
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

  private static int Clamp255(double x) {
    return (int) ((x) < 0 ? 0 : ((x) <= 255 ? (x) : 255));
  }

  private static void SetupRgbYuvAccelerators ()
  {
    int i;

    for( i = 0; i < VAL_RANGE * 3; i++) {
      r_tab[i] = Clamp255(i-VAL_RANGE) << 16;
      g_tab[i] = Clamp255(i-VAL_RANGE) << 8;
      b_tab[i] = Clamp255(i-VAL_RANGE) | 0xff000000;
    }
  }
}
