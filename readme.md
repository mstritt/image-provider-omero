#Orbit Omero Connector

### Orbit Image Provider Omero

Generic jar:

    gradle jar

Works with Omero 5.4.x

### Updates:
- 1.5.9: save model bugfix

####TODO:
- getRawDataFileUrl(): auto login / session handling (so far one has to login via webinterface first)
- getRawDataFileUrl(): resolution level  (but only user for Android Downloader)

- OmeroImage::getTileData(): proxy.renderCompressed() server-side compression (better: plain byte copy)

License: GPLv3