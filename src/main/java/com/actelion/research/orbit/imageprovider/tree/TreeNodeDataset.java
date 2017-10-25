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

package com.actelion.research.orbit.imageprovider.tree;

import com.actelion.research.orbit.beans.RawData;
import com.actelion.research.orbit.imageprovider.ImageProviderOmero;
import com.actelion.research.orbit.utils.Logger;

import java.util.ArrayList;
import java.util.List;

public class TreeNodeDataset extends AbstractOrbitTreeNode {

    private static Logger logger = Logger.getLogger(TreeNodeDataset.class);
    private RawData dataset = null;
    private ImageProviderOmero imageProviderOmero;

    public TreeNodeDataset(ImageProviderOmero imageProviderOmero, RawData dataset) {
        this.imageProviderOmero = imageProviderOmero;
        this.dataset = dataset;
    }


    @Override
    public synchronized List<TreeNodeDataset> getNodes(AbstractOrbitTreeNode parent) {
        List<TreeNodeDataset> nodeList = new ArrayList<>();
        RawData parentIdent = null;
        if (parent != null) parentIdent = (RawData) parent.getIdentifier();
        List<RawData> rdList = loadDatasets(parentIdent);
        for (RawData rd : rdList) {
            nodeList.add(new TreeNodeDataset(imageProviderOmero, rd));
        }
        return nodeList;
    }


    @Override
    public boolean isChildOf(Object parent) {
        return parent instanceof TreeNodeProject;
    }


    @Override
    public Object getIdentifier() {
        return dataset;
    }

    @Override
    public String toString() {
        return dataset != null ? dataset.getBioLabJournal() : "";
    }

    public RawData getDataset() {
        return dataset;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TreeNodeDataset that = (TreeNodeDataset) o;

        return dataset != null ? dataset.equals(that.dataset) : that.dataset == null;

    }

    @Override
    public int hashCode() {
        return dataset != null ? dataset.hashCode() : 0;
    }

    private List<RawData> loadDatasets(RawData project) {
        return imageProviderOmero.loadRawDataDatasets(project);
    }


}
