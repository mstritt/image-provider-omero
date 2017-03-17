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

package com.actelion.research.orbit.imageprovider;

import omero.log.LogMessage;
import omero.log.Logger;

public class Slf4jWrapper implements Logger {

    private org.slf4j.Logger log;

    public Slf4jWrapper(org.slf4j.Logger log) {
        this.log = log;
    }

    @Override
    public void debug(Object originator, String logMsg) {
        log.debug(logMsg);
    }

    @Override
    public void debug(Object originator, LogMessage msg) {
        log.debug(String.valueOf(msg));
    }

    @Override
    public void info(Object originator, String logMsg) {
        log.info(logMsg);
    }

    @Override
    public void info(Object originator, LogMessage msg) {
        log.info(String.valueOf(msg));
    }

    @Override
    public void warn(Object originator, String logMsg) {
        log.warn(logMsg);
    }

    @Override
    public void warn(Object originator, LogMessage msg) {
        log.warn(String.valueOf(msg));
    }

    @Override
    public void error(Object originator, String logMsg) {
        log.error(logMsg);
    }

    @Override
    public void error(Object originator, LogMessage msg) {
        log.error(String.valueOf(msg));
    }

    @Override
    public void fatal(Object originator, String logMsg) {
        log.error(logMsg);
    }

    @Override
    public void fatal(Object originator, LogMessage msg) {
        log.error(String.valueOf(msg));
    }

    @Override
    public String getLogFile() {
        return null;
    }
}
