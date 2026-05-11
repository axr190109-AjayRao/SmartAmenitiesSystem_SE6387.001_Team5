#!/usr/bin/env python3
"""Validate the Level 3 geometry source used by routing.

This script is intentionally read-only. It summarizes route network health,
checks amenity anchors against the walkable corridor network, and writes a
JSON report that can be compared across data refreshes.
"""

from __future__ import annotations

import json
import math
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCE_FILE = ROOT / "data" / "source" / "Level3.geojson"
EXPORT_FILE = ROOT / "data" / "exports" / "level3_geometry_validation.json"
ALLOWED_AMENITY_TYPES = {
    "MEN_RESTROOM",
    "WOMEN_RESTROOM",
    "ACCESSIBLE_RESTROOM",
}
EARTH_RADIUS_METERS = 6_371_000.0
AMENITY_SNAP_THRESHOLD_METERS = 2.5


def clean_label(label: str | None) -> str:
    return " ".join((label or "").replace("\n", " ").split())


def normalize_type(label: str) -> str | None:
    lowered = label.lower()
    if "women" in lowered or "womens" in lowered:
        return "WOMEN_RESTROOM"
    if "accessible" in lowered or "wheelchair" in lowered:
        return "ACCESSIBLE_RESTROOM"
    if "men" in lowered:
        return "MEN_RESTROOM"
    return None


def project(latitude: float, longitude: float, ref_lat_rad: float) -> tuple[float, float]:
    x = EARTH_RADIUS_METERS * math.radians(longitude) * math.cos(ref_lat_rad)
    y = EARTH_RADIUS_METERS * math.radians(latitude)
    return x, y


def point_to_segment_distance(
    latitude: float,
    longitude: float,
    a_latitude: float,
    a_longitude: float,
    b_latitude: float,
    b_longitude: float,
) -> float:
    ref_lat_rad = math.radians(latitude)
    px, py = project(latitude, longitude, ref_lat_rad)
    ax, ay = project(a_latitude, a_longitude, ref_lat_rad)
    bx, by = project(b_latitude, b_longitude, ref_lat_rad)

    abx = bx - ax
    aby = by - ay
    ab_length_squared = (abx * abx) + (aby * aby)
    if ab_length_squared == 0.0:
        return math.hypot(px - ax, py - ay)

    apx = px - ax
    apy = py - ay
    t = max(0.0, min(1.0, ((apx * abx) + (apy * aby)) / ab_length_squared))
    snapped_x = ax + (abx * t)
    snapped_y = ay + (aby * t)
    return math.hypot(px - snapped_x, py - snapped_y)


def orientation(ax: float, ay: float, bx: float, by: float, cx: float, cy: float) -> int:
    value = (by - ay) * (cx - bx) - (bx - ax) * (cy - by)
    if abs(value) < 1e-9:
        return 0
    return 1 if value > 0 else 2


def on_segment(ax: float, ay: float, bx: float, by: float, cx: float, cy: float) -> bool:
    return (
        min(ax, cx) - 1e-9 <= bx <= max(ax, cx) + 1e-9
        and min(ay, cy) - 1e-9 <= by <= max(ay, cy) + 1e-9
    )


def segments_intersect(a: tuple[float, float], b: tuple[float, float], c: tuple[float, float], d: tuple[float, float]) -> bool:
    ax, ay = a
    bx, by = b
    cx, cy = c
    dx, dy = d
    o1 = orientation(ax, ay, bx, by, cx, cy)
    o2 = orientation(ax, ay, bx, by, dx, dy)
    o3 = orientation(cx, cy, dx, dy, ax, ay)
    o4 = orientation(cx, cy, dx, dy, bx, by)

    if o1 != o2 and o3 != o4:
        return True

    if o1 == 0 and on_segment(ax, ay, cx, ay, bx, by):
        return True
    if o2 == 0 and on_segment(ax, ay, dx, dy, bx, by):
        return True
    if o3 == 0 and on_segment(cx, cy, ax, ay, dx, dy):
        return True
    if o4 == 0 and on_segment(cx, cy, bx, by, dx, dy):
        return True
    return False


def colinear_overlap(a: tuple[float, float], b: tuple[float, float], c: tuple[float, float], d: tuple[float, float]) -> bool:
    ax, ay = a
    bx, by = b
    cx, cy = c
    dx, dy = d
    if abs((by - ay) * (cx - ax) - (bx - ax) * (cy - ay)) > 1e-6:
        return False
    if abs((by - ay) * (dx - ax) - (bx - ax) * (dy - ay)) > 1e-6:
        return False
    return not (
        max(ax, bx) < min(cx, dx)
        or max(cx, dx) < min(ax, bx)
        or max(ay, by) < min(cy, dy)
        or max(cy, dy) < min(ay, by)
    )


def flatten_route_segments(features: list[dict]) -> list[dict]:
    route_segments: list[dict] = []
    for feature_index, feature in enumerate(features):
        geometry = feature.get("geometry") or {}
        properties = feature.get("properties") or {}
        label = clean_label(properties.get("label") or properties.get("Label"))
        if label.lower() != "route":
            continue

        geometry_type = geometry.get("type")
        coordinates = geometry.get("coordinates") or []
        if geometry_type == "LineString":
            lines = [coordinates]
        elif geometry_type == "MultiLineString":
            lines = coordinates
        else:
            continue

        for line_index, line in enumerate(lines):
            if not isinstance(line, list) or len(line) < 2:
                continue
            for point_index in range(1, len(line)):
                start = line[point_index - 1]
                end = line[point_index]
                if not (isinstance(start, list) and isinstance(end, list) and len(start) >= 2 and len(end) >= 2):
                    continue
                route_segments.append(
                    {
                        "featureIndex": feature_index,
                        "lineIndex": line_index,
                        "segmentIndex": point_index - 1,
                        "start": (float(start[1]), float(start[0])),
                        "end": (float(end[1]), float(end[0])),
                    }
                )
    return route_segments


def extract_amenities(features: list[dict]) -> list[dict]:
    amenities: list[dict] = []
    for feature_index, feature in enumerate(features):
        geometry = feature.get("geometry") or {}
        if geometry.get("type") != "Point":
            continue
        coordinates = geometry.get("coordinates") or []
        if not isinstance(coordinates, list) or len(coordinates) < 2:
            continue

        properties = feature.get("properties") or {}
        raw_label = clean_label(properties.get("label") or properties.get("Label"))
        if not raw_label:
            continue

        amenity_type = normalize_type(raw_label)
        if amenity_type is None:
            continue

        amenities.append(
            {
                "featureIndex": feature_index,
                "label": raw_label,
                "type": amenity_type,
                "latitude": float(coordinates[1]),
                "longitude": float(coordinates[0]),
            }
        )
    return amenities


def main() -> None:
    data = json.loads(SOURCE_FILE.read_text(encoding="utf-8"))
    features = data.get("features") or []

    route_segments = flatten_route_segments(features)
    amenities = extract_amenities(features)

    issues: list[str] = []
    per_type = Counter(amenity["type"] for amenity in amenities)
    per_label = Counter(amenity["label"] for amenity in amenities)

    if not route_segments:
        issues.append("No Level 3 walkable route segments were found.")

    if any(amenity_type not in ALLOWED_AMENITY_TYPES for amenity_type in per_type):
        issues.append("Unexpected amenity type found in source geometry.")

    duplicate_segment_keys = Counter()
    projected_segments = []
    ref_latitude = 0.0
    ref_count = 0
    for segment in route_segments:
        start = segment["start"]
        end = segment["end"]
        ref_latitude += start[0] + end[0]
        ref_count += 2
        key = tuple(sorted((
            (round(start[0], 6), round(start[1], 6)),
            (round(end[0], 6), round(end[1], 6)),
        )))
        duplicate_segment_keys[key] += 1
        projected_segments.append((start, end))

    duplicate_segments = sum(1 for count in duplicate_segment_keys.values() if count > 1)
    ref_lat_rad = math.radians(ref_latitude / ref_count) if ref_count else 0.0

    intersections = 0
    overlaps = 0
    projected_pairs = [
        (
            project(segment["start"][0], segment["start"][1], ref_lat_rad),
            project(segment["end"][0], segment["end"][1], ref_lat_rad),
        )
        for segment in route_segments
    ]

    for index, segment_a in enumerate(projected_pairs):
        ax, ay = segment_a[0]
        bx, by = segment_a[1]
        for segment_b in projected_pairs[index + 1 :]:
            cx, cy = segment_b[0]
            dx, dy = segment_b[1]
            if segments_intersect((ax, ay), (bx, by), (cx, cy), (dx, dy)):
                intersections += 1
            if colinear_overlap((ax, ay), (bx, by), (cx, cy), (dx, dy)):
                    overlaps += 1

    snap_distances = []
    for amenity in amenities:
        best_distance = None
        for segment in route_segments:
            distance = point_to_segment_distance(
                amenity["latitude"],
                amenity["longitude"],
                segment["start"][0],
                segment["start"][1],
                segment["end"][0],
                segment["end"][1],
            )
            if best_distance is None or distance < best_distance:
                best_distance = distance
        if best_distance is not None:
            snap_distances.append(best_distance)
            if best_distance > AMENITY_SNAP_THRESHOLD_METERS:
                issues.append(
                    f"Amenity '{amenity['label']}' is {best_distance:.2f}m from the nearest walkable segment."
                )

    summary = {
        "sourceFile": str(SOURCE_FILE.name),
        "routeLineSegments": len(route_segments),
        "duplicateRouteSegments": duplicate_segments,
        "intersectionsDetected": intersections,
        "overlapsDetected": overlaps,
        "amenityAnchors": len(amenities),
        "countsPerAmenityType": dict(per_type),
        "countsPerAmenityLabel": dict(per_label),
        "maxAmenitySnapDistanceMeters": round(max(snap_distances), 3) if snap_distances else None,
        "averageAmenitySnapDistanceMeters": round(sum(snap_distances) / len(snap_distances), 3) if snap_distances else None,
        "issues": issues,
    }

    EXPORT_FILE.parent.mkdir(parents=True, exist_ok=True)
    EXPORT_FILE.write_text(json.dumps(summary, indent=2), encoding="utf-8")
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
