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

import java.awt.image.*;

public class YUVBuffer implements ImageProducer {

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
    private boolean newPixels = true;
    private ColorModel colorModel = ColorModel.getRGBdefault();

    public void addConsumer(ImageConsumer ic) {
    }

    public boolean isConsumer(ImageConsumer ic) {
        return false;
    }

    public void removeConsumer(ImageConsumer ic) {
    }

    public void requestTopDownLeftRightResend(ImageConsumer ic) {
    }

    public void startProduction(ImageConsumer ic) {
        ic.setColorModel(colorModel);
        ic.setHints(ImageConsumer.TOPDOWNLEFTRIGHT |
                ImageConsumer.COMPLETESCANLINES |
                ImageConsumer.SINGLEFRAME |
                ImageConsumer.SINGLEPASS);
        ic.setDimensions(y_width, y_height);
        prepareRGBData(0, 0, y_width, y_height);
        ic.setPixels(0, 0, y_width, y_height, colorModel, pixels, 0, y_width);
        ic.imageComplete(ImageConsumer.STATICIMAGEDONE);
    }

    private synchronized void prepareRGBData(int x, int y, int width, int height) {
        if (!newPixels) {
            return;
        }

        int size = width * height;

        try {
            if (size != pix_size) {
                pixels = new int[size];
                pix_size = size;
            }
            YUVtoRGB(x, y, width, height);
        } catch (Throwable t) {
            /* ignore */
        }
        newPixels = false;
    }

    public synchronized void newPixels() {
        newPixels = true;
    }

    public Object getObject(int x, int y, int width, int height) {
        return this;
    }
    
    private void YUVtoRGB(int x, int y, int width, int height) {

        /*
         * this modified version of the original YUVtoRGB was
         * provided by Ilan and Yaniv Ben Hagai.
         *
         * additional thanks to Gumboot for helping with making this
         * code perform better.
         */

        // Set up starting values for YUV pointers
        int YPtr = y_offset + x + y * (y_stride);
        int YPtr2 = YPtr + y_stride;
        int UPtr = u_offset + x / 2 + (y / 2) * (uv_stride);
        int VPtr = v_offset + x / 2 + (y / 2) * (uv_stride);
        int RGBPtr = 0;
        int RGBPtr2 = width;
        int width2 = width / 2;
        int height2 = height / 2;

        // Set the line step for the Y and UV planes and YPtr2
        int YStep = y_stride * 2 - (width2) * 2;
        int UVStep = uv_stride - (width2);
        int RGBStep = width;

        for (int i = 0; i < height2; i++) {
            for (int j = 0; j < width2; j++) {
                int D, E, r, g, b, t1, t2, t3, t4;

                D = data[UPtr++] - 128;
                E = data[VPtr++] - 128;

                t1 = 298 * (data[YPtr] - 16);
                t2 = 409 * E + 128;
                t3 = (100 * D) + (208 * E) - 128;
                t4 = 516 * D + 128;

                r = (t1 + t2) >> 8;
                g = (t1 - t3) >> 8;
                b = (t1 + t4) >> 8;

                // retrieve data for next pixel now, hide latency?
                t1 = 298 * (data[YPtr + 1] - 16);

                // pack pixel
                pixels[RGBPtr] =
                        ((clamp255(r) << 16) + (clamp255(g) << 8) + clamp255(b)) | 0xff000000;

                r = (t1 + t2) >> 8;
                g = (t1 - t3) >> 8;
                b = (t1 + t4) >> 8;

                // retrieve data for next pixel now, hide latency?
                t1 = 298 * (data[YPtr2] - 16);

                // pack pixel
                pixels[RGBPtr + 1] =
                        ((clamp255(r) << 16) + (clamp255(g) << 8) + clamp255(b)) | 0xff000000;


                r = (t1 + t2) >> 8;
                g = (t1 - t3) >> 8;
                b = (t1 + t4) >> 8;

                // retrieve data for next pixel now, hide latency?
                t1 = 298 * (data[YPtr2 + 1] - 16);

                // pack pixel
                pixels[RGBPtr2] =
                        ((clamp255(r) << 16) + (clamp255(g) << 8) + clamp255(b)) | 0xff000000;

                
                r = (t1 + t2) >> 8;
                g = (t1 - t3) >> 8;
                b = (t1 + t4) >> 8;

                // pack pixel
                pixels[RGBPtr2 + 1] =
                        ((clamp255(r) << 16) + (clamp255(g) << 8) + clamp255(b)) | 0xff000000;

                YPtr += 2;
                YPtr2 += 2;
                RGBPtr += 2;
                RGBPtr2 += 2;
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
        val = -(255 + ((val >> (31)) & val));
        return (short) -((val >> 31) & val);
    }

}
