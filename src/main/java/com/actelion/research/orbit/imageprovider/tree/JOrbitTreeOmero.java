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

package com.actelion.research.orbit.imageprovider.tree;

import com.actelion.research.orbit.beans.RawData;
import com.actelion.research.orbit.beans.RawDataFile;
import com.actelion.research.orbit.gui.AbstractOrbitTree;
import com.actelion.research.orbit.gui.IOrbitTree;
import com.actelion.research.orbit.gui.IntInputVerifier;
import com.actelion.research.orbit.imageprovider.ImageProviderOmero;
import com.actelion.research.orbit.utils.Logger;
import com.actelion.research.orbit.utils.RawUtilsCommon;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.NumberFormatter;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

public class JOrbitTreeOmero extends AbstractOrbitTree {

    private final static Logger logger = Logger.getLogger(JOrbitTreeOmero.class);
    private List<AbstractOrbitTreeNode> treeNodeTypes;
    private String loginUser = "Guest";
    private boolean favoritTree = false;
    protected boolean invertLeaves = false;
    protected String filter = null;
    private static String RAWDATAFILES_SELECTED = "raw_data_files_selected";
    private ImageProviderOmero imageProviderOmero;
    private final JCheckBox onlyMyEntitiesCB = new JCheckBox("show only my assets", false);
    private final JCheckBox onlySelectedSeriesCB = new JCheckBox("only selected series", false);
    private final Integer current = new Integer(0);
    private final Integer min = new Integer(0);
    private final Integer max = new Integer(9999);
    private final Integer step = new Integer(1);
    private final SpinnerNumberModel spinnerModel = new SpinnerNumberModel(current, min, max, step);
    private final JSpinner seriesSpinner = new JSpinner(spinnerModel);


    public JOrbitTreeOmero(final ImageProviderOmero imageProviderOmero, String rootName, List<AbstractOrbitTreeNode> treeNodeTypes) {
        super(new SortableTreeNode(rootName));
        if (treeNodeTypes == null || treeNodeTypes.size() < 1)
            throw new IllegalArgumentException("treeNodeTypes is null or does not contain any elements");
        this.treeNodeTypes = treeNodeTypes;
        this.imageProviderOmero = imageProviderOmero;

        JFormattedTextField tf = ((JSpinner.DefaultEditor) seriesSpinner.getEditor()).getTextField();
        tf.setInputVerifier(new IntInputVerifier(0,min,max));
        ((NumberFormatter) tf.getFormatter()).setAllowsInvalid(false);

        onlyMyEntitiesCB.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (imageProviderOmero != null) {
                    imageProviderOmero.setOnlyOwnerObjects(onlyMyEntitiesCB.isSelected());
                    refresh();
                    firePropertyChange("SERIES_CHANGED", null, (Integer) seriesSpinner.getValue());
                }
            }
        });

        onlySelectedSeriesCB.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (imageProviderOmero != null) {
                    imageProviderOmero.setListAllSeries(!onlySelectedSeriesCB.isSelected());
                    refresh();
                    firePropertyChange("SERIES_CHANGED", null, (Integer) seriesSpinner.getValue());
                }
            }
        });

        seriesSpinner.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                if (imageProviderOmero != null) {
                    imageProviderOmero.setSelectedSeries((Integer) seriesSpinner.getValue());
                    refresh();
                    firePropertyChange("SERIES_CHANGED", null, (Integer) seriesSpinner.getValue());
                }
            }
        });

        try {
            loadBase();


            addTreeWillExpandListener(this);
            expandRow(0);
            getSelectionModel().addTreeSelectionListener(
                    new TreeSelectionListener() {
                        @Override
                        public void valueChanged(TreeSelectionEvent e) {
                            TreePath path = e.getNewLeadSelectionPath();
                            System.out.println(path);
                        }
                    });
        } catch (SQLException e) {
            logger.error("error loading Orbit tree: " + e.getMessage());
        }

        getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
            @Override
            public void valueChanged(TreeSelectionEvent e) {
                if (getSelectionPath() != null) {
                    final List<RawData> selectedRd = new ArrayList<RawData>();
                    final List<RawDataFile> selectedRdf = new ArrayList<RawDataFile>();
                    for (TreePath tp : getSelectionPaths()) {
                        DefaultMutableTreeNode node = (DefaultMutableTreeNode) tp.getLastPathComponent();
                        Object obj = node.getUserObject();

                        if (obj instanceof TreeNodeDataset) { // and not just a baseRd (so it is a leave)
                            TreeNodeDataset nodeDS = (TreeNodeDataset) obj;
                            List<RawData> nodeRdList = new ArrayList<>();
                            try {
                                nodeRdList.add((RawData) nodeDS.getIdentifier());
                            } catch (Exception e1) {
                                e1.printStackTrace();
                            }

                            if (logger.isTraceEnabled())
                                logger.trace("nodelist size: " + nodeRdList.size());
                            selectedRd.addAll(nodeRdList);

                        }


                    }
                    final int si = selectedRd.size();
                    final int siRDF = selectedRdf.size();
                    if (si > 0) {
                        firePropertyChange(RawUtilsCommon.RAWDATA_SELECTED, null, selectedRd);
                    } else if (siRDF > 0) {
                        firePropertyChange(RAWDATAFILES_SELECTED, null, selectedRdf);     // TODO
                    }

                }
            }
        });
    }

    public void loadBase() throws SQLException {
        // base level
        SortableTreeNode root = (SortableTreeNode) getModel().getRoot();
        List<? extends AbstractOrbitTreeNode> nodeList = this.treeNodeTypes.get(0).getNodes(null);
        for (AbstractOrbitTreeNode node : nodeList) {
            root.add(new SortableTreeNode(node, true) {
                @Override
                public boolean isLeaf() {
                    return false;
                }
            });
        }
    }


    @Override
    public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException {
        if (logger.isTraceEnabled()) {
            logger.trace("expanding " + event.getPath().getLastPathComponent());
        }

        try {
            DefaultMutableTreeNode parent = (DefaultMutableTreeNode) event.getPath().getLastPathComponent();
            for (int i = 1; i < treeNodeTypes.size(); i++) {
                final AbstractOrbitTreeNode nodeType = treeNodeTypes.get(i);
                final boolean isLeaf = (i >= treeNodeTypes.size() - 1);

                if (nodeType.isChildOf(parent.getUserObject())) {
                    AbstractOrbitTreeNode parentNode = (AbstractOrbitTreeNode) parent.getUserObject();
                    List<? extends AbstractOrbitTreeNode> children = nodeType.getNodes(parentNode);
                    for (AbstractOrbitTreeNode child : children) {
                        parent.add(new SortableTreeNode(child) {
                            @Override
                            public boolean isLeaf() {
                                return isLeaf;
                            }
                        });
                    }
                    break;
                }
            }
        } catch (Exception e) {
            throw new ExpandVetoException(event, e.getMessage());
        }
    }

    @Override
    public void treeWillCollapse(TreeExpansionEvent event) throws ExpandVetoException {

    }

    @Override
    public void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
        super.firePropertyChange(propertyName, oldValue, newValue);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {

    }

    @Override
    public List<String> getExpandedState() {
        List<String> expandedList = new ArrayList<String>();
        for (int i = 0; i < getRowCount(); i++) {
            TreePath path = getPathForRow(i);
            if (isExpanded(path)) {
                expandedList.add(path.toString());
            }
        }
        return expandedList;
    }

    public void setExpandedState(List<String> expandState) {
        HashSet<String> expandHash = new HashSet<String>(expandState);
        for (int i = 0; i < getRowCount(); i++) {
            TreePath path = getPathForRow(i);
            if (expandHash.contains(path.toString())) {
                expandPath(path);
            }
        }
    }

    public void refresh() {
        List<String> state = getExpandedState();
        SortableTreeNode root = (SortableTreeNode) getModel().getRoot();
        //collapsePath(getPathForRow(0));
        root.removeAllChildren();
        try {
            loadBase();
        } catch (SQLException e) {
            logger.error("error loading data: " + e.getMessage());
        }
        TreeModel tm = new DefaultTreeModel(root);
        setModel(tm);
        setExpandedState(state);
        invalidate();
    }

    @Override
    public List<IOrbitTree> getRawDataTrees() {
        List<IOrbitTree> list = new ArrayList<IOrbitTree>(1);
        list.add(this);
        return list;
    }

    public boolean isFavoritTree() {
        return favoritTree;
    }

    public void setFavoritTree(boolean favoritTree) {
        this.favoritTree = favoritTree;
    }

    public String getLoginUser() {
        return loginUser;
    }

    public void setLoginUser(String loginUser) {
        this.loginUser = loginUser;
    }

    public boolean isInvertLeaves() {
        return invertLeaves;
    }

    public void setInvertLeaves(boolean invertLeaves) {
        this.invertLeaves = invertLeaves;
        for (AbstractOrbitTreeNode node : treeNodeTypes) {
            // TODO
        }
    }

    public String getFilter() {
        return filter;
    }

    public void setFilter(String filter) {
        this.filter = filter;
        for (AbstractOrbitTreeNode node : treeNodeTypes) {
            // TODO
        }
    }

    @Override
    public JTree getRawTree() {
        return this;
    }


    @Override
    public JComponent createTreeOptionPane() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT,5,0));
        panel.add(onlyMyEntitiesCB);
        panel.add(onlySelectedSeriesCB);
        panel.add(seriesSpinner);
        return panel;
    }

    public static class SortableTreeNode extends DefaultMutableTreeNode {
        private static final long serialVersionUID = 1L;
        private boolean sorted = false;

        public SortableTreeNode(Object userObject) {
            super(userObject);
        }

        public SortableTreeNode(Object userObject, boolean isLeave) {
            super(userObject, isLeave);
        }

        @SuppressWarnings("unchecked")
        public void sort(boolean invertLeaves) {
            // TODO
        }

        @SuppressWarnings("unchecked")
        protected Comparator nodeComparator = new Comparator() {
            public int compare(Object o1, Object o2) {
                return o1.toString().compareToIgnoreCase(o2.toString());
            }
        };
    }

}
