/*
 *     Orbit, a versatile image analysis software for biological image-based quantification.
 *     Copyright (C) 2009 - 2016 Actelion Pharmaceuticals Ltd., Gewerbestrasse 16, CH-4123 Allschwil, Switzerland.
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

import com.actelion.research.orbit.beans.HCSMetaData;
import com.actelion.research.orbit.beans.RawDataFile;
import com.actelion.research.orbit.dal.IImageProvider;
import com.actelion.research.orbit.lims.LIMSBioSample;
import com.actelion.research.orbit.utils.IRdfToInputStream;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * These methods are not implemented yet in ImageProviderOmero.
 */
public abstract class ImageProviderAbstract implements IImageProvider {


    @Override
    public IRdfToInputStream getRdfToInputStream() {     // better replace with getFullImage (small) as byte[]
        return null;
    }


    @Override
    public boolean DeleteRawAnnotationAllWithType(int rdfId, int annotationType) throws Exception {
        return false;  // not supported
    }


    @Override
    public LIMSBioSample getLIMSBiosample(RawDataFile rdf) throws Exception {
        return null;
    }

    @Override
    public List<LIMSBioSample> LoadByContainerId(String barcode) throws Exception {
        return new ArrayList<>();  // load biosamples based on a container (e.g. slide or tube). Not supported.
    }

    @Override
    public void openBrowser(String username, String password) {
        // the browser is for managing images (e.g. renaming, deleting).
        // not supported here -> use Omero tools for that
    }


    @Override
    public void setPooledConnectionEnabled(boolean enabled) {
        // not needed for omero
    }

    @Override
    public void setDBConnectionName(String name) {
        // not needed for omero
    }

    @Override
    public List<String> getAdminUsers() {
        return new ArrayList<>();
    }


    @Override
    public BufferedImage getLabelImage(RawDataFile rdf) throws Exception {
        return null;
    }

    @Override
    public String getReplacementMetadata(Object result) {
        return null;
    }

    @Override
    public HCSMetaData LoadHCSMetaData(int rdfId) throws Exception {
        return null;
    }

}
