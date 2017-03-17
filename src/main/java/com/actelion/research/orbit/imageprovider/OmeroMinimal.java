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

import omero.api.RawPixelsStorePrx;
import omero.gateway.Gateway;
import omero.gateway.LoginCredentials;
import omero.gateway.SecurityContext;
import omero.gateway.facility.BrowseFacility;
import omero.gateway.model.ExperimenterData;
import omero.gateway.model.ImageData;
import omero.gateway.model.PixelsData;
import omero.log.SimpleLogger;

import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

/**
 * Omero connection demo. Enhanced with a connection test to test if an Omero server is available.
 * Reads an image tile.
 * <p>
 * Set imageId and tileX/Y.
 */
public class OmeroMinimal {

    public static void main(String[] args) throws Exception {

        String hostName = "localhost";
        String userName = "root";
        String password = "omero";
        int port = 4064;
        int imageId = 1;
        int tileX = 0;
        int tileY = 0;

        Gateway gateway = null;

        try {
            LoginCredentials cred = new LoginCredentials();
            cred.getServer().setHostname(hostName);
            cred.getServer().setPort(port);
            cred.getUser().setUsername(userName);
            cred.getUser().setPassword(password);

            SimpleLogger simpleLogger = new SimpleLogger();

            gateway = new Gateway(simpleLogger);

            if (!gateway.isNetworkUp(false)) {
                throw new Exception("Network is down");
            }

            boolean connectionOk = false;
            try {
                SocketChannel socketChannel = SocketChannel.open();
                socketChannel.configureBlocking(true);
                connectionOk = socketChannel.connect(new InetSocketAddress(hostName, port));
                socketChannel.close();
            } catch (Exception e) {
                connectionOk = false;
            }
            if (!connectionOk) {
                throw new Exception("cannot connect to Omero server");
            }

            ExperimenterData user;
            user = gateway.connect(cred);

            String version = gateway.getServerVersion();
            System.out.println("server version: " + version);

            SecurityContext ctx = new SecurityContext(user.getGroupId());

            // image infos
            BrowseFacility browse = gateway.getFacility(BrowseFacility.class);
            ImageData image = browse.getImage(ctx, imageId);

            PixelsData pixels = image.getDefaultPixels();
            int sizeZ = pixels.getSizeZ(); // The number of z-sections.
            int sizeT = pixels.getSizeT(); // The number of timepoints.
            int sizeC = pixels.getSizeC(); // The number of channels.
            int sizeX = pixels.getSizeX(); // The number of pixels along the X-axis.
            int sizeY = pixels.getSizeY(); // The number of pixels along the Y-axis.

            System.out.println("dims: " + sizeX + "x" + sizeY);

            // read tile
            int z = 0;
            int t = 0;
            int tw = 256;
            int th = 256;
            int x = tileX * tw;
            int y = tileY * th;

            long pixelsId = pixels.getId();
            RawPixelsStorePrx store = gateway.getPixelsStore(ctx);
            store.setPixelsId(pixelsId, false);
            store.setResolutionLevel(0);
            byte[] r = store.getTile(z, 0, t, x, y, tw, th);
            store.close();
        } catch (Exception e2) {
            e2.printStackTrace();
        } finally {
            gateway.disconnect();
        }

    }

}
