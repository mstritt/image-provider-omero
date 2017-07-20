#Orbit Omero Connector

### Orbit Image Provider Omero

Generic jar:

    gradle jar

Works with Omero 5.2.x (and probably with 5.1.x). For other Omero versions please change dependencies in build.gradle.

### Updates:
-1.4.13: channelActive bugfix
-1.4.12: bioformats 5.5.3
-1.4.3: Omero 5.3.1 support
-1.4.2: >8bit,<16bit bugfix, isRGBImage() 

####TODO:
- getRawDataFileUrl(): auto login / session handling (so far one has to login via webinterface first)
- getRawDataFileUrl(): resolution level  (but only user for Android Downloader)

- OmeroImage::getTileData(): proxy.renderCompressed() server-side compression (better: plain byte copy)

License: GPLv3