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

package com.actelion.research.orbit.imageprovider.tree;

import com.actelion.research.orbit.beans.RawData;
import com.actelion.research.orbit.imageprovider.ImageProviderOmero;
import com.actelion.research.orbit.utils.Logger;

import java.util.ArrayList;
import java.util.List;

public class TreeNodeGroup extends AbstractOrbitTreeNode {

    private static Logger logger = Logger.getLogger(TreeNodeGroup.class);
    private RawData group = null;
    private ImageProviderOmero imageProviderOmero;


    public TreeNodeGroup(ImageProviderOmero imageProviderOmero, RawData group) {
        this.imageProviderOmero = imageProviderOmero;
        this.group = group;
    }

    @Override
    public synchronized List<TreeNodeGroup> getNodes(AbstractOrbitTreeNode parent) {
        List<TreeNodeGroup> nodeList = new ArrayList<>();
        List<RawData> rdList = loadGroups();
        for (RawData rd : rdList) {
            nodeList.add(new TreeNodeGroup(imageProviderOmero, rd));
        }
        return nodeList;
    }


    @Override
    public boolean isChildOf(Object parent) {
        return true;
    }


    @Override
    public Object getIdentifier() {
        return group;
    }

    @Override
    public String toString() {
        return group != null ? group.getBioLabJournal() : "";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TreeNodeGroup that = (TreeNodeGroup) o;

        return group != null ? group.equals(that.group) : that.group == null;

    }

    @Override
    public int hashCode() {
        return group != null ? group.hashCode() : 0;
    }


    private List<RawData> loadGroups() {
        return imageProviderOmero.loadGroups();
    }


}
