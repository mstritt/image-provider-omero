/*
 *     Orbit, a versatile image analysis software for biological image-based quantification.
 *     Copyright (C) 2009 - 2018 Idorsia Pharmaceuticals Ltd., Hegenheimermattweg 91, CH-4123 Allschwil, Switzerland.
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

import com.actelion.research.orbit.beans.RawAnnotation;
import com.actelion.research.orbit.imageprovider.ImageProviderOmero;

import java.io.IOException;
import java.util.Date;

public class SaveModel {
    public static void main(String[] args) {
        ImageProviderOmero ipo = new ImageProviderOmero();

        RawAnnotation ra = new RawAnnotation();
        ra.setDescription("testAnnotation");
        ra.setModifyDate(new Date());
        ra.setUserId("root");
        ra.setRawAnnotationType(RawAnnotation.ANNOTATION_TYPE_IMAGE);
        ra.setRawDataFileId(1);
        ra.setData(new byte[10]);

        try {
            ipo.authenticateUser("root","password");
            ipo.InsertRawAnnotation(ra);
            System.out.println("ok");

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                ipo.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }
}
