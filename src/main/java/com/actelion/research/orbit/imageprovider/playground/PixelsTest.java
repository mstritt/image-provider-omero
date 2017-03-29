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

package com.actelion.research.orbit.imageprovider.playground;

import omero.api.IPixelsPrx;
import omero.api.RawPixelsStorePrx;
import omero.gateway.Gateway;
import omero.gateway.LoginCredentials;
import omero.gateway.SecurityContext;
import omero.gateway.facility.BrowseFacility;
import omero.gateway.model.ExperimenterData;
import omero.gateway.model.ImageData;
import omero.log.SimpleLogger;
import omero.model.Pixels;

import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

/**
 * Omero connection demo. Enhanced with a connection test to test if an Omero server is available.
 * Reads an image tile.
 * <p>
 * Set imageId and tileX/Y.
 */
public class PixelsTest {

    public static void main(String[] args) throws Exception {

        String hostName = "localhost";
        String userName = "root";
        String password = "password";
        int port = 4064;
        int imageId = 1;     // 251

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

            int group = 0; //(int) user.getGroupId();

            String version = gateway.getServerVersion();
            System.out.println("server version: " + version);
            System.out.println("imageId: "+imageId);
            System.out.println("groupId: "+group);
            SecurityContext ctx = new SecurityContext(group);

            // image infos

            RawPixelsStorePrx store = gateway.getPixelsStore(ctx);

            BrowseFacility browse = gateway.getFacility(BrowseFacility.class);
            ImageData image = browse.getImage(ctx, imageId);
            long pixId = image.getDefaultPixels().getId();
            System.out.println("pixId: "+pixId);
            System.out.println("imageGroup: "+image.getGroupId());
            IPixelsPrx pixelService = gateway.getPixelsService(ctx);
            Pixels pix = pixelService.retrievePixDescription(pixId);
            System.out.println("pixels: "+pix);
            System.out.println(pix!=null?"ok, pixels are fine":"pixels==null -> error");

            store.close();
        } catch (Exception e2) {
            e2.printStackTrace();
        } finally {
            gateway.disconnect();
        }

    }


}
