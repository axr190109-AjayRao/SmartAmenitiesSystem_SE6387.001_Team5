# Data Branch: GeoJSON Amenity Seed (Iteration 1)

This folder makes Level3 GeoJSON the canonical amenity source for backend usage and keeps the Level 3 walkable corridor network as the routing reference.

## Source Files

- data/source/Level3.geojson

Route/path lines are preserved in source GeoJSON and are not modified.

## Geometry Validation

Use `scripts/validate_level3_geometry.py` to verify that the source geometry is still healthy before regenerating exports.

The validator checks:

- route line feature counts
- duplicate corridor segments
- route segment intersections and overlap connectivity
- amenity anchor distance to the nearest walkable route segment
- allowed amenity types only:
	- MEN_RESTROOM
	- WOMEN_RESTROOM
	- ACCESSIBLE_RESTROOM

## Normalization Rules

Labels are read from `properties.label` or `properties.Label` and normalized case-insensitively:

- contains `men` -> `MEN_RESTROOM`
- contains `women` or `womens` -> `WOMEN_RESTROOM`
- contains `accessible` or `wheelchair` -> `ACCESSIBLE_RESTROOM`

Only `Point` geometry features are considered amenity anchors.

Only the three restroom amenity types above are exported.

## Deduping Rule

Exact duplicate amenity points are removed when all match:

- sourceLevel
- latitude (rounded to 6 decimals)
- longitude (rounded to 6 decimals)
- amenityTypeNormalized

## Outputs

Backend-ready export:

- data/exports/amenities_seed.json

Validation summary:

- data/exports/validation_summary.json

Geometry validation summary:

- data/exports/level3_geometry_validation.json

Validation script:

- data/scripts/validate_level3_geometry.py

## Validation Summary (current)

- total amenity anchors: 30
- deduped removed: 1

Per level:

- Level3: 30

Per amenity type:

- MEN_RESTROOM: 10
- WOMEN_RESTROOM: 10
- ACCESSIBLE_RESTROOM: 10

Issues found in source GeoJSON:

- none
