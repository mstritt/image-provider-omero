# Orbit Omero Connector

### Orbit Image Provider Omero

Generic jar:

    gradle jar

Works with Omero 5.6.x

### Updates:
- 1.7.3: option "useWebSockets=true" in OrbitOmero.properties to work with Omero/websockets
- 1.7.1: Gradle changes, improvement to playground applications
- 1.7.0: Omero 5.6.x compatible, Gradle >= 6

#### TODO:
- getRawDataFileUrl(): auto login / session handling (so far one has to login via webinterface first)
- getRawDataFileUrl(): resolution level  (but only user for Android Downloader)

- OmeroImage::getTileData(): proxy.renderCompressed() server-side compression (better: plain byte copy)

License: GPLv3