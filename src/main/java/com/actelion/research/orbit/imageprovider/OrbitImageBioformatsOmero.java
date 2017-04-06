/*
 *     Orbit, a versatile image analysis software for biological image-based quantification.
 *     Copyright (C) 2009 - 2017 Actelion Pharmaceuticals Ltd., Gewerbestrasse 16, CH-4123 Allschwil, Switzerland.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.actelion.research.orbit.imageprovider;

import com.actelion.research.orbit.dal.IOrbitImageMultiChannel;
import com.actelion.research.orbit.exceptions.OrbitImageServletException;
import com.actelion.research.orbit.utils.ChannelToHue;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import loci.common.services.ServiceFactory;
import loci.formats.ChannelMerger;
import loci.formats.FormatException;
import loci.formats.IFormatReader;
import loci.formats.MinMaxCalculator;
import loci.formats.gui.AWTImageTools;
import loci.formats.gui.BufferedImageReader;
import loci.formats.meta.IMetadata;
import loci.formats.services.OMEXMLService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.media.jai.PlanarImage;
import java.awt.*;
import java.awt.image.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class OrbitImageBioformatsOmero implements IOrbitImageMultiChannel {

    private static final Logger logger = LoggerFactory.getLogger(OrbitImageBioformatsOmero.class);
    public static final Cache<ROIDef, BufferedImage> tileCache = CacheBuilder.
            newBuilder().
                    maximumSize(40). // 40 tiles  - should be enough to fill a screen
                    expireAfterWrite(5, TimeUnit.MINUTES).
                    build();
    public static final int TILE_SIZE_DEFAULT = 512;
    final int maxThumbWidth = 300;
    final protected ThreadLocal<BufferedImageReader> reader;
    final List<BufferedImageReader> allReaders = Collections.synchronizedList(new ArrayList<BufferedImageReader>());
    private String filename;
    private String originalFilename;
    private int optimalTileWidth;
    private int optimalTileHeight;
    private int numLevels;
    private int level;
    private boolean interleaved;
    private ColorModel colorModel;
    private SampleModel sampleModel;
    private int numBands;
    private int numBandsOriginal;
    private int originalBitsPerSample;
    private boolean originalWasGrayScale;
    private int tileGridXOffset;
    private int tileGridYOffset;
    private int minX;
    private int minY;
    private long width;
    private long height;
    private boolean is16bit = false;
    protected static final Map<FilenameSeries,MinMaxPerChan> minMaxCache = new ConcurrentHashMap<>();
    private String[] channelNames;
    protected static final ColorModel rgbColorModel = new BufferedImage(1,1,BufferedImage.TYPE_INT_RGB).getColorModel();
    private int series = 0;
    
    private float[] channelContributions = null;
    private float[] hueMap;

    private boolean useCache;
    private ImageProviderOmero.GatewayAndCtx gatewayAndCtx;
    private long imageId;
    private long groupId;

    public OrbitImageBioformatsOmero(final String filename, final int level, final int series, boolean useCache, ImageProviderOmero.GatewayAndCtx gatewayAndCtx, final long imageId, long groupId) throws IOException, FormatException {
        this.originalFilename = filename;
        this.series = series;
        this.filename = filename+"["+level+"]"+" ["+ series +"]";    // level/series here is important because filename is part of key for hashing!
        this.level = level;
        this.useCache = useCache;
        this.gatewayAndCtx = gatewayAndCtx;
        this.imageId = imageId;
        this.groupId = groupId;

        logger.info("bioformats image: "+this.filename);



        reader = new ThreadLocal<BufferedImageReader>() {
            @Override
            protected BufferedImageReader initialValue() {
                try {
                    logger.debug("init bioformats: "+filename+" ["+level+"]"+" ["+ series +"]");
                    IFormatReader r = getIFormatReader(filename, level);
                    ServiceFactory factory = new ServiceFactory();
                    OMEXMLService service = factory.getInstance(OMEXMLService.class);
                    IMetadata meta = service.createOMEXMLMetadata();
                    r.setMetadataStore(meta);
                    r.setId("omeroorbit:iid="+imageId);

                    if (series>=r.getSeriesCount()) {
                        close();
                        throw new OrbitImageServletException("image series " + series + " does not exist for image " + filename+" Max series count: "+r.getSeriesCount());
                    }

                    //setReaderSeries(r, filename);
                    r.setSeries(series);
                    r.setResolution(level);

                    if (mergeRGBChannels(r.isRGB(), r.getSizeC(), r.getRGBChannelCount() ,meta)) {
                        r = new ChannelMerger(r);
                    }

                    BufferedImageReader bir;
                    if (doMergeChannels(r)) {
                        // fluo images

                        if (channelNames == null) {
                            channelNames = new String[r.getSizeC()];
                            for (int c = 0; c < r.getSizeC(); c++) {
                                String name = meta.getChannelName(r.getSeries(), c);
                                if (name == null) {
                                    name = "Channel" + c;
                                }
                                logger.info("channel name " + c + ": " + name);
                                channelNames[c] = name;
                            }
                        }

                        // build hueMap
                        hueMap = getHues();
                    }

                    is16bit = r.getBitsPerPixel()>=16;
                    logger.debug("is16bit: "+is16bit);
                    synchronized (minMaxCache) {
                        final FilenameSeries key = new FilenameSeries(originalFilename,series);
                        if (is16bit && !minMaxCache.containsKey((key)))
                        {
                            IFormatReader r2 = getIFormatReader(filename, r.getResolutionCount() - 1);
                            r2.setId("omeroorbit:iid="+imageId);
                            r2.setSeries(series);
                            MinMaxCalculator minMax = new MinMaxCalculator(r2);
                            int[] nos = minMax.getZCTCoords(0);
                            int z = nos[0], t = nos[2];
                            for (int ic=0; ic<minMax.getSizeC(); ic++) {
                                int idx = minMax.getIndex(z, ic, t);
                                minMax.openBytes(idx); // needed to make min,max available
                            }

                            int[] min = new int[r.getSizeC()];
                            int[] max = new int[r.getSizeC()];
                            for (int c = 0; c < minMax.getSizeC(); c++) {
                               Double tmin = minMax.getChannelKnownMinimum(c);
                                Double tmax = minMax.getChannelKnownMaximum(c);
                                min[c] = (int) tmin.doubleValue();
                                max[c] = (int) tmax.doubleValue();
                                logger.trace("channel: "+c+"  minIntens: "+min[c]+" maxIntens: "+max[c]);
                            }
                            minMaxCache.put(key,new MinMaxPerChan(min,max));
                            r2.close();
                        }
                    }

                    bir = BufferedImageReader.makeBufferedImageReader(r);
                    synchronized (allReaders) {
                        allReaders.add(bir); // remember for closing
                    }
                    return bir;
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }
            }
        };

        if (reader.get()==null) {
            throw new OrbitImageServletException("could not initialize reader for image " + filename);
        }

        numLevels =  reader.get().getResolutionCount();
        //numLevels = getRealResolutionCount(reader.get());

        logger.debug("actual level: "+this.level+" / numLevels: "+numLevels);
        boolean levelOk;
        do {
            levelOk = true;
            if (this.level >= numLevels) {
                close();
                throw new OrbitImageServletException("image pyramid level " + this.level + " does not exist for image " + filename);
            }

            width = reader.get().getSizeX();
            height = reader.get().getSizeY();
            numBandsOriginal = reader.get().getSizeC();

            BufferedImageReader bir = reader.get();
            if (bir.getResolution()!=this.level) bir.setResolution(this.level);
//            optimalTileWidth = bir.getOptimalTileWidth();
//            optimalTileHeight = bir.getOptimalTileHeight();
//            if (optimalTileWidth< TILE_SIZE_DEFAULT && width>=TILE_SIZE_DEFAULT) optimalTileWidth = TILE_SIZE_DEFAULT;
//            if (optimalTileHeight< TILE_SIZE_DEFAULT && height>=TILE_SIZE_DEFAULT) optimalTileHeight = TILE_SIZE_DEFAULT;

            // bir.getOptimalTileWidth() seems to return the full with of the image, thus we use a fixed tilesize here
            optimalTileWidth = TILE_SIZE_DEFAULT;
            optimalTileHeight = TILE_SIZE_DEFAULT;

            logger.debug("tile size: "+optimalTileWidth+" x "+optimalTileHeight);


            originalBitsPerSample = bir.getBitsPerPixel();
            interleaved = bir.isInterleaved();
            try {
                BufferedImage img = getPlane(0, 0, null);
                colorModel = img.getColorModel();
                sampleModel = img.getSampleModel();
                numBands = sampleModel.getNumBands();
                originalWasGrayScale = numBands==1;
                tileGridXOffset = img.getTileGridXOffset();
                tileGridYOffset = img.getTileGridYOffset();
                minX = img.getMinX();
                minY = img.getMinY();
            } catch (Exception e) {
                if (this.level<numLevels-1) {
                    this.level++;
                    synchronized (allReaders) {
                        for (IFormatReader r : allReaders) {
                            r.setResolution(this.level);
                        }
                    }
                    levelOk=false;
                    logger.debug("error loading level "+this.level+" trying next level");
                }  else {
                    e.printStackTrace();
                    throw new OrbitImageServletException("error loading level " + this.level + " (and not more alternative levels available). File: "+filename);
                }
            }

        } while (!levelOk);

        logger.info(filename+" loaded ["+width+" x "+height+"]");
    }

    /**
     * checks if it is not already RGB, sizeC==3 and rgbChannelCount==1
     */
    private boolean mergeRGBChannels(boolean isRGB, int sizeC, int rgbChannelCount,  IMetadata meta) {
        return  (!isRGB) && (sizeC==3) && (rgbChannelCount==1);
    }


    private IFormatReader getIFormatReader(String filename, int level) {
        try {
            OmeroReaderOrbit r = new OmeroReaderOrbit();
            r.setGroupId(groupId);
            r.setGatewayAndCtx(gatewayAndCtx);
            r.setFlattenedResolutions(false);
            r.setResolution(level);
            return r;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }



    private boolean doMergeChannels(IFormatReader r) {
        int c = r.getSizeC();
        return c > 1 && !r.isRGB();
    }


    @Override
    public String readInfoString(String filename) throws OrbitImageServletException {
        return "";
    }

    @Override
    public Raster getTileData(int tileX, int tileY) {
        return getTileData(tileX, tileY, null);
    }

    @Override
    public Raster getTileData(int tileX, int tileY, float[] channelContributions) {
        try {
           BufferedImage img = getPlane(tileX, tileY, channelContributions!=null?channelContributions:this.channelContributions);
           // ensure tiles have always full tileWidth and tileHeight (even at borders)
           if (img.getWidth()!=getTileWidth() || img.getHeight()!=getTileHeight())
           {
               BufferedImage bi = new BufferedImage(getTileWidth(), getTileHeight(), img.getType());
               bi.getGraphics().drawImage(img, 0, 0, null);
               img = bi;
           }

          // set correct bounds
          Raster r = img.getData().createTranslatedChild(PlanarImage.tileXToX(tileX, img.getTileGridXOffset(), getTileWidth()), PlanarImage.tileYToY(tileY, img.getTileGridYOffset(), getTileHeight()));
          return r;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    protected BufferedImage getPlane(int tileX, int tileY, final float[] channelContributions) throws Exception {
        int x = optimalTileWidth * tileX;
        int y = optimalTileHeight * tileY;
        int w = (int) Math.min(optimalTileWidth, width - x);
        int h = (int) Math.min(optimalTileHeight, height - y);
        final FilenameSeries key = new FilenameSeries(originalFilename,series);
        BufferedImageReader bir = reader.get();
        if (bir.getResolution()!=this.level) bir.setResolution(this.level);

        if (!doMergeChannels(bir)) {   // brightfield or just one grayscale channel
            BufferedImage bi = bir.openImage(0,x,y,w,h);
            if (bi!=null && bi.getType()==BufferedImage.TYPE_INT_RGB) return bi;
            else {
                if (is16bit) {
                    int minIntens = minMaxCache.get(key).getMin()[0];
                    int maxIntens = minMaxCache.get(key).getMax()[0];
                    bi = AWTImageTools.autoscale(bi,minIntens,maxIntens);
                }
                BufferedImage biRGB = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                biRGB.getGraphics().drawImage(bi, 0, 0, null);
                return biRGB;
            }
        }
        else {   // fluo -> merge channels
            BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            WritableRaster raster = bi.getRaster();
            int no = 0;
            int sizeC = reader.get().getSizeC();
            int[] nos = reader.get().getZCTCoords(no);
            int z = nos[0], t = nos[2];
            int col;
            int[] pix = new int[3];
            for (int c = 0; c < sizeC; c++) {
                if (isChannelActive(c)) {
                    int index = reader.get().getIndex(z, c, t);
                    ROIDef roiDef = new ROIDef(filename,level, index,x,y,w,h);
                    BufferedImage bit = useCache? OrbitImageBioformatsOmero.tileCache.getIfPresent(roiDef): null;
                    if (bit==null) {
                        bit = reader.get().openImage(index, x, y, w, h);
                        if (useCache) OrbitImageBioformatsOmero.tileCache.put(roiDef,bit);
                    }
                    //bit = AWTImageTools.autoscale(bit,minMaxCache.get(key).getMin()[c] , minMaxCache.get(key).getMax()[c]);
                    Object pixels = AWTImageTools.getPixels(bit, 0, 0, 1, 1);
                    int minIntens = 0;
                    int maxIntens = 256;
                    if (is16bit) {
                        minIntens = minMaxCache.get(key).getMin()[c];
                        maxIntens = minMaxCache.get(key).getMax()[c];
                    }
                    for (int iy = 0; iy < h; iy++) {
                        for (int ix = 0; ix < w; ix++) {
                            int s = 0;
                            for (int b = 0; b < bit.getSampleModel().getNumBands(); b++) {
                                int intens = bit.getRaster().getSample(ix, iy, b);
                                if (is16bit) {
                                    intens = autoscale(intens, pixels, minIntens, maxIntens);
                                }
                                s += intens;
                            }
                            if (channelContributions!=null) {
                                s *= channelContributions[c];
                            }
                            int intens = s <= 255 ? s : 255;
                            pix = raster.getPixel(ix, iy, pix);

                            col = Color.HSBtoRGB(hueMap[c], 1f, intens / 255f);
                            pix[0] += (col >> 16) & 0xFF;    // red
                            pix[1] += (col >> 8) & 0xFF;  // green
                            pix[2] += col & 0xFF;    // blue
                            if (pix[0] > 255) pix[0] = 255;
                            if (pix[1] > 255) pix[1] = 255;
                            if (pix[2] > 255) pix[2] = 255;

                            raster.setPixel(ix, iy, pix);
                        }
                    }
                } // channelActive?
            }  // channels
            return bi;
        }  // fluo
    }

  
    /**
     *  see AWTImageTools.autoscale()
     */
    private int autoscale(int s, Object pixels, int min, int max) {
        int out;
        if (pixels instanceof byte[][]) return s;
        else if (pixels instanceof short[][]) {

            if (s < 0) s += 32767;
            int diff = max - min;
            float dist = (float) (s - min) / diff;

            if (s >= max) out = 255;
            else if (s <= min) out = 0;
            else out = (int) (dist * 256);

            return out;
        } else if (pixels instanceof int[][]) {

            if (s >= max) out = 255;
            else if (s <= min) out = 0;
            else {
                int diff = max - min;
                float dist = (s - min) / diff;
                out = (int) (dist * 256);
            }

            return out;
        } else if (pixels instanceof float[][]) {

            if (s >= max) out = 255;
            else if (s <= min) out = 0;
            else {
                int diff = max - min;
                float dist = (s - min) / diff;
                out = (int) (dist * 256);
            }

            return out;
        } else if (pixels instanceof double[][]) {

            if (s >= max) out = 255;
            else if (s <= min) out = 0;
            else {
                int diff = max - min;
                float dist = (float) (s - min) / diff;
                out = (int) (dist * 256);
            }

            return out;
        }
        return s;
    }



    @Override
    public String getFilename() {
        return filename;
    }

    @Override
    public int getWidth() {
        return (int) width;
    }

    @Override
    public int getHeight() {
        return (int) height;
    }

    @Override
    public int getTileWidth() {
        return optimalTileWidth;
    }

    @Override
    public int getTileHeight() {
        return optimalTileHeight;
    }

    @Override
    public int getTileGridXOffset() {
        return tileGridXOffset;
    }

    @Override
    public int getTileGridYOffset() {
        return tileGridYOffset;
    }

    @Override
    public int getMinX() {
        return minX;
    }

    @Override
    public int getMinY() {
        return minY;
    }

    @Override
    public int getNumBands() {
        return numBands;
    }

    @Override
    public ColorModel getColorModel() {
        return rgbColorModel;
    }

    @Override
    public SampleModel getSampleModel() {
        return rgbColorModel.createCompatibleSampleModel(optimalTileWidth,optimalTileHeight);
    }

    @Override
    public int getOriginalBitsPerSample() {
        return originalBitsPerSample;
    }

    @Override
    public boolean getOriginalWasGrayScale() {
        return originalWasGrayScale;
    }

    @Override
    public void close() throws IOException {
        synchronized (allReaders) {
            for (IFormatReader r: allReaders) {
                try {
                    r.close();
                } catch (Exception e) {
                }
            }
        }
    }




    public int getNumLevels() {
        return numLevels;
    }

    public int getLevel() {
        return level;
    }


    public BufferedImage getThumbnail() {

        long thumbW=1;
        long thumbH=1;

        try {
            IFormatReader ir = null;

            for (int lev=numLevels-1; lev>=0; lev--) {
                if (ir!=null) ir.close();
                ir = getIFormatReader(reader.get().getCurrentFile(),lev);
                ir.setId(reader.get().getCurrentFile());
                ir.setSeries(reader.get().getSeries());
                ir.setResolution(lev);
                thumbW = ir.getSizeX();
                thumbH = ir.getSizeY();
                double diff = Math.abs((thumbW/(double)thumbH) - (width/(double)height));
                logger.trace("thumb lev: "+lev+"  diff: "+diff+"  WxH: "+thumbW+"x"+thumbH);
                if (diff<0.001) break;
            }

            BufferedImageReader bir = BufferedImageReader.makeBufferedImageReader(ir);
            BufferedImage thumb = bir.openImage(0);
            bir.close();

            if (thumbW>maxThumbWidth) {
                int h = (int)(maxThumbWidth * (thumbH/(double)thumbW));
                BufferedImage img = new BufferedImage(maxThumbWidth,h, BufferedImage.TYPE_INT_RGB);
                Graphics2D g2d = (Graphics2D) img.getGraphics();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2d.drawImage(thumb,0,0,maxThumbWidth,h, null);
                thumb = img;
            }
            return thumb;
        } catch (IOException e) {
            e.printStackTrace();
        } catch (FormatException e) {
            e.printStackTrace();
        }
        return null;
    }



    @Override
    public String[] getChannelNames() {
        return channelNames;
    }

    @Override
    public void setChannelNames(String[] channelNames) {
        this.channelNames = channelNames;
    }

    @Override
    public float[] getChannelContributions() {
        return channelContributions;
    }

    @Override
    public void setChannelContributions(float[] contributions) {
        if (contributions==null) {
            channelContributions = null;
            return;
        }
        if (channelContributions==null) {
            channelContributions = new float[contributions.length];
        }
        System.arraycopy(contributions,0,channelContributions, 0, contributions.length);
    }

    @Override
    public float[] getHues() {
        if (channelNames==null) return null;
        float[] hues = new float[channelNames.length];
        for (int c=0; c<channelNames.length; c++) {
            hues[c] = ChannelToHue.getHue(channelNames[c].toLowerCase());
        }
        return hues;
    }

    @Override
    public void setHues(float[] hues) {
         // not implemented
    }

    public boolean isChannelActive(int c) {
        if (channelContributions==null) return true;  // if nothing is set we assume all channels should be active
        if (channelContributions.length <= c) {
            throw new IllegalArgumentException("channelContributions length is "+channelContributions.length+" but requested channel is "+c);
        }
        return Math.abs(channelContributions[c])>0.00001f;
    }

    public class FilenameSeries {
        String filename;
        int series;

        public FilenameSeries(String filename, int series) {
            this.filename = filename;
            this.series = series;
        }

        public String getFilename() {
            return filename;
        }

        public void setFilename(String filename) {
            this.filename = filename;
        }

        public int getSeries() {
            return series;
        }

        public void setSeries(int series) {
            this.series = series;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            FilenameSeries that = (FilenameSeries) o;

            if (series != that.series) return false;
            return filename != null ? filename.equals(that.filename) : that.filename == null;
        }

        @Override
        public int hashCode() {
            int result = filename != null ? filename.hashCode() : 0;
            result = 31 * result + series;
            return result;
        }
    }

    public static class MinMaxPerChan {
        private int[] min;
        private int[] max;

        public MinMaxPerChan(int[] min, int[] max) {
            this.min = min;
            this.max = max;
        }

        public int[] getMin() {
            return min;
        }

        public void setMin(int[] min) {
            this.min = min;
        }

        public int[] getMax() {
            return max;
        }

        public void setMax(int[] max) {
            this.max = max;
        }
    }

    public int getSeries() {
        return series;
    }

    private class ROIDef {
        String filename;
        int level,index,x,y,w,h;

        public ROIDef(String filename, int level, int index, int x, int y, int w, int h) {
            this.filename = filename;
            this.level = level;
            this.index = index;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        @Override
        public String toString() {
            return "ROIDef{" +
                    "filename='" + filename + '\'' +
                    ", level=" + level +
                    ", index=" + index +
                    ", x=" + x +
                    ", y=" + y +
                    ", w=" + w +
                    ", h=" + h +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ROIDef roiDef = (ROIDef) o;

            if (level != roiDef.level) return false;
            if (index != roiDef.index) return false;
            if (x != roiDef.x) return false;
            if (y != roiDef.y) return false;
            if (w != roiDef.w) return false;
            if (h != roiDef.h) return false;
            return filename != null ? filename.equals(roiDef.filename) : roiDef.filename == null;
        }

        @Override
        public int hashCode() {
            int result = filename != null ? filename.hashCode() : 0;
            result = 31 * result + level;
            result = 31 * result + index;
            result = 31 * result + x;
            result = 31 * result + y;
            result = 31 * result + w;
            result = 31 * result + h;
            return result;
        }
    }



}
