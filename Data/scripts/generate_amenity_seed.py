import json
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCE_DIR = ROOT / "data" / "source"
EXPORT_DIR = ROOT / "data" / "exports"

SOURCE_FILES = [
    ("Level3", "Level3.geojson"),
]


def normalize_type(label: str):
    lowered = label.lower()
    if "women" in lowered or "womens" in lowered:
        return "WOMEN_RESTROOM"
    if "accessible" in lowered or "wheelchair" in lowered:
        return "ACCESSIBLE_RESTROOM"
    if "men" in lowered:
        return "MEN_RESTROOM"
    return None


def clean_label(label: str):
    return " ".join((label or "").replace("\n", " ").split())


def main():
    EXPORT_DIR.mkdir(parents=True, exist_ok=True)

    issues = []
    rows = []
    dedupe_keys = set()
    dedupe_removed = 0

    per_level = Counter()
    per_type = Counter()

    for source_level, filename in SOURCE_FILES:
        path = SOURCE_DIR / filename
        if not path.exists():
            issues.append(f"Missing source file: {path}")
            continue

        raw = json.loads(path.read_text(encoding="utf-8"))

        if raw.get("type") != "FeatureCollection":
            issues.append(f"{filename}: root type is not FeatureCollection")
            continue

        features = raw.get("features")
        if not isinstance(features, list):
            issues.append(f"{filename}: features is not an array")
            continue

        for idx, feature in enumerate(features):
            geometry = feature.get("geometry") or {}
            geom_type = geometry.get("type")
            coordinates = geometry.get("coordinates")

            if geom_type != "Point":
                continue

            if not isinstance(coordinates, list) or len(coordinates) < 2:
                issues.append(f"{filename}[{idx}]: malformed Point coordinates")
                continue

            lon = coordinates[0]
            lat = coordinates[1]
            if not isinstance(lat, (int, float)) or not isinstance(lon, (int, float)):
                issues.append(f"{filename}[{idx}]: non-numeric coordinates")
                continue

            props = feature.get("properties") or {}
            source_label = props.get("label") or props.get("Label")
            if not source_label:
                continue

            cleaned = clean_label(source_label)
            amenity_type = normalize_type(cleaned)
            if not amenity_type:
                continue

            dedupe_key = (source_level, round(lat, 6), round(lon, 6), amenity_type)
            if dedupe_key in dedupe_keys:
                dedupe_removed += 1
                continue
            dedupe_keys.add(dedupe_key)

            row = {
                "sourceLevel": source_level,
                "sourceLabel": cleaned,
                "amenityTypeNormalized": amenity_type,
                "latitude": lat,
                "longitude": lon,
                "sourceFile": filename,
                "sourceFeatureIndex": idx,
            }
            rows.append(row)
            per_level[source_level] += 1
            per_type[amenity_type] += 1

    rows.sort(key=lambda r: (r["sourceLevel"], r["amenityTypeNormalized"], r["sourceFeatureIndex"]))

    export_payload = {
        "generatedFrom": [name for _, name in SOURCE_FILES],
        "recordCount": len(rows),
        "records": rows,
    }

    summary_payload = {
        "recordCount": len(rows),
        "countsPerLevel": dict(per_level),
        "countsPerAmenityType": dict(per_type),
        "dedupeRemoved": dedupe_removed,
        "issues": issues,
    }

    (EXPORT_DIR / "amenities_seed.json").write_text(json.dumps(export_payload, indent=2), encoding="utf-8")
    (EXPORT_DIR / "validation_summary.json").write_text(json.dumps(summary_payload, indent=2), encoding="utf-8")

    print(json.dumps(summary_payload, indent=2))


if __name__ == "__main__":
    main()
