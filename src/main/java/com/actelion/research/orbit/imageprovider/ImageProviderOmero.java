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

import com.actelion.research.orbit.beans.*;
import com.actelion.research.orbit.dal.IOrbitImage;
import com.actelion.research.orbit.gui.AbstractOrbitTree;
import com.actelion.research.orbit.gui.IFileListCellRenderer;
import com.actelion.research.orbit.imageprovider.beans.RawDataDataset;
import com.actelion.research.orbit.imageprovider.beans.RawDataGroup;
import com.actelion.research.orbit.imageprovider.beans.RawDataProject;
import com.actelion.research.orbit.imageprovider.tree.JOrbitTreeOmero;
import com.actelion.research.orbit.imageprovider.tree.TreeNodeDataset;
import com.actelion.research.orbit.imageprovider.tree.TreeNodeGroup;
import com.actelion.research.orbit.imageprovider.tree.TreeNodeProject;
import com.actelion.research.orbit.utils.RawMetaFactoryData;
import com.actelion.research.orbit.utils.RawMetaFactoryFile;
import com.actelion.research.orbit.utils.RawUtilsCommon;
import omero.ServerError;
import omero.api.*;
import omero.cmd.Delete2Response;
import omero.gateway.Gateway;
import omero.gateway.LoginCredentials;
import omero.gateway.SecurityContext;
import omero.gateway.exception.DSOutOfServiceException;
import omero.gateway.facility.BrowseFacility;
import omero.gateway.facility.DataManagerFacility;
import omero.gateway.facility.MetadataFacility;
import omero.gateway.model.*;
import omero.log.Logger;
import omero.log.SimpleLogger;
import omero.model.*;
import omero.model.Image;
import omero.model.enums.ChecksumAlgorithmSHA1160;
import omero.model.enums.UnitsLength;
import omero.sys.Parameters;
import omero.sys.ParametersI;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static omero.rtypes.rstring;

public class ImageProviderOmero extends ImageProviderAbstract {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(ImageProviderOmero.class);
    public static final String ORBIT_ANNOTATION_MIMETYPE = "orbit/annotation";
    public static final String ORBIT_PATH = "/orbit";
    public static final String ANNOTATION_NAMESPACE = "orbit/annotation";
    public static final String ANNOTATION_NOFILE_NAMESPACE = "orbit/annotation/nofile"; // special namespace for non file specific annotations
    public static final String ORBIT_METADATA_NAMESPACE = "orbit/metadata";
    private final ConcurrentHashMap<String, Object> hints = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<Long, Long> projectGroupMap = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<Long, Long> datasetGroupMap = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<Long, Long> rdfGroupMap = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<Long, Long> metaGroupMap = new ConcurrentHashMap<>();

    private static final int INC = 16 * 1024;
    private String omeroUser = "";
    private String omeroPassword = "";
    private String omeroUserScaleout = "";
    private String omeroPasswordScaleout = "";
    private String host = "localhost";
    private int port = 4064;
    private int webport = 8080;
    private transient GatewayAndCtx gatewayAndCtx = new GatewayAndCtx();
    protected int searchLimit = 1000;
    protected boolean listAllSeries = true;
    protected final Map<Integer, Map<String, RawMeta>> metaHashRDF = new ConcurrentHashMap<>();
    protected final Map<Integer, Map<String, RawMeta>> metaHashRawData = new ConcurrentHashMap<>();
    private boolean onlyOwnerObjects = false; // show/edit only objects owned by current user (otherwise show/edit all with read/write access)
    private String configFile = "OrbitOmero.properties";
    private boolean useSSL = false;

    public ImageProviderOmero() {
        Properties props = new Properties();
        props.put("OmeroHost", host);
        props.put("OmeroPort", String.valueOf(port));
        props.put("OmeroWebPort", String.valueOf(webport));
        props.put("UseSSL", String.valueOf(useSSL));
        props.put("SearchLimit", String.valueOf(searchLimit));
        props.put("OmeroUserScaleout", "");
        props.put("OmeroPasswordScaleout", "");
        String userDir = System.getProperty("user.dir");
        String userHome = System.getProperty("user.home");

        try {
            boolean loaded = false;
            log.info("searching for " + configFile + " in current directory (" + userDir + ")");
            File config = new File(userDir + File.separator + configFile);
            if (config.exists()) {
                props.load(new FileInputStream(config));
                log.info("using config file: " + config.getAbsolutePath());
                loaded = true;
            }

            if (!loaded)
                log.info("searching for " + configFile + " in user home (" + userHome + ")");
            config = new File(userHome + File.separator + configFile);
            if (!config.exists()) {
                File configTemplate = new File(userHome + File.separator + configFile + ".template");
                try {
                    props.store(new FileOutputStream(configTemplate), "Orbit Omero Config");
                } catch (Exception e) {
                    log.warn("error saving Omero config template to: " + configTemplate.getAbsolutePath());
                }
            } else {
                if (!loaded) {
                    props.load(new FileInputStream(config));
                    log.info("using config file: " + config.getAbsolutePath());
                }
            }

            host = props.getProperty("OmeroHost");
            port = Integer.parseInt(props.getProperty("OmeroPort"));
            webport = Integer.parseInt(props.getProperty("OmeroWebPort"));
            useSSL = Boolean.parseBoolean(props.getProperty("UseSSL"));
            searchLimit = Integer.parseInt(props.getProperty("SearchLimit"));
            omeroUserScaleout = props.getProperty("OmeroUserScaleout");
            omeroPasswordScaleout = props.getProperty("OmeroPasswordScaleout");
        } catch (Exception e) {
            log.error("error loading omero config file", e);
        }

        log.info("Omero host: " + host);
        log.info("Omero port: " + port);
        log.info("Omero web port: " + webport);
        log.info("Omero use SSL: " + useSSL);
        log.info("Search limit: " + searchLimit);
        log.info("Omero User Scaleout: " + omeroUserScaleout);

        boolean connectionOk = false;
        try {
            SocketChannel socketChannel = SocketChannel.open();
            socketChannel.configureBlocking(true);
            connectionOk = socketChannel.connect(new InetSocketAddress(host, port));
            socketChannel.close();
        } catch (Exception e) {
            connectionOk = false;
        }
        if (!connectionOk) {
            String p1 = userDir + File.separator + configFile;
            String p2 = userHome + File.separator + configFile;
            throw new IllegalStateException("Cannot connect to Omero server.\nTried to connect on " + host + ":" + port + ".\nFor a different host/port please modify either\n" + p1 + " (priority) or\n" + p2 + ".\n(You can rename and use "+p1+".template)");
        }

    }

    public void clearMetaRDFHash() {
        metaHashRDF.clear();
    }

    public void clearMetaRawDataHash() {
        metaHashRawData.clear();
    }


    protected Parameters getParameterRead() throws DSOutOfServiceException {
        if (onlyOwnerObjects) {
            long userId = gatewayAndCtx.getGateway().getLoggedInUser().getId();
            ParametersI param = new ParametersI();
            param.exp(omero.rtypes.rlong(userId));
            return param;
        } else {
            return null;
        }
    }


    protected Parameters getParameterWrite() throws DSOutOfServiceException {
        return null;
    }

    /**
     * returns the ownerId if onlyOwnerObjects is true, -1 otherwise (-1 is used for "all projects/datasets")
     *
     * @return long id of the owner
     */
    protected long getOwnerId() throws DSOutOfServiceException {
        return onlyOwnerObjects ? gatewayAndCtx.getGateway().getLoggedInUser().getId() : -1;
    }

    @Override
    public List<RawData> LoadRawDataByBioLabJournal(String elb) throws Exception {
        return loadRawDataDatasets(elb);
    }

    /**
     * Loads a Dataset by id
     * Without transmitting the owner id  because all datasets (with read-right) should be loaded.
     *
     * @param rawDataId
     * @return RawData
     * @throws Exception
     */
    @Override
    public RawData LoadRawData(int rawDataId) throws Exception {
        long group = getDatasetGroup((long)rawDataId);
        BrowseFacility browse = getGatewayAndCtx().getGateway().getFacility(BrowseFacility.class);
        Collection<DatasetData> datasets = browse.getDatasets(getGatewayAndCtx().getCtx(group), /*getOwnerId(),*/ Collections.singleton((long) rawDataId));
        if (datasets != null && datasets.size() > 0) {
            return createRawDataDataset(datasets.iterator().next(),group);
        } else {
            return null;
        }
    }


    /**
     * Updates a Dataset
     * Without transmitting the owner id  because all datasets (with write-right) should be updated.
     *
     * @param rd the rawData object to update
     * @return boolean true/false
     * @throws Exception
     */
    @Override
    public boolean UpdateRawData(RawData rd) throws Exception {
        BrowseFacility browse = getGatewayAndCtx().getGateway().getFacility(BrowseFacility.class);
        DataManagerFacility dm = gatewayAndCtx.getGateway().getFacility(DataManagerFacility.class);
        long group = getDatasetGroup(rd.getRawDataId());
        if (rd instanceof RawDataProject) {
            Collection<ProjectData> projects = browse.getProjects(gatewayAndCtx.getCtx(group), /*getOwnerId(),*/ Collections.singleton((long) rd.getRawDataId()));
            if (projects == null || projects.size() == 0)
                throw new IllegalArgumentException("project with id " + rd.getRawDataId() + " not found");
            ProjectData project = projects.iterator().next();
            project.setName(rd.getBioLabJournal());
            project.setDescription(rd.getDescription());
            dm.updateObject(gatewayAndCtx.getCtx(group), project.asProject(), getParameterWrite());
            return true;
        } else if (rd instanceof RawDataDataset) {
            Collection<DatasetData> datasets = browse.getDatasets(gatewayAndCtx.getCtx(group), /*getOwnerId(),*/ Collections.singleton((long) rd.getRawDataId()));
            if (datasets == null || datasets.size() == 0)
                throw new IllegalArgumentException("dataset with id " + rd.getRawDataId() + " not found");
            DatasetData dataset = datasets.iterator().next();
            dataset.setName(rd.getBioLabJournal());
            dataset.setDescription(rd.getDescription());
            dm.updateObject(gatewayAndCtx.getCtx(group), dataset.asDataset(), getParameterWrite());
            return true;
        } else {
            throw new IllegalArgumentException("RawData object must be instance of RawDataProject or RawDataDataset, but is " + rd.getClass().getName());
        }
    }


    @Override
    public boolean UpdateRawDataFile(RawDataFile rdf) throws Exception {
        long group = getRdfGroup(rdf);
        BrowseFacility browse = gatewayAndCtx.getGateway().getFacility(BrowseFacility.class);
        DataManagerFacility dm = gatewayAndCtx.getGateway().getFacility(DataManagerFacility.class);
        ImageData image = browse.getImage(gatewayAndCtx.getCtx(group), rdf.getRawDataFileId());
        image.setName(rdf.getFileName());
        dm.updateObject(gatewayAndCtx.getCtx(group), image.asImage(), getParameterWrite());
        return true;
    }


    @Override
    public RawDataFile LoadRawDataFile(int rdfId) throws Exception {
        long group = getImageGroup((long)rdfId);
        BrowseFacility browse = gatewayAndCtx.getGateway().getFacility(BrowseFacility.class);
        ImageData image = browse.getImage(gatewayAndCtx.getCtx(group), rdfId);
        return createRawDataFile(image,group);
    }

    @Override
    public List<RawDataFile> LoadRawDataFiles(int rawDataId) throws Exception {
        return loadRdfList(rawDataId, -1);
    }

    @Override
    public List<RawDataFile> LoadRawDataFiles(int rawDataId, int limit) throws Exception {
        return loadRdfList(rawDataId, limit);
    }

    /**
     * Load raw data files which are assigned to a dataset. Filetypes are currently ignored but might be used as filter in future.
     *
     * @param rawDataId
     * @param fileTypes (currently ignored)
     * @param limit
     * @return List<RawDataFile>
     * @throws Exception
     */
    @Override
    public List<RawDataFile> LoadRawDataFiles(int rawDataId, List<String> fileTypes, int limit) throws Exception {
        return loadRdfList(rawDataId, limit);
    }

    /**
     * Search for raw data files. andMode is currently ignored.
     *
     * @param search
     * @param andMode (currently ignored)
     * @return List<RawDataFile>
     * @throws Exception
     */
    @Override
    public List<RawDataFile> LoadRawDataFilesSearch(String search, boolean andMode) throws Exception {
        return LoadRawDataFilesSearchGeneric("%" + search + "%", searchLimit);
    }

    /**
     * Search for raw data files. andMode and fileTypes are currently ignored.
     *
     * @param search
     * @param andMode   (currently ignored)
     * @param limit     search limit (set < 0 to ignore)
     * @param fileTypes currently ignored
     * @return List<RawDataFile>
     * @throws Exception
     */
    @Override
    public List<RawDataFile> LoadRawDataFilesSearch(String search, boolean andMode, int limit, List<String> fileTypes) throws Exception {
        return LoadRawDataFilesSearchGeneric("%" + search + "%", limit);
    }

    @Override
    public List<RawDataFile> LoadRawDataFilesByFilenameStart(String search, boolean andMode, int searchLimit, List<String> fileTypes, String orderHint) throws Exception {
        return LoadRawDataFilesSearchGeneric("%" + search, searchLimit);
    }

    @Override
    public List<RawDataFile> LoadRawDataFilesSearchFast(String search, int limit, List<String> fileTypes) throws Exception {
        return LoadRawDataFilesSearchGeneric("%" + search + "%", limit);
    }

    public List<RawDataFile> LoadRawDataFilesSearchGeneric(String search, int limit) throws Exception {
        List<RawDataFile> rdfList = new ArrayList<>();
        BrowseFacility browse = getGatewayAndCtx().getGateway().getFacility(BrowseFacility.class);
        String query_string = "select i from Image i where UPPER(name) like :search";
        ParametersI p = new ParametersI();
        p.add("search", rstring(search.toUpperCase()));
        p.page(0, limit);
        for (RawData rdGroup: loadGroups()) {
            long group = rdGroup.getRawDataId();
            List<IObject> results = gatewayAndCtx.getGateway().getQueryService(gatewayAndCtx.getCtx(group)).findAllByQuery(query_string, p);
            for (IObject result : results) {
                Image image = (Image) result;
                ImageData imageData = browse.getImage(getGatewayAndCtx().getCtx(group), image.getId().getValue());
                if (imageData.getSeries() == 0 || listAllSeries) {
                    rdfList.add(createRawDataFile(imageData,group));
                }
            }
        }
        Collections.sort(rdfList, new Comparator<RawDataFile>() {
            @Override
            public int compare(RawDataFile o1, RawDataFile o2) {
                return o1.getFileName().compareTo(o1.getFileName());
            }
        });
        return rdfList;
    }

    @Override
    public List<RawDataFile> LoadRawDataFilesByPlateName(String plateName, int plateBatch) throws Exception {
        log.error("plate access is currently not supported");
        return new ArrayList<>();
    }


    @Override
    public List<RawDataFile> browseImages(Object parent) throws Exception {
        return null;
    }

    @Override
    public boolean useCustomBrowseImagesDialog() {
        return false;
    }

    private String getWebserviceBase() {
        //return useSSL?"https://":"http://"+host+":"+webport;
        return "http://" + host + ":" + webport;
    }

    /**
     * Returns the url of the image specified by the rdf.
     *
     * @param rdf
     * @return URL
     * e.g. http://localhost:8080/webgateway/render_image/5
     * http://root:omero@localhost:8080/webgateway/render_image/5
     * see https://www.openmicroscopy.org/site/support/omero5.2/developers/Web/WebGateway.html <p>
     * Authentification does currently not work. User has to be logged in already, e.g. via Omero web interace.
     * Maybe username:password@ can be used?
     * </p>
     */
    @Override
    public URL getRawDataFileUrl(RawDataFile rdf) {
        try {
            return new URL(getWebserviceBase() + "/webgateway/render_image/" + rdf.getRawDataFileId());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Returns the url of the image specified by the rdf.
     *
     * @param rdf
     * @param level currently not taken into account!
     * @return URL
     */
    @Override
    public URL getRawDataFileUrl(RawDataFile rdf, int level) {
        try {
            return new URL(getWebserviceBase() + "/webgateway/render_image/" + rdf.getRawDataFileId()); // TODO: level
        } catch (MalformedURLException e) {
            return null;
        }
    }

    @Override
    public URL getRawDataFileThumbnailUrl(RawDataFile rdf) {
        try {
            return new URL(getWebserviceBase() + "/webgateway/render_thumbnail/" + rdf.getRawDataFileId() + "/200/150"); // width=200, height=150
        } catch (MalformedURLException e) {
            return null;
        }
    }


    public BufferedImage getThumbnail(RawDataFile rdf) throws Exception {
        long group = getRdfGroup(rdf);
        BrowseFacility browse = getGatewayAndCtx().getGateway().getFacility(BrowseFacility.class);
        ImageData imageData = browse.getImage(getGatewayAndCtx().getCtx(group), rdf.getRawDataFileId());
        ThumbnailStorePrx store = gatewayAndCtx.getGateway().getThumbnailService(gatewayAndCtx.getCtx(group));
        PixelsData pixels = imageData.getDefaultPixels();
        store.setPixelsId(pixels.getId());
        byte[] array = store.getThumbnailByLongestSide(omero.rtypes.rint(RawUtilsCommon.THUMBNAIL_WIDTH));
        ByteArrayInputStream stream = new ByteArrayInputStream(array);
        BufferedImage thumbnail = ImageIO.read(stream);
        store.close();
        return thumbnail;
    }

    /**
     * The idea is to get the filesetid of the image and query series 1 image for the fileset.
     * However, I don't know how to query a fileset in Omero. Thus all images for the same dataset are
     * queried and the image with same filesetid and series 1 is returned.
     * <p>
     * It is also not clear which series means overview. I assume 1.
     */
    @Override
    public BufferedImage getOverviewImage(RawDataFile rdf) throws Exception {
        try {
            long group = getRdfGroup(rdf);
            BrowseFacility browse = getGatewayAndCtx().getGateway().getFacility(BrowseFacility.class);
            ImageData oriImage = browse.getImage(getGatewayAndCtx().getCtx(group), rdf.getRawDataFileId());
            long fileSetId = oriImage.getFilesetId();
            Collection<ImageData> images = browse.getImagesForDatasets(getGatewayAndCtx().getCtx(group), Collections.singleton((long) rdf.getRawDataId()));

            Iterator<ImageData> j = images.iterator();
            ImageData image;
            ImageData overview = null;
            while (j.hasNext()) {
                image = j.next();
                if (image.getFilesetId() == fileSetId && image.getSeries() == 1) {
                    overview = image;
                }
            }
            if (overview != null) return new OmeroImage(overview.getId(), 0, getGatewayAndCtx()).getBufferedImage();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }




    @Override
    public IOrbitImage createOrbitImage(RawDataFile rdf, int level) throws Exception {
        long imageId = rdf.getRawDataFileId();
        long group = getImageGroupCached(imageId);

        boolean useCache = true;
        OrbitImageBioformatsOmero oi = new OrbitImageBioformatsOmero("omeroorbit:iid="+rdf.getRawDataFileId(),level,0, useCache, gatewayAndCtx,imageId, group);
        return oi;
    }


    @Override
    public AbstractOrbitTree createOrbitTree() {
        return new JOrbitTreeOmero(this, "Omero", Arrays.asList(new TreeNodeGroup(this, null), new TreeNodeProject(this, null), new TreeNodeDataset(this, null)));
    }

    @Override
    public boolean authenticateUser(String username, String password) {
        LoginCredentials cred = new LoginCredentials();
        cred.getServer().setHostname(host);
        cred.getServer().setPort(port);
        cred.setEncryption(useSSL);
        cred.getUser().setUsername(username);
        cred.getUser().setPassword(password);
        //Logger omeroLogger = new SimpleLogger();
        Logger omeroLogger = new Slf4jWrapper(log);
        Gateway gateway = new Gateway(omeroLogger);
        omeroUser = username;
        omeroPassword = password;

        try {
            getGatewayAndCtx().reset();
            ExperimenterData user = gateway.connect(cred);
            omeroUser = username;
            omeroPassword = password;
            getGatewayAndCtx().reset();
            return true;
        } catch (DSOutOfServiceException e) {
            log.warn("login failed with username: " + username);
            return false;
        } finally {
            try {
                if (gateway.isConnected()) gateway.disconnect();
            } catch (Exception e) {
            }
        }
    }

    /**
     * Technical user authentification for scaleout clients.
     *
     * @return login success/failure
     */
    public boolean authenticateUserScaleout() {
        omeroUser = omeroUserScaleout;
        omeroPassword = omeroPasswordScaleout;
        return authenticateUser(omeroUser, omeroPassword);
    }

    ;


    @Override
    public ConcurrentHashMap<String, Object> getHints() {
        return hints;
    }

    @Override
    public IFileListCellRenderer getFileListCellRenderer() {
        return new FileListCellRendererOmero();
    }


    @Override
    public void logUsage(String username, String method) {
        log.info("user: " + username + " :: method: " + method);
    }

    @Override
    public boolean enforceLoginDialogAtStartup() {
        return true;
    }


    private GatewayAndCtx getGatewayAndCtx() throws DSOutOfServiceException {
        return gatewayAndCtx;
    }


    public List<RawData> loadProjects(int group) {
        List<RawData> rdList = new ArrayList<>();
        if (omeroUser != null && omeroUser.length() > 0) {
            try {
                BrowseFacility browse = getGatewayAndCtx().getGateway().getFacility(BrowseFacility.class);
                Collection<ProjectData> projects = browse.getProjects(gatewayAndCtx.getCtx(group), getOwnerId());
                Iterator<ProjectData> i = projects.iterator();
                ProjectData project;
                while (i.hasNext()) {
                    project = i.next();
                    rdList.add(createRawDataProject(project,group));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            // add additional project for datasets not in a project
            RawData rd = new RawData();
            rd.setRawDataId(-1);
            rd.setBioLabJournal("unassigned");
            rd.setDescription("datasets not assigned to a project");
            rd.setReferenceDate(new Date());
            rd.setModifyDate(new Date());
            rdList.add(rd);

            Collections.sort(rdList, new Comparator<RawData>() {
                @Override
                public int compare(RawData o1, RawData o2) {
                    return o1.getBioLabJournal().compareTo(o2.getBioLabJournal());
                }
            });
        }

        return rdList;
    }


    public List<RawData> loadGroups() {
        List<RawData> rdList = new ArrayList<>();
        if (omeroUser != null && omeroUser.length() > 0) {
            try {
                BrowseFacility browse = getGatewayAndCtx().getGateway().getFacility(BrowseFacility.class);
                Set<GroupData> groups = browse.getAvailableGroups(gatewayAndCtx.getCtx(), gatewayAndCtx.getGateway().getLoggedInUser());
                for (GroupData group: groups) {
                    rdList.add(createRawDataGroup(group));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            Collections.sort(rdList, new Comparator<RawData>() {
                @Override
                public int compare(RawData o1, RawData o2) {
                    return o1.getBioLabJournal().compareTo(o2.getBioLabJournal());
                }
            });
        }

        return rdList;
    }

    public List<Long> getGroups() {
        List<RawData> rdGroups = loadGroups();
        List<Long> groupList = new ArrayList<>(rdGroups.size());
        for (RawData rd: rdGroups) {
            groupList.add((long) rd.getRawDataId());
        }
        return groupList;
    }

    public List<RawData> loadRawDataDatasets(RawData projectRD) {
        List<RawData> rdList = new ArrayList<>();
        if (omeroUser != null && omeroUser.length() > 0) {
            try {
                BrowseFacility browse = getGatewayAndCtx().getGateway().getFacility(BrowseFacility.class);
                Collection<ProjectData> projects;
                if (projectRD != null && projectRD.getRawDataId() > 0) {
                    long group = getProjectGroup((long)projectRD.getRawDataId());
                    projects = browse.getProjects(gatewayAndCtx.getCtx(group), getOwnerId(), Collections.singleton((long) projectRD.getRawDataId()));
                    Iterator<ProjectData> i = projects.iterator();
                    ProjectData project;
                    Iterator<DatasetData> j;
                    DatasetData dataset;
                    while (i.hasNext()) {
                        project = i.next();
                        Set<DatasetData> datasets = project.getDatasets();
                        j = datasets.iterator();
                        while (j.hasNext()) {
                            dataset = j.next();
                            rdList.add(createRawDataDataset(dataset,group));
                        }
                    }
                } // datasets not assigned to projects (call all projects before calling this method without(=-1) projectID!)
                else {
                    for (RawData groupRd: loadGroups()) {
                        long group = groupRd.getRawDataId();
                        HashSet<Long> loadedDatasetIDs = new HashSet<>();
                        projects = browse.getProjects(gatewayAndCtx.getCtx(group), getOwnerId()); // load all projects
                        Iterator<ProjectData> i = projects.iterator();
                        ProjectData project;
                        Iterator<DatasetData> j;
                        DatasetData dataset;
                        while (i.hasNext()) {
                            project = i.next();
                            Set<DatasetData> datasets = project.getDatasets();
                            j = datasets.iterator();
                            while (j.hasNext()) {
                                dataset = j.next();
                                loadedDatasetIDs.add(dataset.getId());
                            }
                        }
                        // now load all datasets and keep the ones not in a project -> really bad...
                        Collection<DatasetData> datasets = browse.getDatasets(gatewayAndCtx.getCtx(group), getOwnerId());
                        for (DatasetData dataset2 : datasets) {
                            if (!loadedDatasetIDs.contains(dataset2.getId())) {
                                rdList.add(createRawDataDataset(dataset2,group));
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            Collections.sort(rdList, new Comparator<RawData>() {
                @Override
                public int compare(RawData o1, RawData o2) {
                    return o1.getBioLabJournal().compareTo(o2.getBioLabJournal());
                }
            });
        }

        return rdList;
    }


    /**
     * Loads a list of raw datasets. Filter is optional and can be used to filter the results (case-insensitive).
     *
     * @param filter case-insensitive filter
     * @return list of raw datasets.
     */
    private List<RawData> loadRawDataDatasets(String filter) {
        List<RawData> rdList = new ArrayList<>();
        for (RawData groupRd: loadGroups()) {
            long group = groupRd.getRawDataId();
            try {
                BrowseFacility browse = getGatewayAndCtx().getGateway().getFacility(BrowseFacility.class);
                Collection<DatasetData> datasets = browse.getDatasets(gatewayAndCtx.getCtx(group), getOwnerId());
                Iterator<DatasetData> i = datasets.iterator();
                DatasetData dataset;
                while (i.hasNext()) {
                    dataset = i.next();
                    if (filter == null || filter.length() == 0 || dataset.getName().toLowerCase().contains(filter.toLowerCase())) {
                        rdList.add(createRawDataDataset(dataset,group));
                    }
                }
                Collections.sort(rdList, new Comparator<RawData>() {
                    @Override
                    public int compare(RawData o1, RawData o2) {
                        return o1.getBioLabJournal().compareTo(o2.getBioLabJournal());
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return rdList;
    }

    /**
     * @param rawDataId
     * @param limit     set < 0 for no limit
     * @return list of raw data files contained by the dataset
     */
    private List<RawDataFile> loadRdfList(final int rawDataId, final int limit) {
        return loadRdfList(rawDataId, limit, listAllSeries);
    }

    /**
     * @param rawDataId
     * @param limit     set < 0 for no limit
     * @return list of raw data files contained by the dataset
     */
    private List<RawDataFile> loadRdfList(final int rawDataId, final int limit, boolean listAllSeries) {
        long group = getDatasetGroup((long)rawDataId);
        List<RawDataFile> rdfList = new ArrayList<>();
        try {
            BrowseFacility browse = getGatewayAndCtx().getGateway().getFacility(BrowseFacility.class);
            Collection<ImageData> images = browse.getImagesForDatasets(getGatewayAndCtx().getCtx(group), Collections.singleton((long) rawDataId));

            Iterator<ImageData> j = images.iterator();
            ImageData image;
            int cnt = 0;
            while (j.hasNext()) {
                image = j.next();
                //System.out.println(image.getName()+" / index: "+ image.getSeries());
                if (image.getSeries() == 0 || listAllSeries) {
                    if (limit < 0 || cnt++ > limit) break;
                    rdfList.add(createRawDataFile(image,group));
                }
            }
            Collections.sort(rdfList, new Comparator<RawDataFile>() {
                @Override
                public int compare(RawDataFile o1, RawDataFile o2) {
                    return o1.getFileName().compareTo(o1.getFileName());
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }

        return rdfList;
    }


    @Override
    public void close() throws IOException {
        if (gatewayAndCtx != null) {
            try {
                if (gatewayAndCtx.getGateway().isConnected())
                    gatewayAndCtx.getGateway().disconnect();
                log.info("Omero gateway closed");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    public class GatewayAndCtx {
        transient Gateway gateway = null;
        transient SecurityContext ctx = null;
        LoginCredentials cred;

        public GatewayAndCtx() {
        }

        public synchronized SecurityContext getCtx() throws DSOutOfServiceException {
            getGateway();
            return ctx;
        }

        public synchronized SecurityContext getCtx(long group) throws DSOutOfServiceException {
            getGateway();
            return new SecurityContext(group);
        }

        public synchronized Gateway getGateway() throws DSOutOfServiceException {
            if (gateway == null) {

                // TODO: authenticate in grid mode???

                cred = new LoginCredentials();
                cred.getServer().setHostname(host);
                cred.getServer().setPort(port);
                cred.setApplicationName("Orbit");
                cred.getUser().setUsername(omeroUser);
                cred.getUser().setPassword(omeroPassword);
                //cred.setCompression(0.8f);
                SimpleLogger simpleLogger = new SimpleLogger();
                gateway = new Gateway(simpleLogger);
                ExperimenterData user = gateway.connect(cred);

                ctx = new SecurityContext(user.getGroupId());    // default group
            } else {
                if (!gateway.isConnected()/*||!gateway.isAlive(ctx)*/) {
                    gateway.connect(cred);
                    ExperimenterData user = gateway.connect(cred); // needed?
                    ctx = new SecurityContext(user.getGroupId());
                }
            }
            return gateway;
        }

        /**
         * e.g. if switch user
         */
        public void reset() {
            gateway = null;
        }

    }


    // bean creation

    protected RawDataFile createRawDataFile(ImageData image, long group) {
        int rawDataId = 0; //loadRawDataId((int)image.getId());

        try {
            ParametersI param = new ParametersI();
            param.addIds(Collections.singleton(image.getId()));
            List<IObject> list = gatewayAndCtx.getGateway().getQueryService(gatewayAndCtx.getCtx(group)).findAllByQuery("select l from DatasetImageLink as l left outer join fetch l.parent where l.child.id =:ids ", param);

            if (list != null && list.size() > 0) {
                DatasetI dataset = (DatasetI) ((DatasetImageLinkI) list.get(0)).getParent();
                rawDataId = (int) dataset.getId().getValue();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return createRawDataFile(image, rawDataId, group);
    }

    protected RawDataFile createRawDataFile(ImageData image, int rawDataId, long group) {
        RawDataFile rdf = new RawDataFile();
        rdf.setRawDataId(rawDataId);
        rdf.setRawDataFileId((int) image.getId());
        rdf.setReferenceDate(image.getCreated() != null ? image.getCreated() : new Date());
        rdf.setModifyDate(image.getUpdated());
        rdf.setFileName(image.getName());
        rdf.setUserId(experimenterToString(image.getOwner()));
        String ending = RawUtilsCommon.getExtension(image.getName());
        String format = image.getFormat().toLowerCase();
        System.out.println("ending: " + ending + "  format: " + image.getFormat());
        switch (ending) {
            case "jpg":
                rdf.setFileType(RawUtilsCommon.DATA_TYPE_IMAGE_JPG);
                break;
            case "jpeg":
                rdf.setFileType(RawUtilsCommon.DATA_TYPE_IMAGE_JPG);
                break;
            case "jp2":
                rdf.setFileType(RawUtilsCommon.DATA_TYPE_IMAGE_JPEG2000);
                break;
            case "tif":
                rdf.setFileType(RawUtilsCommon.DATA_TYPE_IMAGE_TIFF);
                break;
            case "tiff":
                rdf.setFileType(RawUtilsCommon.DATA_TYPE_IMAGE_TIFF);
                break;
            case "png":
                rdf.setFileType(RawUtilsCommon.DATA_TYPE_IMAGE_PNG);
                break;
        }
        System.out.println("type: " + rdf.getFileType());
        rdfGroupMap.put(image.getId(),group);
        return rdf;
    }

    protected RawDataProject createRawDataProject(ProjectData project, long group) {
        RawDataProject rd = new RawDataProject();
        rd.setRawDataId((int) project.getId());
        rd.setReferenceDate(project.getCreated() != null ? project.getCreated() : new Date());
        rd.setModifyDate(project.getUpdated());
        rd.setBioLabJournal(project.getName());
        rd.setDescription(project.getDescription());
        rd.setUserId(experimenterToString(project.getOwner()));
        projectGroupMap.put(project.getId(),group);
        return rd;
    }

    protected RawDataGroup createRawDataGroup(GroupData group) {
        RawDataGroup rd = new RawDataGroup();
        rd.setRawDataId((int) group.getId());
        rd.setReferenceDate(group.getCreated() != null ? group.getCreated() : new Date());
        rd.setModifyDate(group.getUpdated());
        rd.setBioLabJournal(group.getName());
        rd.setDescription(group.getDescription());
        rd.setUserId(experimenterToString(group.getOwner()));
        return rd;
    }


    protected RawDataDataset createRawDataDataset(DatasetData dataset, long group) {
        RawDataDataset rd = new RawDataDataset();
        rd.setRawDataId((int) dataset.getId());
        rd.setReferenceDate(dataset.getCreated() != null ? dataset.getCreated() : new Date());
        rd.setModifyDate(dataset.getUpdated());
        rd.setBioLabJournal(dataset.getName());
        rd.setDescription(dataset.getDescription());
        rd.setUserId(experimenterToString(dataset.getOwner()));
        datasetGroupMap.put(dataset.getId(),group);
        return rd;
    }

    public String experimenterToString(ExperimenterData experimenterData) {
        if (experimenterData == null) return "";
        try {
            IAdminPrx admin = getGatewayAndCtx().getGateway().getAdminService(gatewayAndCtx.getCtx());
            Experimenter experimenter = admin.getExperimenter(experimenterData.getId());
            if (experimenter != null) {
                String username = experimenter.getOmeName().getValue();      // is omeName == username ???
                if (log.isTraceEnabled())
                    log.trace("username: " + username);
                return username;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }


    /**
     * just for testing
     *
     * @param bi
     */
    private static void DisplayImage(final BufferedImage bi) {
        JFrame frame = new JFrame("Image Data");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setSize(bi.getWidth(), bi.getHeight());
        JPanel pane = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.drawImage(bi, 0, 0, null);
            }
        };
        frame.add(pane);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }


    // Meta Data

    @Override
    public synchronized List<RawMeta> LoadRawMetasByRawDataFile(int rdfId) throws Exception {

        if (metaHashRDF.containsKey(rdfId)) {
            Map<String, RawMeta> metaMap = metaHashRDF.get(rdfId);
            List<RawMeta> rmList = new ArrayList<>(metaMap.size());
            for (String key : metaMap.keySet()) {
                RawMeta rm = metaMap.get(key).clone(); // because the name will be manipulated later
                rmList.add(rm);
            }
            return rmList;
        } else {

            RawMetaFactoryFile rmff = new RawMetaFactoryFile(rdfId, new Date(), gatewayAndCtx.getGateway().getLoggedInUser().getUserName());

            RawDataFile rdf = LoadRawDataFile(rdfId);
            long group = getRdfGroup(rdf);
            List<RawMeta> rmList = new ArrayList<>();
            rmList.add(rmff.createMetaStr("User", rdf.getUserId()));
            rmList.add(rmff.createMetaStr("Filename", rdf.getFileName()));
            rmList.add(rmff.createMetaInt("Filesize", (int) rdf.getFileSize()));
            rmList.add(rmff.createMetaInt("Omero ID", rdf.getRawDataFileId()));
            if (rdf.getReferenceDate() != null)
                rmList.add(rmff.createMetaDate("Create Date", rdf.getReferenceDate()));
            if (rdf.getModifyDate() != null)
                rmList.add(rmff.createMetaDate("Update Date", rdf.getModifyDate()));


            MetadataFacility metaData = getGatewayAndCtx().getGateway().getFacility(MetadataFacility.class);
            List<ChannelData> cd = metaData.getChannelData(gatewayAndCtx.getCtx(group), rdfId);
            for (ChannelData channelData : cd) {
                rmList.add(rmff.createMetaStr("Channel." + RawUtilsCommon.STR_META_CHANNEL + " " + channelData.getId(), channelData.getName() + " (" + channelData.getChannelLabeling() + ")"));
            }


            // annotations
            IMetadataPrx proxy = gatewayAndCtx.getGateway().getMetadataService(gatewayAndCtx.getCtx(group));
            List<String> nsToInclude = new ArrayList<String>();
            //nsToInclude.add(NAME_SPACE_TO_SET);
            List<String> nsToExclude = new ArrayList<String>();
            // FileAnnotation.class.getName()
            // TextAnnotation
            // TagAnnotation
            List<Annotation> annotations = proxy.loadSpecifiedAnnotations(TagAnnotation.class.getName(), nsToInclude, nsToExclude, getParameterRead());
            StringBuilder tagBuilder = new StringBuilder();
            for (Annotation annotation : annotations) {
                tagBuilder.append(((TagAnnotationI) annotation).getTextValue().getValue() + " ");
            }
            String tags = tagBuilder.toString();
            if (tags.length() > 0)
                rmList.add(rmff.createMetaStr("Tags", tagBuilder.toString()));


            List<Annotation> commentAnnotation = proxy.loadSpecifiedAnnotations(CommentAnnotation.class.getName(), nsToInclude, nsToExclude, getParameterRead());
            for (Annotation annotation : commentAnnotation) {
                rmList.add(rmff.createMetaStr("Comments." + annotation.getDetails().getOwner().getFirstName().getValue() + " " + annotation.getDetails().getOwner().getLastName().getValue(), ((CommentAnnotationI) annotation).getTextValue().getValue()));
            }


            //DataManagerFacility dm = gatewayAndCtx.getGateway().getFacility(DataManagerFacility.class);
            // metaData
            nsToInclude = new ArrayList<>();
            nsToInclude.add(ORBIT_METADATA_NAMESPACE);
            nsToExclude = new ArrayList<>();
            List<Annotation> metaDataannotations = proxy.loadSpecifiedAnnotationsLinkedTo(MapAnnotationI.class.getName(), nsToInclude, nsToExclude, Image.class.getName(), Collections.singletonList((long) rdfId), getParameterRead()).get((long) rdfId);
            if (metaDataannotations != null) {
                for (Annotation metaDataannotation : metaDataannotations) {
                    if (metaDataannotation instanceof MapAnnotationI) {
                        MapAnnotationI anno = (MapAnnotationI) metaDataannotation;
                        RawMeta rm = rmff.createMetaStr(anno.getName().getValue(), anno.getMapValueAsMap().get(anno.getName().getValue()));
                        rm.setRawMetaId((int) anno.getId().getValue());
                        log.debug("meta loaded: " + rm + " id: " + rm.getRawMetaId());
                        rmList.add(rm);
                        //dm.deleteObject(gatewayAndCtx.getCtx(),anno);
                    }
                }
            }


            // image dims and resolution
            BrowseFacility browse = gatewayAndCtx.getGateway().getFacility(BrowseFacility.class);
            PixelsData imageData = browse.getImage(gatewayAndCtx.getCtx(group), rdfId).getDefaultPixels();
            rmList.add(rmff.createMetaInt(RawUtilsCommon.STR_META_IMAGE_IMAGEWIDTH, imageData.getSizeX()));
            rmList.add(rmff.createMetaInt(RawUtilsCommon.STR_META_IMAGE_IMAGEHEIGHT, imageData.getSizeY()));
            Length mupp = imageData.getPixelSizeX(UnitsLength.MICROMETER);
            if (mupp != null)
                rmList.add(rmff.createMetaDouble(RawUtilsCommon.STR_META_IMAGE_SCALE, mupp.getValue()));    // TODO: pixel size for x and y (and z)


            // cache entries
            Map<String, RawMeta> metaMap = new HashMap<>();
            for (RawMeta rm : rmList) {
                metaMap.put(rm.getName(), rm);
            }
            metaHashRDF.put(rdfId, metaMap);
            return rmList;
        }
    }


    @Override
    public synchronized List<RawMeta> LoadRawMetasByRawDataFileAndName(int rdfId, String name) throws Exception {
        if (!metaHashRDF.containsKey(rdfId)) {
            LoadRawMetasByRawDataFile(rdfId);
        }

        List<RawMeta> rmList = new ArrayList<>(1);
        RawMeta rm = metaHashRDF.get(rdfId).get(name);
        if (rm != null)
            rmList.add(rm.clone());
        return rmList;
    }


    @Override
    public List<RawMeta> LoadRawMetasByRawData(int rdId) throws Exception {
        if (metaHashRawData.containsKey(rdId)) {
            Map<String, RawMeta> metaMap = metaHashRawData.get(rdId);
            List<RawMeta> rmList = new ArrayList<>(metaMap.size());
            for (String key : metaMap.keySet()) {
                RawMeta rm = metaMap.get(key).clone();   // because the name will be manipulated later
                rmList.add(rm);
            }
            return rmList;
        } else {
            RawMetaFactoryData rmfd = new RawMetaFactoryData(rdId, new Date(), gatewayAndCtx.getGateway().getLoggedInUser().getUserName());
            List<RawMeta> rmList = new ArrayList<>();
            RawData rd = LoadRawData(rdId);
            if (rd.getBioLabJournal() != null)
                rmList.add(rmfd.createMetaStr("Dataset", rd.getBioLabJournal()));
            rmList.add(rmfd.createMetaStr("User", rd.getUserId()));
            if (rd.getReferenceDate() != null)
                rmList.add(rmfd.createMetaDate("Create Date", rd.getReferenceDate()));
            if (rd.getModifyDate() != null)
                rmList.add(rmfd.createMetaDate("Update Date", rd.getModifyDate()));
            if (rd.getDescription() != null)
                rmList.add(rmfd.createMetaStr("Description", rd.getDescription()));
            if (rd.getComment() != null)
                rmList.add(rmfd.createMetaStr("Comment", rd.getComment()));
            rmList.add(rmfd.createMetaInt("Dataset ID", rd.getRawDataId()));

            // cache entries
            Map<String, RawMeta> metaMap = new HashMap<>();
            for (RawMeta rm : rmList) {
                metaMap.put(rm.getName(), rm);
            }
            metaHashRawData.put(rdId, metaMap);
            return rmList;
        }

    }

    @Override
    public List<RawMeta> LoadRawMetasByRawDataAndName(int rawDataId, String name) throws Exception {
        if (!metaHashRawData.containsKey(rawDataId)) {
            LoadRawMetasByRawData(rawDataId);
        }

        List<RawMeta> rmList = new ArrayList<>(1);
        RawMeta rm = metaHashRawData.get(rawDataId).get(name);
        if (rm != null)
            rmList.add(rm.clone());
        return rmList;
    }


    // annotations

    @Override
    public int InsertRawAnnotation(RawAnnotation rawAnnotation) throws Exception {
        //ROIData roi = new ROIData();
        //PointData pd = new PointData();
        long group = getImageGroup(rawAnnotation.getRawDataFileId());

        // fist version: save as attached file
        DataManagerFacility dm = gatewayAndCtx.getGateway().getFacility(DataManagerFacility.class);

        String name = rawAnnotation.getDescription();
        String path = ORBIT_PATH;
        String fileMimeType = ORBIT_ANNOTATION_MIMETYPE;
        //String namespace = fileMimeType+"/"+rawAnnotation.getRawAnnotationType();  // would be nice, but we can't query with a wildcard later, so we use a standard namespace
        String namespace = ANNOTATION_NAMESPACE;
        if (rawAnnotation.getRawDataFileId() < 0) namespace = ANNOTATION_NOFILE_NAMESPACE;

        //create the original file object.
        OriginalFile originalFile = new OriginalFileI();
        originalFile.setName(omero.rtypes.rstring(name));
        originalFile.setPath(omero.rtypes.rstring(path));
        originalFile.setSize(omero.rtypes.rlong(rawAnnotation.getData().length));
        final ChecksumAlgorithm checksumAlgorithm = new ChecksumAlgorithmI();
        checksumAlgorithm.setValue(omero.rtypes.rstring(ChecksumAlgorithmSHA1160.value));
        originalFile.setHasher(checksumAlgorithm);
        originalFile.setMimetype(omero.rtypes.rstring(fileMimeType));
        //Now we save the originalFile object
        originalFile = (OriginalFile) dm.saveAndReturnObject(gatewayAndCtx.getCtx(group), originalFile);

        // upload the byte array
        originalFile = storeOriginalFile(rawAnnotation, originalFile);

        //now we have an original File in DB and raw data uploaded.
        //We now need to link the Original file to the image using
        //the File annotation object.
        FileAnnotation fa = new FileAnnotationI();
        fa.setFile(originalFile);
        fa.setDescription(omero.rtypes.rstring(rawAnnotation.getDescription()));
        fa.setNs(omero.rtypes.rstring(namespace));

        //save the file annotation.
        fa = (FileAnnotation) dm.saveAndReturnObject(gatewayAndCtx.getCtx(group), fa);

        //now link the image and the annotation
        if (rawAnnotation.getRawDataFileId() >= 0) {    // otherwise it's a non file specific annotation
            BrowseFacility browse = getGatewayAndCtx().getGateway().getFacility(BrowseFacility.class);
            ImageData image = browse.getImage(gatewayAndCtx.getCtx(group), rawAnnotation.getRawDataFileId());
            ImageAnnotationLink link = new ImageAnnotationLinkI();
            link.setChild(fa);
            link.setParent(image.asImage());
            //save the link back to the server.
            link = (ImageAnnotationLink) dm.saveAndReturnObject(gatewayAndCtx.getCtx(group), link);
            // to attach to a Dataset use DatasetAnnotationLink;
        }

        // set the id the the rawAnnotation object and return the id
        rawAnnotation.setRawAnnotationId((int) fa.getId().getValue());
        log.debug("rawAnnotation inserted: " + rawAnnotation);
        return rawAnnotation.getRawAnnotationId();
    }


    @Override
    public boolean UpdateRawAnnotation(RawAnnotation rawAnnotation) throws Exception {
        long group = getImageGroup(rawAnnotation.getRawDataFileId());

        BrowseFacility browse = getGatewayAndCtx().getGateway().getFacility(BrowseFacility.class);
        FileAnnotation annotation = (FileAnnotation) loadAnnotation(rawAnnotation.getRawAnnotationId());
        DataManagerFacility dm = gatewayAndCtx.getGateway().getFacility(DataManagerFacility.class);

        String name = rawAnnotation.getDescription();
        String path = ORBIT_PATH;
        String fileMimeType = ORBIT_ANNOTATION_MIMETYPE;
        String namespace = ANNOTATION_NAMESPACE;
        if (rawAnnotation.getRawDataFileId() < 0) namespace = ANNOTATION_NOFILE_NAMESPACE;

        //save the original file object.
        OriginalFile originalFile = annotation.getFile();
        originalFile.setName(omero.rtypes.rstring(name));
        originalFile.setPath(omero.rtypes.rstring(path));
        originalFile.setSize(omero.rtypes.rlong(rawAnnotation.getData().length));
        final ChecksumAlgorithm checksumAlgorithm = new ChecksumAlgorithmI();
        checksumAlgorithm.setValue(omero.rtypes.rstring(ChecksumAlgorithmSHA1160.value));
        originalFile.setHasher(checksumAlgorithm);
        originalFile.setMimetype(omero.rtypes.rstring(fileMimeType));
        //Now we update the originalFile object
        originalFile = (OriginalFile) dm.updateObject(gatewayAndCtx.getCtx(group), originalFile, getParameterWrite());
        if (log.isTraceEnabled())
            log.trace("original file updated: " + originalFile);

        // upload the byte array
        originalFile = storeOriginalFile(rawAnnotation, originalFile);

        //now we have an original File in DB and raw data uploaded.
        //We now need to link the Original file to the image using
        //the File annotation object.
        FileAnnotation fa = annotation; // update existing annotation
        fa.setFile(originalFile);
        fa.setDescription(omero.rtypes.rstring(rawAnnotation.getDescription()));
        fa.setNs(omero.rtypes.rstring(namespace)); // The name space you have set to identify the file annotation.

        //save the file annotation.
        fa = (FileAnnotation) dm.updateObject(gatewayAndCtx.getCtx(group), fa, getParameterWrite());  // id should be the same afterwards
        //rawAnnotation.setRawAnnotationId((int) fa.getId().getValue());
        log.debug("rawAnnotation updated: " + rawAnnotation);
        return (int) fa.getId().getValue() == rawAnnotation.getRawAnnotationId();
    }

    private OriginalFile storeOriginalFile(RawAnnotation rawAnnotation, OriginalFile originalFile) throws DSOutOfServiceException, ServerError, IOException {
        long group = getImageGroup(rawAnnotation.getRawDataFileId());

        RawFileStorePrx rawFileStore = gatewayAndCtx.getGateway().getRawFileService(gatewayAndCtx.getCtx(group));
        rawFileStore.setFileId(originalFile.getId().getValue());

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(rawAnnotation);
        oos.flush();
        oos.close();
        bos.flush();
        bos.close();

        InputStream stream = new ByteArrayInputStream(bos.toByteArray());
        long pos = 0;
        int rlen;
        byte[] buf = new byte[INC];
        ByteBuffer bbuf;
        while ((rlen = stream.read(buf)) > 0) {
            rawFileStore.write(buf, pos, rlen);
            pos += rlen;
            bbuf = ByteBuffer.wrap(buf);
            bbuf.limit(rlen);
        }
        stream.close();
        originalFile = rawFileStore.save();
        rawFileStore.close();
        return originalFile;
    }


    private Annotation loadAnnotation(int rawAnnotationId) throws Exception {
        long group = getAnnotationGroup(rawAnnotationId);
        Annotation annotation = null;
        IMetadataPrx proxy = gatewayAndCtx.getGateway().getMetadataService(gatewayAndCtx.getCtx(group));
        List<Annotation> annotations = proxy.loadAnnotation(Collections.singletonList((long) rawAnnotationId));
        if (annotations == null || annotations.size() == 0) {
            throw new Exception("annotation with id " + rawAnnotationId + " not found");
        }  else {
            if (annotations.size() > 1) {
                log.warn("more than one annotations found for id " + rawAnnotationId + " but only the first will be used");
            }
            annotation = annotations.get(0);
        }
        return annotation;
    }

    private List<RawAnnotation> loadAnnotations(Collection<Annotation> annotations) throws Exception {
        FileAnnotationData fa;
        List<RawAnnotation> rawAnnotations = new ArrayList<>(annotations.size());
        RawFileStorePrx store = null;
        long lastGroup = -2;
        for (Annotation annotation : annotations) {
            long group = getAnnotationGroup(annotation.getId().getValue());
            if (group!=lastGroup) {
                if (store!=null) {
                    try {
                        store.close();
                    } catch (Exception e) {}
                }
                store = gatewayAndCtx.getGateway().getRawFileService(gatewayAndCtx.getCtx(group));
                lastGroup = group;
            }
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            OriginalFile of;
            if (annotation instanceof FileAnnotation) {
                fa = new FileAnnotationData((FileAnnotation) annotation);
                //The id of te original file
                //of = getOriginalFile(fa.getFileID());
                store.setFileId(fa.getFileID());
                int offset = 0;
                long size = store.size();
                try {
                    for (offset = 0; (offset + INC) < size; ) {
                        stream.write(store.read(offset, INC));
                        offset += INC;
                    }
                } finally {
                    stream.write(store.read(offset, (int) (size - offset)));
                    stream.close();
                }
            }

            byte[] data = stream.toByteArray();
            ByteArrayInputStream bis = new ByteArrayInputStream(data);
            ObjectInputStream ois = new ObjectInputStream(bis);
            try {
                Object obj = ois.readObject();
                RawAnnotation ra = (RawAnnotation) obj;
                ra.setRawAnnotationId((int) annotation.getId().getValue());
                rawAnnotations.add(ra);
            } catch (ClassNotFoundException e) {
                log.warn("class for deserialization not found: " + e.getMessage());
            } catch (InvalidClassException ice) {
                log.warn("invalid class exception");
            }
            finally {
                ois.close();
                bis.close();
            }

        }
        if (store!=null) {
            try {
                store.close();
            } catch (Exception e) {}
        }

        return rawAnnotations;
    }


    @Override
    public RawAnnotation LoadRawAnnotation(int rawAnnotationId) throws Exception {
        Annotation annotation = loadAnnotation(rawAnnotationId);
        List<RawAnnotation> rawAnnotations = loadAnnotations(Collections.singleton(annotation));
        if (rawAnnotations == null || rawAnnotations.size() == 0)
            throw new Exception("annotation with id " + rawAnnotationId + " not found");
        return rawAnnotations.get(0);
    }


    @Override
    public List<RawAnnotation> LoadRawAnnotationsByRawDataFile(int rdfID) throws Exception {
        long group = getImageGroup(rdfID);
        IMetadataPrx proxy = gatewayAndCtx.getGateway().getMetadataService(gatewayAndCtx.getCtx(group));
        List<String> nsToInclude = new ArrayList<String>();
        nsToInclude.add(ANNOTATION_NAMESPACE);
        List<String> nsToExclude = new ArrayList<String>();
        List<Annotation> annotations = proxy.loadSpecifiedAnnotationsLinkedTo(FileAnnotation.class.getName(), nsToInclude, nsToExclude, Image.class.getName(), Collections.singletonList((long) rdfID), getParameterRead()).get((long) rdfID);
        if (log.isTraceEnabled() && annotations != null)
            log.trace("#annotations: " + annotations.size());

        if (annotations == null || annotations.size() == 0) {
            return new ArrayList<>();
        }

        return loadAnnotations(annotations);
    }


    @Override
    public List<RawAnnotation> LoadRawAnnotationsByRawDataFile(int rdfID, int rawAnnotationType) throws Exception {
        List<RawAnnotation> rawAnnotations = LoadRawAnnotationsByRawDataFile(rdfID);
        List<RawAnnotation> filtered = new ArrayList<>(rawAnnotations.size());
        // TODO: better query correct types
        for (RawAnnotation rawAnnotation : rawAnnotations) {
            if (rawAnnotation.getRawAnnotationType() == rawAnnotationType) {
                filtered.add(rawAnnotation);
            }
        }
        return filtered;
    }


    @Override
    public List<RawAnnotation> LoadRawAnnotationsByType(int rawAnnotationType) throws Exception {
        String namespace = ANNOTATION_NAMESPACE;
        if (rawAnnotationType == RawAnnotation.ANNOTATION_TYPE_MODEL) namespace = ANNOTATION_NOFILE_NAMESPACE;
        List<Annotation> annotations = new ArrayList<>();
        for (long group: getGroups()) {
            IMetadataPrx proxy = gatewayAndCtx.getGateway().getMetadataService(gatewayAndCtx.getCtx(group));
            List<String> nsToInclude = new ArrayList<String>();
            nsToInclude.add(namespace);
            List<String> nsToExclude = new ArrayList<String>();
            annotations.addAll(proxy.loadSpecifiedAnnotations(FileAnnotation.class.getName(), nsToInclude, nsToExclude, getParameterRead()));   // use parameter param to restrict annotations to user
            if (log.isTraceEnabled() && annotations != null)
                log.trace("group "+group+ " #annotations: " + annotations.size());
        }

        if (annotations == null || annotations.size() == 0) {
            return new ArrayList<>();
        }

        // TODO: better query correct types
        List<RawAnnotation> rawAnnotations = loadAnnotations(annotations);
        List<RawAnnotation> filtered = new ArrayList<>(rawAnnotations.size());
        for (RawAnnotation rawAnnotation : rawAnnotations) {
            if (rawAnnotation.getRawAnnotationType() == rawAnnotationType) {
                filtered.add(rawAnnotation);
            }
        }
        return filtered;
    }


    @Override
    public boolean DeleteRawAnnotation(int rawAnnotationId) throws Exception {
        long group = getAnnotationGroup(rawAnnotationId);
        Annotation annotation = loadAnnotation(rawAnnotationId);
        DataManagerFacility dm = gatewayAndCtx.getGateway().getFacility(DataManagerFacility.class);
        Delete2Response response = (Delete2Response) dm.deleteObject(gatewayAndCtx.getCtx(group), annotation);
        return (response.deletedObjects.get("ome.model.annotations.FileAnnotation").size() > 0);  // deleted one fileannotaiton (no id check here)
    }


    // Raw Meta

    @Override
    public int InsertRawMeta(RawMeta rm) throws Exception {
        long group = getImageGroup(rm.getRawDataFileId());

        DataManagerFacility dm = gatewayAndCtx.getGateway().getFacility(DataManagerFacility.class);
        MapAnnotationI anno = new MapAnnotationI();
        anno.setNs(omero.rtypes.rstring(ORBIT_METADATA_NAMESPACE));
        anno.setName(omero.rtypes.rstring(rm.getName()));
        List<NamedValue> kvList = new ArrayList<>(1);
        kvList.add(new NamedValue(rm.getName(), rm.getValue()));
        anno.setMapValue(kvList);
        anno = (MapAnnotationI) dm.saveAndReturnObject(gatewayAndCtx.getCtx(group), anno);
        rm.setRawMetaId((int) anno.getId().getValue());
        log.debug("inserted meta data: " + rm);

        // link to rawDataFile
        BrowseFacility browse = getGatewayAndCtx().getGateway().getFacility(BrowseFacility.class);
        ImageData image = browse.getImage(gatewayAndCtx.getCtx(group), rm.getRawDataFileId());
        ImageAnnotationLink link = new ImageAnnotationLinkI();
        link.setChild(anno);
        link.setParent(image.asImage());
        link = (ImageAnnotationLink) dm.saveAndReturnObject(gatewayAndCtx.getCtx(group), link);
        clearMetaRDFHash();
        return rm.getRawMetaId();
    }

    @Override
    public boolean UpdateRawMeta(RawMeta rm) throws Exception {
        if (rm.getRawMetaId() <= 0)
            throw new IllegalArgumentException("RawMeta is not persistent, cannot update. RawMeta: " + rm);
        long group = getImageGroup(rm.getRawDataFileId());
        Annotation annotation = loadAnnotation(rm.getRawMetaId());
        if (annotation instanceof MapAnnotationI) {
            DataManagerFacility dm = gatewayAndCtx.getGateway().getFacility(DataManagerFacility.class);
            MapAnnotationI anno = (MapAnnotationI) annotation;
            log.debug("update rawMeta annotation: " + anno);
            anno.setName(omero.rtypes.rstring(rm.getName()));
            List<NamedValue> kvList = new ArrayList<>(1);
            kvList.add(new NamedValue(rm.getName(), rm.getValue()));
            anno.setMapValue(kvList);
            anno = (MapAnnotationI) dm.updateObject(gatewayAndCtx.getCtx(group), anno, getParameterWrite());
            clearMetaRDFHash();
            return ((int) anno.getId().getValue()) == rm.getRawMetaId();
        } else {
            throw new IllegalArgumentException("annotation is not a MapAnnotationI, but is " + annotation.getClass().getName());
        }
    }


    @Override
    public boolean DeleteRawMeta(int rawMetaId) throws Exception {
        if (rawMetaId <= 0)
            throw new IllegalArgumentException("RawMeta is not persistent, cannot delete. RawMetaId: " + rawMetaId);
        long group = getAnnotationGroup(rawMetaId);
        Annotation annotation = loadAnnotation(rawMetaId);
        if (annotation instanceof MapAnnotationI) {

            DataManagerFacility dm = gatewayAndCtx.getGateway().getFacility(DataManagerFacility.class);
            MapAnnotationI anno = (MapAnnotationI) annotation;
            dm.delete(gatewayAndCtx.getCtx(group), anno);
            log.debug("delete rawMeta annotation: " + anno);
        } else {
            throw new IllegalArgumentException("annotation is not a MapAnnotationI, but is " + annotation.getClass().getName());
        }
        return true;
    }


    @Override
    public OrbitUser getOrbitUser(String username) {
        OrbitUser user = new OrbitUser(username, "", "");
        try {
            ExperimenterData experimenter = getGatewayAndCtx().getGateway().getUserDetails(gatewayAndCtx.getCtx(), username);
            user.setFirstName(experimenter.getFirstName());
            user.setLastName(experimenter.getLastName());
        } catch (DSOutOfServiceException e) {
            e.printStackTrace();
            log.error("cannot load user details", e);
        }

        return user;
    }


    public boolean isOnlyOwnerObjects() {
        return onlyOwnerObjects;
    }

    public void setOnlyOwnerObjects(boolean onlyOwnerObjects) {
        this.onlyOwnerObjects = onlyOwnerObjects;
    }

    public boolean isListAllSeries() {
        return listAllSeries;
    }

    public void setListAllSeries(boolean listAllSeries) {
        this.listAllSeries = listAllSeries;
    }

    public long getProjectGroup(long projectId) {
        long group = -1;
        if (projectGroupMap.containsKey(projectId)) {
            group = projectGroupMap.get(projectId);
        } else {
            BrowseFacility browse = null;
            try {
                browse = gatewayAndCtx.getGateway().getFacility(BrowseFacility.class);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (browse!=null) {
                for (long g : getGroups()) {
                    ProjectData project = null;
                    try {
                        Collection<ProjectData> projects = browse.getProjects(gatewayAndCtx.getCtx(g), Collections.singletonList(projectId));
                        if (projects != null && projects.size() > 0) {
                            project = projects.iterator().next();
                        }
                    } catch (Exception e) {
                        // do nothing here, just testing
                    }
                    if (project != null) {  // correct group
                        group = g;
                        return group;
                    }
                }
            }
        }
        return group;
    }

    public long getDatasetGroup(long datasetId) {
        long group = -1;
        if (datasetGroupMap.containsKey(datasetId)) {
            group = datasetGroupMap.get(datasetId);
        } else {
            BrowseFacility browse = null;
            try {
                browse = gatewayAndCtx.getGateway().getFacility(BrowseFacility.class);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (browse!=null) {
                for (long g : getGroups()) {
                    DatasetData dataset = null;
                    try {
                        Collection<DatasetData> datasets = browse.getDatasets(gatewayAndCtx.getCtx(g), Collections.singletonList(datasetId));
                        if (datasets != null && datasets.size() > 0) {
                            dataset = datasets.iterator().next();
                        }
                    } catch (Exception e) {
                        // do nothing here, just testing
                    }
                    if (dataset != null) {  // correct group
                        group = g;
                        datasetGroupMap.put(datasetId,group);
                        return group;
                    }
                }
            }
        }
        return group;
    }

    public long getRdfGroup(RawDataFile rdf) {
        long group = -1;
        if (rdfGroupMap.containsKey((long)rdf.getRawDataFileId())) {
            group = rdfGroupMap.get((long)rdf.getRawDataFileId());
        }
        else if (datasetGroupMap.containsKey((long)rdf.getRawDataId())) {
            group = getDatasetGroup((long) rdf.getRawDataId());
        } else {
            group = getImageGroup((long)rdf.getRawDataFileId());
        }
        return group;
    }

    public static long getImageGroupCached(long imageId) {
        long group = -1;
        if (rdfGroupMap.containsKey(imageId)) {
            group = rdfGroupMap.get(imageId);
        }
        return group;
    }

    public long getImageGroup(long imageId) {
        long group = -1;
        if (rdfGroupMap.containsKey(imageId)) {
            group = rdfGroupMap.get(imageId);
        } else {
            BrowseFacility browse = null;
            try {
                browse = gatewayAndCtx.getGateway().getFacility(BrowseFacility.class);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (browse!=null) {
                for (long g : getGroups()) {
                    ImageData image = null;
                    try {
                        browse = gatewayAndCtx.getGateway().getFacility(BrowseFacility.class);
                        image = browse.getImage(gatewayAndCtx.getCtx(g), imageId);
                    } catch (Exception e) {
                        // do nothing here, just testing
                    }
                    if (image != null) {  // correct group
                        group = g;
                        rdfGroupMap.put(imageId,group);
                        return group;
                    }
                }
            }
        }
        return group;
    }


    public long getAnnotationGroup(long annotationId) {
        long group = -1;
        if (metaGroupMap.containsKey(annotationId)) {
            group = metaGroupMap.get(annotationId);
        } else {
                for (long g : getGroups()) {
                    Annotation annotation = null;
                    try {
                        IMetadataPrx proxy  = gatewayAndCtx.getGateway().getMetadataService(gatewayAndCtx.getCtx(g));
                        List<Annotation> annotations = proxy.loadAnnotation(Collections.singletonList(annotationId));
                        if (annotations!=null && annotations.size()>0) {
                            annotation = annotations.get(0);
                        }
                    } catch (Exception e) {
                        // do nothing here, just testing
                    }
                    if (annotation != null) {  // correct group
                        group = g;
                        metaGroupMap.put(annotationId,group);
                        return group;
                    }
                }
            }
        return group;
    }

    /**
     * Only for debugging
     * @param user
     * @param pass
     */
    protected void showOrbitTree(String user, String pass) {
        boolean auth = authenticateUser(user, pass);
        //boolean auth = ip.authenticateUser("root", "omero");
        System.out.println("auth: "+auth);
        AbstractOrbitTree tree = createOrbitTree();
        tree.refresh();
        JFrame frame = new JFrame("Omero");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.add(tree);
        frame.setBounds(0,0,500,700);
        frame.setVisible(true);
        RawUtilsCommon.centerComponent(frame);
    }

    public static void main(String[] args) throws Exception {
        // just a demo
        /*
        ImageProviderOmero ip = new ImageProviderOmero();
        boolean auth = ip.authenticateUser("g2user", "omero");
        System.out.println("auth: "+auth);
        int rdfId = 160;
        RawDataFile rdf = ip.LoadRawDataFile(rdfId);
        System.out.println(rdf.toStringDetail());

        BufferedImage img = ip.getThumbnail(rdf);
        System.out.println("overview image: " + img);

        ip.close();
        */

       // ImageProviderOmero ip = new ImageProviderOmero();
       // ip.showOrbitTree("g2user","omero");

        int id = 219; //219;
        ImageProviderOmero ip = new ImageProviderOmero();
        ip.authenticateUser("root","omero");
        long group = ip.getImageGroup(id);
        RawDataFile rdf = ip.LoadRawDataFile(id);
        IOrbitImage image = ip.createOrbitImage(rdf,0);
        System.out.println(image.getFilename());
        System.out.println("isFluo: "+((OmeroImage)image).isMultiChannel());

        ip.close();

         /*
        long imageId = 160;
        SimpleLogger simpleLogger = new SimpleLogger();
        Gateway gateway = new Gateway(simpleLogger);

        LoginCredentials cred = new LoginCredentials();
        cred.getServer().setHostname("localhost");
        cred.getServer().setPort(4064);
        cred.setApplicationName("Orbit");
        cred.getUser().setUsername("g2user");
        cred.getUser().setPassword("omero");
        cred.setGroupID(-1);    // 84, 34
        ExperimenterData user = gateway.connect(cred);
        //SecurityContext ctx = new SecurityContext(user.getGroupId());
        SecurityContext ctx = new SecurityContext(34);

        try {
            BrowseFacility browse = gateway.getFacility(BrowseFacility.class);
            ImageData image = browse.getImage(ctx, imageId);
            System.out.println(image);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                gateway.disconnect();
            } catch (Exception e1) {};
        }
        */

    }
}
