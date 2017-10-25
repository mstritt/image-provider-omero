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

public class TreeNodeProject extends AbstractOrbitTreeNode {

    private static Logger logger = Logger.getLogger(TreeNodeProject.class);
    private RawData project = null;
    private ImageProviderOmero imageProviderOmero;


    public TreeNodeProject(ImageProviderOmero imageProviderOmero, RawData project) {
        this.imageProviderOmero = imageProviderOmero;
        this.project = project;
    }

    @Override
    public synchronized List<TreeNodeProject> getNodes(AbstractOrbitTreeNode parent) {
        List<TreeNodeProject> nodeList = new ArrayList<>();
        int group = -1;
        if (parent!=null && parent instanceof TreeNodeGroup) {
            TreeNodeGroup groupNode = (TreeNodeGroup) parent;
            RawData rdGroup = (RawData) groupNode.getIdentifier();
            group = rdGroup.getRawDataId();
        }
        List<RawData> rdList = loadProjects(group);
        for (RawData rd : rdList) {
            nodeList.add(new TreeNodeProject(imageProviderOmero, rd));
        }
        return nodeList;
    }


    @Override
    public boolean isChildOf(Object parent) {
        return parent instanceof TreeNodeGroup;
    }


    @Override
    public Object getIdentifier() {
        return project;
    }

    @Override
    public String toString() {
        return project != null ? project.getBioLabJournal() : "";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TreeNodeProject that = (TreeNodeProject) o;

        return project != null ? project.equals(that.project) : that.project == null;

    }

    @Override
    public int hashCode() {
        return project != null ? project.hashCode() : 0;
    }


    private List<RawData> loadProjects(int group) {
        return imageProviderOmero.loadProjects(group);
    }


}
