/*
 *     Orbit, a versatile image analysis software for biological image-based quantification.
 *     Copyright (C) 2009 - 2019 Idorsia Pharmaceuticals Ltd., Hegenheimermattweg 91, CH-4123 Allschwil, Switzerland.
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

import java.io.Serializable;

public class OmeroConf implements Serializable {
    private String host;
    private int port;
    private int webPort;
    private boolean useSSL;
    private int searchLimit;
    private String userScaleout;
    private String passwordScaleout;
    private int serverNumber;

    private OmeroConf() {

    }

    public OmeroConf(String host, int port) {
        this(host,port,443,true,1000,"","",1);
    }

    public OmeroConf(String host, int port, boolean useSSL) {
        this(host,port,443,useSSL,1000,"","",1);
    }

    public OmeroConf(String host, int port, int webPort, boolean useSSL, int searchLimit, String userScaleout, String passwordScaleout) {
        this(host,port,webPort,useSSL,searchLimit,userScaleout,passwordScaleout,1);
    }

    public OmeroConf(String host, int port, int webPort, boolean useSSL, int searchLimit, String userScaleout, String passwordScaleout, int serverNumber) {
        this.host = host;
        this.port = port;
        this.webPort = webPort;
        this.useSSL = useSSL;
        this.searchLimit = searchLimit;
        this.userScaleout = userScaleout;
        this.passwordScaleout = passwordScaleout;
        this.serverNumber = serverNumber;
    }

    @Override
    public String toString() {
        return "OmeroConf{" +
                "host='" + host + '\'' +
                ", port=" + port +
                ", webPort=" + webPort +
                ", useSSL=" + useSSL +
                ", searchLimit=" + searchLimit +
                ", userScaleout='" + userScaleout + '\'' +
                ", serverNumber=" + serverNumber +
                '}';
    }

    public String getWebURL() {
        if(useSSL) {
            return "https://" + getHost() + ":" + getWebPort();
        } else {
            return "http://" + getHost() + ":" + getWebPort();
        }
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getWebPort() {
        return webPort;
    }

    public void setWebPort(int webPort) {
        this.webPort = webPort;
    }

    public boolean isUseSSL() {
        return useSSL;
    }

    public void setUseSSL(boolean useSSL) {
        this.useSSL = useSSL;
    }

    public int getSearchLimit() {
        return searchLimit;
    }

    public void setSearchLimit(int searchLimit) {
        this.searchLimit = searchLimit;
    }

    public String getUserScaleout() {
        return userScaleout;
    }

    public void setUserScaleout(String userScaleout) {
        this.userScaleout = userScaleout;
    }

    public String getPasswordScaleout() {
        return passwordScaleout;
    }

    public void setPasswordScaleout(String passwordScaleout) {
        this.passwordScaleout = passwordScaleout;
    }

    public int getServerNumber() {
        return serverNumber;
    }

    public void setServerNumber(int serverNumber) {
        this.serverNumber = serverNumber;
    }

}
