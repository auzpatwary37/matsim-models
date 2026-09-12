# City fixtures for signal-ready integration tests

Small, real bounded OpenStreetMap extracts, one per region, used by
`OsmCityFixtureIntegrationTest`. Each file is gzip-compressed OSM XML (`.osm.gz`) read by
`OsmNetworkImporter` (the importer detects `.gz` automatically).

| fixture       | Geofabrik region                        | WGS84 bounding box (left,bottom,right,top)     |
|---------------|-----------------------------------------|------------------------------------------------|
| luxembourg    | europe/luxembourg                       | 6.12455,49.59551,6.14055,49.60751              |
| toronto       | north-america/canada/ontario            | -79.40691,43.63552,-79.39091,43.64752          |
| seattle       | north-america/us/washington             | -122.34138,47.59891,-122.32538,47.61091        |
| melbourne     | australia-oceania/australia/victoria    | 144.94608,-37.8275,144.96208,-37.8155          |

Data copyright OpenStreetMap contributors, licensed under ODbL-1.0
(https://www.openstreetmap.org/copyright).

## Regenerating

These files are pinned artifacts; tests never download. To regenerate, download the region PBF
from Geofabrik and cut it with [osmosis](https://wiki.openstreetmap.org/wiki/Osmosis)
(public domain). `completeWays=true` keeps boundary-crossing ways whole, so the extract is
self-contained (no dangling node references) and relations (e.g. turn restrictions) are retained:

```sh
osmosis --read-pbf <region>.osm.pbf \
        --bounding-box left=<L> bottom=<B> right=<R> top=<T> completeWays=true \
        --write-xml <city>.osm
gzip -9 <city>.osm     # -> <city>.osm.gz
```

Then prepend an ODbL attribution comment after the XML declaration if needed. Keep the
extracts small (a few thousand ways) and ensure each contains traffic-signal nodes plus, across
the set, a roundabout, a `turn:lanes` way, and a `type=restriction` relation.
