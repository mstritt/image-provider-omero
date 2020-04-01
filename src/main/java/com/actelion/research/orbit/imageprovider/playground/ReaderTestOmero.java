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

package com.actelion.research.orbit.imageprovider.playground;

import com.actelion.research.orbit.beans.RawDataFile;
import com.actelion.research.orbit.dal.IOrbitImage;
import com.actelion.research.orbit.imageprovider.ImageProviderOmero;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.File;

public class ReaderTestOmero {
    public static void main(String[] args) throws Exception {

        int id = 4; // 101 219;
        ImageProviderOmero ip = new ImageProviderOmero();
        try {
            ip.authenticateUser("root", "omero");
            long group = ip.getImageGroup(id);
            RawDataFile rdf = ip.LoadRawDataFile(id);
            IOrbitImage io = ip.createOrbitImage(rdf, 0);
            System.out.println(io.getFilename() + " wxh: " + io.getWidth() + " x " + io.getHeight());
            Raster raster = io.getTileData(1, 1, false);

            WritableRaster writableRaster = raster.createCompatibleWritableRaster(1 * 512, 1 * 512, 512, 512);
            writableRaster.setDataElements(0, 0, raster);
            writableRaster = writableRaster.createWritableTranslatedChild(0, 0);

            BufferedImage bi = new BufferedImage(io.getColorModel(), writableRaster, false, null);
            ImageIO.write(bi, "png", new File("c:/temp/test.png"));

//            RawAnnotation anno = new RawAnnotation();
//            anno.setUserId("dummy");
//            anno.setDescription("testDesc");
//            anno.setModifyDate(new Date());
//            anno.setData(new byte[]{1, 2, 3});
//            anno.setRawDataFileId(id);
//            int num = ip.InsertRawAnnotation(anno);
//            System.out.println("anno: " + num);
//
//            RawAnnotation ra = ip.LoadRawAnnotation(num);
//            System.out.println(ra);
//
//
//            long startt = System.currentTimeMillis();
//            boolean del = ip.DeleteRawAnnotation(num);
//            long usedt = System.currentTimeMillis()-startt;
//            System.out.println("del: "+del+" usedt: "+usedt);


        } finally {
            ip.close();
        }


    }


}
