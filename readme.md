#Orbit Omero Connector

### Orbit Image Provider Omero

Generic jar:

    gradle jar

Works with Omero 5.2.x (and probably with 5.1.x). For other Omero versions please change dependencies in build.gradle.

### Updates:
- 1.3.6: pixelsId bugfix, getPlane returns always a RGB image
- 1.3.0: implements a IFormat reader, multi-channel (fluorescence) support
- Now works with all groups, not just default group

####TODO:
- getRawDataFileUrl(): auto login / session handling (so far one has to login via webinterface first)
- getRawDataFileUrl(): resolution level  (but only user for Android Downloader)

- OmeroImage::getTileData(): proxy.renderCompressed() server-side compression (better: plain byte copy)

License: GPLv3