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

import com.actelion.research.orbit.beans.RawDataFile;
import com.actelion.research.orbit.dal.IOrbitImage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.File;

public class ReaderTestOmero {
    public static void main(String[] args) throws Exception {

        int id = 204; //219;
        ImageProviderOmero ip = new ImageProviderOmero();
        ip.authenticateUser("root","omero");
        long group = ip.getImageGroup(id);
        RawDataFile rdf = ip.LoadRawDataFile(id);
        IOrbitImage io = ip.createOrbitImage(rdf,0);
        System.out.println(io.getFilename()+" wxh: "+io.getWidth()+" x "+io.getHeight());
        Raster raster = io.getTileData(0,0);

        WritableRaster writableRaster = raster.createCompatibleWritableRaster(0,0,512,512);
        writableRaster.setDataElements(0, 0, raster);
        writableRaster = writableRaster.createWritableTranslatedChild(0,0);

        BufferedImage bi = new BufferedImage(io.getColorModel(), writableRaster, false,null );
        ImageIO.write(bi,"png",new File("d:/test.png"));

        
        ip.close();



    }


}
