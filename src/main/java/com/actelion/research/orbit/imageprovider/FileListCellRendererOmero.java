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

package com.actelion.research.orbit.imageprovider;

import com.actelion.research.orbit.beans.RawDataFile;
import com.actelion.research.orbit.gui.IFileListCellRenderer;
import com.actelion.research.orbit.utils.RawUtilsCommon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FileListCellRendererOmero extends JLabel implements IFileListCellRenderer {

    protected long maxHashSize = 500;
    protected int thumbnailWidth = 100;
    protected int thumbnailHeight = 80;
    protected ThreadLocal<SimpleDateFormat> dateFormat = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("dd.MM.yyyy");
        }
    };
    protected Map<URL, ImageIcon> iconHash = new ConcurrentHashMap<URL, ImageIcon>();
    protected JList list = null;
    protected ImageIcon dummyThn = new ImageIcon(new BufferedImage(thumbnailWidth, thumbnailHeight, BufferedImage.TYPE_INT_RGB));


    public FileListCellRendererOmero() {
        dummyThn.getImage().getGraphics().setColor(Color.white);
        dummyThn.getImage().getGraphics().fillRect(0, 0, dummyThn.getIconWidth(), dummyThn.getIconHeight());
    }

    @Override
    public void refreshThnWorker() {

    }

    @Override
    public void clearIconHash() {
        iconHash.clear();
    }

    public Map<URL, ImageIcon> getIconHash() {
        return iconHash;
    }

    @Override
    public void setIconWidth(int iconWidth) {
        thumbnailWidth = iconWidth / 2;
        thumbnailHeight = (int) (thumbnailWidth * 0.75d);
        //refreshThnWorker();
    }

    @Override
    public Component getListCellRendererComponent(
            JList list,
            Object value,            // value to display
            int index,               // cell index
            boolean isSelected,      // is the cell selected
            boolean cellHasFocus)    // the list and the cell have the focus
    {
        this.list = list;
        String s = value.toString();
        boolean hasLinkedChannels = false;
        if (value instanceof RawDataFile) {
            RawDataFile rdf = (RawDataFile) value;
            hasLinkedChannels = rdf.isFlagBit(RawDataFile.Flag_HAS_LINKED_CHANNELS);
            s = rdf.getFileName() +
                    " (" + RawUtilsCommon.formatFileSize(rdf.getFileSize()) + ")";
            String rd = " ref date missing";
            if (rdf.getReferenceDate() != null) {
                rd = " [" + dateFormat.get().format(rdf.getReferenceDate()) + "]";
            }
            s += rd;

            // Icon
            ImageIcon icon = dummyThn;
            // TODO: load icon
            setIcon(icon);

        }

        setText(s);

        setBorder(new EmptyBorder(3, 3, 3, 3));

        if (isSelected) {
            setBackground(list.getSelectionBackground());
            setForeground(list.getSelectionForeground());
            if (hasLinkedChannels) setBackground(Color.blue);
        } else {
            setBackground(list.getBackground());
            setForeground(list.getForeground());
            if (hasLinkedChannels) setBackground(Color.green);
        }
        setEnabled(list.isEnabled());
        setFont(list.getFont());
        setOpaque(true);

        return this;
    }

    @Override
    public void close() throws IOException {

    }
}
