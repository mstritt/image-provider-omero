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

package com.actelion.research.orbit.imageprovider;

import com.actelion.research.orbit.gui.IntegerTextField;
import com.actelion.research.orbit.utils.RawUtilsCommon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Configuration dialog to set values in a OrbitOmero.properties file.
 */
public class OmeroConfigDialog extends JDialog {

    private static final Logger logger = LoggerFactory.getLogger(OmeroConfigDialog.class);
    private final JLabel labServer = new JLabel("Omero Host/IP");
    private final JLabel labPort = new JLabel("Port (usually 4064)");
    private final JLabel labWebPort = new JLabel("Web-Port (usually 4080) [optional]");
    private final JLabel labScaleoutuser = new JLabel("Scaleout Omero User [optional]");
    private final JLabel labSearchlimit = new JLabel("Search Limit");
    private final JTextField tfHost = new JTextField("localhost");
    private final IntegerTextField tfPort = new IntegerTextField(4064,4064,0,99999);
    private final IntegerTextField tfWebPort = new IntegerTextField(4080,4080,0,99999);
    private final JTextField tfScaleoutUser = new JTextField("omero");
    private final IntegerTextField tfSearchLimit = new IntegerTextField(1000,1000,1,99999);
    
    private final JCheckBox cbSSL = new JCheckBox("SSL encryption",true);
    private final JCheckBox cbWebSockets = new JCheckBox("Use WebSockets",false);
    private final JButton btnOk = new JButton("Ok");
    private final JButton btnCancel = new JButton("Cancel");
    private final JButton btnTestConnection = new JButton("Test Connection");
    private final JLabel labTestResult = new JLabel("");
    private final Properties props;
    private final String propsFileName;

    public OmeroConfigDialog(Dialog owner, boolean modal, final String propsFileName) {
        super(owner, modal);
        this.propsFileName = propsFileName;
        this.props = new Properties();
        File propFile = new File(propsFileName);
        if (propFile.exists()) {
            try {
                props.load(new FileInputStream(propsFileName));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        setTitle("Omero Server Configuration");
        setResizable(false);
        getRootPane().setBorder(new EmptyBorder(20, 20, 20, 20));
        
        setLayout(new GridLayout(8,1,20,20));

        tfHost.setHorizontalAlignment(SwingConstants.TRAILING);
        tfScaleoutUser.setHorizontalAlignment(SwingConstants.TRAILING);

        Panel p = new Panel(new GridLayout(1,2,20,20));
        p.add(labServer);
        p.add(tfHost);
        add(p);

        p = new Panel(new GridLayout(1,2,20,20));
        p.add(labPort);
        p.add(tfPort);
        add(p);

        p = new Panel(new GridLayout(1,2,20,20));
        p.add(new JPanel());
        p.add(cbSSL);
        add(p);

        p = new Panel(new GridLayout(1,2,20,20));
        p.add(new JPanel());
        p.add(cbWebSockets);
        add(p);

        p = new Panel(new GridLayout(1,2,20,20));
        p.add(labWebPort);
        p.add(tfWebPort);
        add(p);

        p = new Panel(new GridLayout(1,2,20,20));
        p.add(labScaleoutuser);
        p.add(tfScaleoutUser);
        add(p);

        p = new Panel(new GridLayout(1,2,20,20));
        p.add(labSearchlimit);
        p.add(tfSearchLimit);
        add(p);

        JPanel btnPanel = new JPanel(new BorderLayout());

        JPanel okCancelPanel = new JPanel();
        okCancelPanel.add(btnOk);
        okCancelPanel.add(btnCancel);
        btnPanel.add(okCancelPanel,BorderLayout.EAST);

        JPanel testConnectionPanel = new JPanel();
        testConnectionPanel.add(btnTestConnection);
        testConnectionPanel.add(labTestResult);
        btnPanel.add(testConnectionPanel,BorderLayout.WEST);

        add(btnPanel);

        tfHost.setText(this.props.getProperty(ImageProviderOmero.PROPERTY_OMERO_HOST,"localhost"));
        tfPort.setInt(Integer.parseInt(this.props.getProperty(ImageProviderOmero.PROPERTY_OMERO_PORT,String.valueOf(4064))));
        tfWebPort.setInt(Integer.parseInt(this.props.getProperty(ImageProviderOmero.PROPERTY_OMERO_WEB_PORT,String.valueOf(4080))));
        tfSearchLimit.setInt(Integer.parseInt(this.props.getProperty(ImageProviderOmero.PROPERTY_SEARCH_LIMIT,String.valueOf(1000))));
        tfScaleoutUser.setText(this.props.getProperty(ImageProviderOmero.PROPERTY_OMERO_USER_SCALEOUT,"omero"));
        cbSSL.setSelected(Boolean.parseBoolean(this.props.getProperty(ImageProviderOmero.PROPERTY_USE_SSL,String.valueOf(true))));
        cbWebSockets.setSelected(Boolean.parseBoolean(this.props.getProperty(ImageProviderOmero.PROPERTY_USE_WEBSOCKETS,String.valueOf(false))));

        pack();

        btnOk.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    saveValues();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
                setVisible(false);
            }
        });

        btnCancel.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setVisible(false);
            }
        });

        btnTestConnection.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ImageProviderOmero.useWebSockets = cbWebSockets.isSelected();
                boolean connectionOk = ImageProviderOmero.connectionOk(getHost(),getPort());
                logger.info("connection test: "+connectionOk);
                labTestResult.setText(connectionOk?"OK":"FAILED");
            }
        });

    }


    public void saveValues() throws IOException {
        props.put(ImageProviderOmero.PROPERTY_OMERO_HOST, tfHost.getText().trim());
        props.put(ImageProviderOmero.PROPERTY_OMERO_PORT, String.valueOf(tfPort.getInt()));
        props.put(ImageProviderOmero.PROPERTY_OMERO_WEB_PORT, String.valueOf(tfWebPort.getInt()));
        props.put(ImageProviderOmero.PROPERTY_SEARCH_LIMIT, String.valueOf(tfSearchLimit.getInt()));
        props.put(ImageProviderOmero.PROPERTY_USE_SSL, String.valueOf(cbSSL.isSelected()));
        props.put(ImageProviderOmero.PROPERTY_USE_WEBSOCKETS, String.valueOf(cbWebSockets.isSelected()));
        props.put(ImageProviderOmero.PROPERTY_OMERO_USER_SCALEOUT, tfScaleoutUser.getText().trim());
        props.store(new FileOutputStream(propsFileName),ImageProviderOmero.COMMENT_ORBIT_OMERO_CONFIG);
    }

    public String getHost() {
        return tfHost.getText().trim();
    }

    public int getPort() {
        return tfPort.getInt();
    }

    public int getWebPort() {
        return tfWebPort.getInt();
    }

    public int getSearchLimit() {
        return tfSearchLimit.getInt();
    }

    public String getScaleoutUser() {
        return tfScaleoutUser.getText().trim();
    }
    
    public boolean isUseSSL() {
        return cbSSL.isSelected();
    }

    public boolean isUseWebSockets() {
        return cbWebSockets.isSelected();
    }

    public static void main(String[] args) throws IOException {
        OmeroConfigDialog omeroConfigDialog = new OmeroConfigDialog(null,true, "d:/test.props");
        omeroConfigDialog.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        RawUtilsCommon.centerComponent(omeroConfigDialog);
        omeroConfigDialog.setVisible(true);
        omeroConfigDialog.dispose();

    }
}
