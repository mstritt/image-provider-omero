/*
 *     Orbit, a versatile image analysis software for biological image-based quantification.
 *     Copyright (C) 2009 - 2017  Idorsia Pharmaceuticals Ltd., Hegenheimermattweg 91, CH-4123 Allschwil, Switzerland.
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

import com.actelion.research.orbit.beans.MinMaxPerChan;
import com.actelion.research.orbit.dal.IOrbitImageMultiChannel;
import com.actelion.research.orbit.exceptions.OrbitImageServletException;
import loci.formats.gui.AWTImageTools;
import omero.ServerError;
import omero.api.IPixelsPrx;
import omero.api.RawPixelsStorePrx;
import omero.api.RenderingEnginePrx;
import omero.gateway.exception.DSAccessException;
import omero.gateway.exception.DSOutOfServiceException;
import omero.gateway.facility.BrowseFacility;
import omero.gateway.model.ImageData;
import omero.gateway.model.PixelsData;
import omero.model.Channel;
import omero.model.Pixels;
import omero.romio.PlaneDef;
import omero.romio.RegionDef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.media.jai.PlanarImage;
import java.awt.image.*;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Implements an IOrbitImage using the Omero backend. Basically it reads image data from Omero tile by tile.
 * Deprecated: Use OrbitImageBioformatsOmero instead.
 */
@Deprecated
public class OmeroImage implements IOrbitImageMultiChannel, Closeable {

    private static final Logger log = LoggerFactory.getLogger(OmeroImage.class);
    private long imageId;

    private String imageName = "";
    private int width;
    private int height;
    private int sizeC;
    private String[] channelNames;
    private float[] channelContributions;
    private float[] hues;
    private int tileWidth;
    private int tileHeight;
    private ColorModel colorModel;
    private SampleModel sampleModel;
    private int numLevels;
    private int level;
    private boolean multiChannel = false;
    //ThreadLocal<RawPixelsStorePrx> stores = new ThreadLocal<>();
    private transient ImageProviderOmero.GatewayAndCtx gatewayAndCtx;
    long pixelsId;
    protected int originalBitsPerSample = 8;
    protected boolean originalWasGrayScale = false;
    private long group = -1;

    public OmeroImage(long imageId, int level, final ImageProviderOmero.GatewayAndCtx gatewayAndCtx) throws ServerError, DSOutOfServiceException, OrbitImageServletException, ExecutionException, DSAccessException {
        this.gatewayAndCtx = gatewayAndCtx;
        this.imageId = imageId;
        this.level = level;
        this.group = ImageProviderOmero.getImageGroupCached(imageId);
        // open image
        BrowseFacility browse = gatewayAndCtx.getGateway().getFacility(BrowseFacility.class);
        ImageData image = browse.getImage(gatewayAndCtx.getCtx(group), imageId);

        this.imageName = image.getName();
        System.out.println("image name: " + imageName);
        IPixelsPrx pixelService = gatewayAndCtx.getGateway().getPixelsService(gatewayAndCtx.getCtx(group));
        Pixels pixels = pixelService.retrievePixDescription(imageId);
        PixelsData pixelsData = image.getDefaultPixels();
//        Pixels pixels = pixelsData.asPixels();
        long pixelsId = pixels.getId().getValue();      // = imageId
        this.sizeC = pixels.getSizeC().getValue();

        log.debug("num channels: "+this.sizeC);
        channelNames = new String[this.sizeC];
        channelContributions = new float[this.sizeC];
        hues = new float[this.sizeC];
        List<Channel> channelList = pixels.copyChannels();
        for (int c=0; c<sizeC; c++) {
            String channelName = channelList.get(c).getLogicalChannel().getName().getValue();
            channelNames[c] = channelName;
            channelContributions[c] = 1f;
            log.debug("channel "+c+": "+channelName);
        }
        this.multiChannel = !isRGB();
        System.out.println("isRGB: "+isRGB());

        RawPixelsStorePrx store = null;
        try {
            store = gatewayAndCtx.getGateway().getPixelsStore(gatewayAndCtx.getCtx(group));
            store.setPixelsId(pixelsId, false);
            this.pixelsId = pixelsId;

            this.width = pixels.getSizeX().getValue();
            this.height = pixels.getSizeY().getValue();
            this.tileWidth = store.getTileSize()[0];
            this.tileHeight = store.getTileSize()[1];

            numLevels = 0; // real levels, excluding overview images

            //if (level > 0)
            {   // mipMap request
                double ratio = store.getResolutionDescriptions()[0].sizeX / (double) store.getResolutionDescriptions()[0].sizeY;
                for (int lev = 0; lev < store.getResolutionLevels(); lev++) {
                    //log.debug("level " + lev + ": " + store.getResolutionDescriptions()[lev].sizeX + "x" + store.getResolutionDescriptions()[lev].sizeY + " ratio: " + (store.getResolutionDescriptions()[lev].sizeX / (double) store.getResolutionDescriptions()[lev].sizeY));
                    if (Math.abs((store.getResolutionDescriptions()[lev].sizeX / (double) store.getResolutionDescriptions()[lev].sizeY) - ratio) < 0.05d) {
                        numLevels++;
                        //log.trace("level " + lev + " accepted");
                    } else {
                        //log.trace("level " + lev + " not accepted");
                    }
                }
                //log.debug("numLevels: " + numLevels);

                this.width = store.getResolutionDescriptions()[level].sizeX;
                this.height = store.getResolutionDescriptions()[level].sizeY;

                if (level >= numLevels) { // or just > ?
                    close();
                    throw new OrbitImageServletException("level " + level + " does not exist for image " + imageName + " id:" + imageId);
                }
            }

            BufferedImage dummyTile = new BufferedImage(this.getTileWidth(), this.getTileHeight(), BufferedImage.TYPE_INT_RGB);
            this.colorModel = dummyTile.getColorModel();
            this.sampleModel = dummyTile.getSampleModel();
        } catch (Exception e1) {
            if (store != null) {
                try {
                    store.close();
                } catch (Exception e2) {
                    //e2.printStackTrace();
                }
            }
        }
    }

    /**
     * Checks if sizeC==3 and channels 'red','green','blue' exist. To be replaced by a Omero property.
     */
    private boolean isRGB() {
        return (sizeC==3) && (channelNames!=null) && (channelNames.length==3) &&
                ((
                    channelNames[0].equalsIgnoreCase("tl brightfield") && channelNames[1].equalsIgnoreCase("tl brightfield") && channelNames[2].equalsIgnoreCase("tl brightfield")
                ) ||
                (
                    (channelNames[0].equalsIgnoreCase("red")||channelNames[1].equalsIgnoreCase("red")||channelNames[2].equalsIgnoreCase("red")) &&
                    (channelNames[0].equalsIgnoreCase("green")||channelNames[1].equalsIgnoreCase("green")||channelNames[2].equalsIgnoreCase("green")) &&
                    (channelNames[0].equalsIgnoreCase("blue")||channelNames[1].equalsIgnoreCase("blue")||channelNames[2].equalsIgnoreCase("blue"))
                ));
    }


    public void close() {
        /*
        try {
			store.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		*/
    }

    @Override
    public String toString() {
        return "OmeroImage{" +
                "imageId=" + imageId +
                ", imageName='" + imageName + '\'' +
                ", width=" + width +
                ", height=" + height +
                ", tileWidth=" + tileWidth +
                ", tileHeight=" + tileHeight +
                ", numLevels=" + numLevels +
                ", colorModel=" + colorModel +
                ", sampleModel=" + sampleModel +
                '}';
    }

    @Override
    public String readInfoString(String s) throws OrbitImageServletException {
        return "isTiled=true,numBands=" + colorModel.getNumColorComponents() + ",height=" + getHeight() + ",tileGridYOffset=0,tileGridXOffset=0,width=" + getWidth() +
                ",bitsPerSample=8,tileHeight=" + getTileHeight() + ",tileWidth=" + getTileWidth() + ",minX=0,numLevels=" + numLevels + ",minY=0";
    }


    /**
     * Load tile data via render engine -> compressed server side
     * Is it possible to get the already-compressed tile from the server directly without recompressing it server-side???
     *
     * @param tileX
     * @param tileY
     * @return Raster of the tile
     */
    public Raster getTileDataRendered(int tileX, int tileY) {
        RenderingEnginePrx proxy = null;
        try {
            proxy = gatewayAndCtx.getGateway().getRenderingService(gatewayAndCtx.getCtx(group), pixelsId);
            proxy.lookupPixels(pixelsId);
            if (!(proxy.lookupRenderingDef(pixelsId))) {
                proxy.resetDefaultSettings(true);
                proxy.lookupRenderingDef(pixelsId);
            }
            proxy.load();
            proxy.setCompressionLevel(0.80f);
            if (numLevels > 0)
                proxy.requiresPixelsPyramid();


            final int levelWidth = proxy.getResolutionDescriptions()[level].sizeX;
            final int levelHeight = proxy.getResolutionDescriptions()[level].sizeY;

            proxy.setResolutionLevel((proxy.getResolutionLevels() - 1) - level);

            // proxy.setActive() to set channels
            // proxy.setModel() to define channel -> color model

            final int tilePosX = tileWidth * tileX;
            final int tilePosY = tileHeight * tileY;
            int tw = Math.min(tileWidth, levelWidth - tilePosX);
            int th = Math.min(tileHeight, levelHeight - tilePosY);

            PlaneDef pDef = new PlaneDef();
            pDef.z = 0;
            pDef.t = 0;
            pDef.slice = omero.romio.XY.value;
            pDef.region = new RegionDef(tilePosX, tilePosY, tw, th);

           // IFormatReader reader = new OmeroReader()

            //		int[] uncompressed = proxy.renderAsPackedInt(pDef);
            byte[] compressed = proxy.renderCompressed(pDef);
            ByteArrayInputStream stream = new ByteArrayInputStream(compressed);
            BufferedImage image = ImageIO.read(stream);
            //BufferedImage image = JAI.create("stream",new ByteArraySeekableStream(compressed)).getAsBufferedImage();
//			PlanarImage image = JAI.create("stream",new ByteArraySeekableStream(compressed));

            BufferedImage bi = new BufferedImage(tileWidth, tileHeight, BufferedImage.TYPE_INT_RGB);
            bi.getGraphics().drawImage(image, 0, 0, null);
            image = bi;
            Raster ra = image.getData().createTranslatedChild(PlanarImage.tileXToX(tileX, image.getTileGridXOffset(), tileWidth), PlanarImage.tileYToY(tileY, image.getTileGridYOffset(), tileHeight));

            return ra;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            try {
                proxy.close();
            } catch (Exception e) {
            }
        }
    }

    /**
     * Load original tile data -> uncompressed from server side ???
     *
     * @param tileX
     * @param tileY
     * @return Raster of the tile
     */
    public Raster getTileData(int tileX, int tileY, boolean analysis) {
          return getTileData(tileX,tileY,null, analysis, null);
    }

    @Override
    public MinMaxPerChan getMinMaxAnalysis() {
        return null;
    }

    @Override
    public boolean is16bit() {
        return false;
    }

    @Override
    public BufferedImage getOverviewImage() {
        return null;
    }


    /**
     * Load original tile data merge to RGB with channelContributions -> uncompressed from server side ???
     *
     * @param tileX
     * @param tileY
     * @return Raster of the tile
     */
    @Override
    public Raster getTileData(int tileX, int tileY, float[] channelContributions, boolean analysis, final float[] analysisHues) {
        RawPixelsStorePrx store = null;
        try {
            //RawPixelsStorePrx store = stores.get();
            store = gatewayAndCtx.getGateway().createPixelsStore(gatewayAndCtx.getCtx(group));
            store.setPixelsId(pixelsId, false);

            final int levelWidth = store.getResolutionDescriptions()[level].sizeX;
            final int levelHeight = store.getResolutionDescriptions()[level].sizeY;
            //log.debug("level: "+level+" levelWidth: "+levelWidth+" levelHeight: "+levelHeight+ " tileWxH: "+store.getTileSize()[0]+"x"+store.getTileSize()[1]);
            store.setResolutionLevel((store.getResolutionLevels() - 1) - level);
            final int tilePosX = tileWidth * tileX;
            final int tilePosY = tileHeight * tileY;
            int tw = Math.min(tileWidth, levelWidth - tilePosX);
            int th = Math.min(tileHeight, levelHeight - tilePosY);

            final int t = 0;
            final int tileZ = 0;

            // getTile(int z, int c, int t, int x, int y, int w, int h);
            BufferedImage image = null;
            if (isMultiChannel()) {
                byte[][] bytesPerChannel = new byte[sizeC][];
                for (int c=0; c<sizeC; c++) {
                    bytesPerChannel[c] = store.getTile(tileZ, c, t, tilePosX, tilePosY, tw, th);
                    boolean interleaved = false;
                    image = createImageMultiChannel(tw,th,bytesPerChannel, interleaved, store.isSigned());
                }
            } else {
                byte[] r = store.getTile(tileZ, 0, t, tilePosX, tilePosY, tw, th);
                byte[] g = store.getTile(tileZ, 1, t, tilePosX, tilePosY, tw, th);
                byte[] b = store.getTile(tileZ, 2, t, tilePosX, tilePosY, tw, th);
                image = createImage(tw, th, r, g, b);
            }

            BufferedImage bi = new BufferedImage(tileWidth, tileHeight, BufferedImage.TYPE_INT_RGB);
            bi.getGraphics().drawImage(image, 0, 0, null);
            image = bi;
            Raster ra = image.getData().createTranslatedChild(PlanarImage.tileXToX(tileX, image.getTileGridXOffset(), tileWidth), PlanarImage.tileYToY(tileY, image.getTileGridYOffset(), tileHeight));
            return ra;

        } catch (ServerError serverError) {
            serverError.printStackTrace();
        } catch (DSOutOfServiceException e) {
            e.printStackTrace();
        } finally {
            try {
                store.close();
            } catch (Exception e) {
            }
        }
        return null;
    }

  

    /**
     * Load preview image
     *
     * @return the image of the highest resolution level
     */
    public BufferedImage getBufferedImage() {
        if (numLevels > 1)
            return getBufferedImage(numLevels - 2);
        else return getBufferedImage(0);
        // 6-0, 5-1, 4-2
    }

    /**
     * Load full image of a specific pyramid level
     *
     * @param level
     * @return the image of the specified level
     */
    public BufferedImage getBufferedImage(int level) {
        RawPixelsStorePrx store = null;
        try {
            BrowseFacility browse = gatewayAndCtx.getGateway().getFacility(BrowseFacility.class);
            ImageData image = browse.getImage(gatewayAndCtx.getCtx(group), imageId);
            int numChannels = image.getDefaultPixels().getSizeC();

            store = gatewayAndCtx.getGateway().createPixelsStore(gatewayAndCtx.getCtx(group));
            store.setPixelsId(pixelsId, false);
            final int levelWidth = store.getResolutionDescriptions()[level].sizeX;
            final int levelHeight = store.getResolutionDescriptions()[level].sizeY;
            store.setResolutionLevel((store.getResolutionLevels() - 1) - level);
            //System.out.println("lwxlh: "+levelWidth+"x"+levelHeight+" planesize: "+store.getPlaneSize());
            BufferedImage bi;
            if (numChannels >= 3) {
                byte[] r = store.getPlane(0, 0, 0);
                byte[] g = store.getPlane(0, 1, 0);
                byte[] b = store.getPlane(0, 2, 0);
                bi = createImage(levelWidth, levelHeight, r, g, b);
            } else {
                // assume grayscale
                byte[] gray = store.getPlane(0, 0, 0);
                bi = createImageGray(levelWidth, levelHeight, gray);
            }
            return bi;
        } catch (ServerError serverError) {
            serverError.printStackTrace();
            return null;
        } catch (DSOutOfServiceException e) {
            e.printStackTrace();
            return null;
        } catch (ExecutionException e) {
            e.printStackTrace();
            return null;
        } catch (DSAccessException e) {
            e.printStackTrace();
            return null;
        } finally {
            try {
                store.close();
            } catch (Exception e) {
            }
        }
    }

    /**
     * Creates a RGB image from channel byte arrays
     *
     * @param width
     * @param height
     * @param r
     * @param g
     * @param b
     * @return an image filled with the r,g,b, data
     */
    private BufferedImage createImage(int width, int height, final byte[] r, final byte[] g, final byte[] b) {
        BufferedImage bim = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        WritableRaster raster = bim.getRaster();
        int[] data = new int[3];
        int cnt = 0;
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++) {
                data[0] = r[cnt];
                data[1] = g[cnt];
                data[2] = b[cnt];
                raster.setPixel(x, y, data);
                cnt++;
            }

        return bim;
    }

    private BufferedImage createImageMultiChannel(int width, int height, final byte[][] bytesPerChannel, boolean interleaved, boolean signed) {
        BufferedImage bim = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int c=0; c<sizeC; c++) {
            BufferedImage bi = AWTImageTools.makeImage(bytesPerChannel[c],width,height,0,interleaved,signed);
        }
        return bim;
    }

    private BufferedImage createImageGray(int width, int height, final byte[] gray) {
        BufferedImage bim = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        WritableRaster raster = bim.getRaster();
        int[] data = new int[1];
        int cnt = 0;
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++) {
                data[0] = gray[cnt];
                raster.setPixel(x, y, data);
                cnt++;
            }

        return bim;
    }


    @Override
    public String getFilename() {
        return imageName + " [" + level + "]";
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public int getTileWidth() {
        return tileWidth;
    }

    @Override
    public int getTileHeight() {
        return tileHeight;
    }

    @Override
    public int getTileGridXOffset() {
        return 0;
    }

    @Override
    public int getTileGridYOffset() {
        return 0;
    }

    @Override
    public int getMinX() {
        return 0;
    }

    @Override
    public int getMinY() {
        return 0;
    }

    @Override
    public int getNumBands() {
        return colorModel.getNumComponents();
    }

    @Override
    public ColorModel getColorModel() {
        return colorModel;
    }

    @Override
    public SampleModel getSampleModel() {
        return sampleModel;
    }

    @Override
    public int getOriginalBitsPerSample() {
        return originalBitsPerSample;
    }

    @Override
    public boolean getOriginalWasGrayScale() {
        return originalWasGrayScale;
    }

    public long getImageId() {
        return imageId;
    }

    public String getImageName() {
        return imageName;
    }

    public int getNumLevels() {
        return numLevels;
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
    public void setChannelContributions(float[] channelContributions) {
        this.channelContributions = channelContributions;
    }

    @Override
    public float[] getHues() {
        return hues;
    }

    @Override
    public void setHues(float[] hues) {
        this.hues = hues;
    }

    public boolean isMultiChannel() {
        return multiChannel;
    }
}
