

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

import loci.common.DateTools;
import loci.formats.*;
import loci.formats.meta.MetadataStore;
import ome.xml.model.primitives.Timestamp;
import omero.RString;
import omero.RTime;
import omero.ServerError;
import omero.api.IPixelsPrx;
import omero.api.RawPixelsStorePrx;
import omero.gateway.exception.DSAccessException;
import omero.gateway.exception.DSOutOfServiceException;
import omero.gateway.facility.BrowseFacility;
import omero.gateway.model.ImageData;
import omero.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static ome.formats.model.UnitsFactory.convertLength;
import static ome.formats.model.UnitsFactory.convertTime;

/**
 * Omero reader based on loci.ome.io.OmeroReader but with shared Omero connection.
 *
 * Implementation of {@link loci.formats.IFormatReader}
 * for use in export from an OMERO Beta 4.2.x database.
 *
 */
public class OmeroReaderOrbit extends FormatReader {

    private static final Logger logger = LoggerFactory.getLogger(OmeroReaderOrbit.class);
    private ImageProviderOmero.GatewayAndCtx gatewayAndCtx;
    private int resolution = 0;
    private long groupId = -1;
    private RawPixelsStorePrx store;
    private long imageId;
    private boolean isRGBImage;


    public OmeroReaderOrbit() {
        super("OMEROORBIT", "*");
    }

    // -- IFormatReader methods --

    @Override
    public boolean isThisType(String name, boolean open) {
        return name.startsWith("omeroorbit:");
    }

    @Override
    public byte[] openBytes(int no, byte[] buf, int x, int y, int w, int h)
            throws FormatException, IOException
    {
        FormatTools.assertId(currentId, true, 1);
        FormatTools.checkPlaneNumber(this, no);
        FormatTools.checkBufferSize(this, buf.length, w, h);

        //System.out.println("no,x,y,w,h: "+no+", "+x+", "+y+", "+w+", "+h);
        final int[] zct = FormatTools.getZCTCoords(this, no);
        byte[] plane;
        try {
            plane = store.getTile(zct[0], zct[1], zct[2], x, y, w, h);
        }
        catch (Exception e) {
            // try to renew the store and try again
            try {
                logger.debug("renewing store");
                renewProxy();
                plane = store.getTile(zct[0], zct[1], zct[2], x, y, w, h);
            } catch (Exception e1) {
                throw new FormatException(e1);
            }
        }

        System.arraycopy(plane,0,buf,0,plane.length);
        return plane;
    }

    private void renewProxy() throws DSOutOfServiceException, DSAccessException, ExecutionException, ServerError {
            store = gatewayAndCtx.getGateway().getPixelsStore(gatewayAndCtx.getCtx(groupId));
            BrowseFacility browse = gatewayAndCtx.getGateway().getFacility(BrowseFacility.class);
            ImageData image = browse.getImage(gatewayAndCtx.getCtx(groupId), imageId);
            omero.model.Image img = image.asImage();
            long pixelsId = img.getPixels(0).getId().getValue();
            store.setPixelsId(pixelsId, false);
            int omeroRes = Math.max(0,(store.getResolutionLevels() - 1) - resolution);
            store.setResolutionLevel(omeroRes);
    }


    @Override
    public void setResolution(int no) {
        this.resolution = no;
    }


    @Override
    public int getResolution() {
        return resolution;
    }

    @Override
    public void close(boolean fileOnly) throws IOException {
        super.close(fileOnly);
        if (!fileOnly && store != null) {
            try {
                store.close();
            } catch (Exception e) {
            }
        }
    }

    @Override
    protected void initFile(String id) throws FormatException, IOException {
        LOGGER.debug("OmeroReaderOrbit.initFile({})", id);

        super.initFile(id);

        if (!id.startsWith("omeroorbit:")) {
            throw new IllegalArgumentException("Not an OMERO id: " + id);
        }

        if (gatewayAndCtx == null) {
            throw new FormatException("GatewayAndContext not initialized. Please call setGatewayAndContext() before calling setId().");
        }




        // parse credentials from id string

        LOGGER.info("Parsing image id");

        long iid = -1;

        final String[] tokens = id.substring(11).split("\n");
        for (String token : tokens) {
            final int equals = token.indexOf("=");
            if (equals < 0) continue;
            final String key = token.substring(0, equals);
            final String val = token.substring(equals + 1);
            if (key.equals("iid")) {
                try {
                    iid = Long.parseLong(val);
                }
                catch (NumberFormatException exc) { }
            }
        }

        if (iid < 0) {
            throw new FormatException("Invalid image ID");
        }

        this.imageId = iid;
        try {

            store = gatewayAndCtx.getGateway().getPixelsStore(gatewayAndCtx.getCtx(groupId));

            BrowseFacility browse = gatewayAndCtx.getGateway().getFacility(BrowseFacility.class);
            ImageData image = browse.getImage(gatewayAndCtx.getCtx(groupId), iid);
            omero.model.Image img = image.asImage();

            long pixelsId = img.getPixels(0).getId().getValue();
            store.setPixelsId(pixelsId, false);

            final int sizeX = store.getResolutionDescriptions()[resolution].sizeX;
            final int sizeY = store.getResolutionDescriptions()[resolution].sizeY;

            int omeroRes = Math.max(0,(store.getResolutionLevels() - 1) - resolution);
            logger.trace("trying to set resolution level "+resolution+" / "+omeroRes);
            store.setResolutionLevel(omeroRes);

            long pixId = image.getDefaultPixels().getId();  // to be replaced by pixelsId?
            logger.debug("pixId: "+pixId+" / imageId: "+imageId);
            logger.debug("imageGroup: "+image.getGroupId()+" security context: "+groupId);

            IPixelsPrx pixelService = gatewayAndCtx.getGateway().getPixelsService(gatewayAndCtx.getCtx(groupId));
            Pixels pix = pixelService.retrievePixDescription(pixId);
            logger.debug("pixels: "+pix);
            if (pix==null) throw new FormatException("Error retrieving pixels object for image "+imageId+", pixelsId: "+pixId+" groupId: "+groupId);
            final int sizeZ = pix.getSizeZ()!=null? pix.getSizeZ().getValue():0;
            final int sizeC = pix.getSizeC()!=null? pix.getSizeC().getValue():0;
            final int sizeT = pix.getSizeT()!=null? pix.getSizeT().getValue():0;
            final String pixelType = pix.getPixelsType().getValue().getValue();

            // populate metadata

            LOGGER.debug("Populating metadata");

            CoreMetadata m = core.get(0);
            m.sizeX = sizeX;
            m.sizeY = sizeY;
            m.sizeZ = sizeZ;
            m.sizeC = sizeC;
            m.sizeT = sizeT;
            m.rgb = false;
            m.littleEndian = false;
            m.dimensionOrder = "XYZCT";
            m.imageCount = sizeZ * sizeC * sizeT;
            m.pixelType = FormatTools.pixelTypeFromString(pixelType);
            m.resolutionCount = store.getResolutionLevels();

            logger.trace("Width x Height: "+m.sizeX+" x "+m.sizeY);

            Length x = pix.getPhysicalSizeX();
            Length y = pix.getPhysicalSizeY();
            Length z = pix.getPhysicalSizeZ();
            Time t = pix.getTimeIncrement();


            ome.units.quantity.Time t2 = convertTime(t);
            ome.units.quantity.Length px = convertLength(x);
            ome.units.quantity.Length py = convertLength(y);
            ome.units.quantity.Length pz = convertLength(z);

            RString imageName = img.getName();
            String name = imageName == null ? null : imageName.getValue();

            if (name != null) {
                currentId = name;
            }
            else {
                currentId = "Image ID " + iid;
            }

            RString imgDescription = img.getDescription();
            String description =
                    imgDescription == null ? null : imgDescription.getValue();
            RTime date = img.getAcquisitionDate();

            MetadataStore metadataStore = getMetadataStore();

            MetadataTools.populatePixels(metadataStore, this);
            metadataStore.setImageName(name, 0);
            metadataStore.setImageDescription(description, 0);
            if (date != null) {
                metadataStore.setImageAcquisitionDate(new Timestamp(
                                DateTools.convertDate(date.getValue(), (int) DateTools.UNIX_EPOCH)),
                        0);
            }

            if (px != null && px.value().doubleValue() > 0) {
                metadataStore.setPixelsPhysicalSizeX(px, 0);
            }
            if (py != null && py.value().doubleValue() > 0) {
                metadataStore.setPixelsPhysicalSizeY(py, 0);
            }
            if (pz != null && pz.value().doubleValue() > 0) {
                metadataStore.setPixelsPhysicalSizeZ(pz, 0);
            }
            if (t2 != null) {
                metadataStore.setPixelsTimeIncrement(t2, 0);
            }

            List<Channel> channels = pix.copyChannels();
            for (int c=0; c<channels.size(); c++) {
                LogicalChannel channel = channels.get(c).getLogicalChannel();

                Length emWave = channel.getEmissionWave();
                Length exWave = channel.getExcitationWave();
                Length pinholeSize = channel.getPinHoleSize();
                RString cname = channel.getName();

                ome.units.quantity.Length emission = convertLength(emWave);
                ome.units.quantity.Length excitation = convertLength(exWave);
                String channelName = cname == null ? null : cname.getValue();
                ome.units.quantity.Length pinhole = convertLength(pinholeSize);

                if (channelName != null) {
                    metadataStore.setChannelName(channelName, 0, c);
                }
                if (pinholeSize != null) {
                    metadataStore.setChannelPinholeSize(pinhole, 0, c);
                }
                if (emission != null && emission.value().doubleValue() > 0) {
                    metadataStore.setChannelEmissionWavelength( emission, 0, c);
                }
                if (excitation != null && excitation.value().doubleValue() > 0) {
                    metadataStore.setChannelExcitationWavelength(excitation, 0, c);
                }
            }

            // check if it is a real rgb image (r,g,b, channels, e.g. brightfield)
            isRGBImage = true;
            if (channels.size()==3) {
                LogicalChannel red = channels.get(0).getLogicalChannel();
                LogicalChannel green = channels.get(1).getLogicalChannel();
                LogicalChannel blue = channels.get(2).getLogicalChannel();
                if (red.getName()!=null && red.getName().getValue()!=null && !red.getName().getValue().equalsIgnoreCase("red")) isRGBImage = false;
                else if (green.getName()!=null && green.getName().getValue()!=null && !green.getName().getValue().equalsIgnoreCase("green")) isRGBImage = false;
                else if (blue.getName()!=null && blue.getName().getValue()!=null && !blue.getName().getValue().equalsIgnoreCase("blue")) isRGBImage = false;
            } else isRGBImage = false;
            logger.debug("isRGBImage: "+isRGBImage);

            logger.trace("init end");

        }
        catch (ServerError e) {
            throw new FormatException(e);
        } catch (DSOutOfServiceException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (DSAccessException e) {
            e.printStackTrace();
        }
        finally {
            // don't close store here - it sould be closed from external via close()
        }
    }

    public ImageProviderOmero.GatewayAndCtx getGatewayAndCtx() {
        return gatewayAndCtx;
    }

    public void setGatewayAndCtx(ImageProviderOmero.GatewayAndCtx gatewayAndCtx) {
        this.gatewayAndCtx = gatewayAndCtx;
    }

    public long getGroupId() {
        return groupId;
    }

    public void setGroupId(long groupId) {
          this.groupId = groupId;
    }

    public boolean isRGBImage() {
        return isRGBImage;
    }
}
