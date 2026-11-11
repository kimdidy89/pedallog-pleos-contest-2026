"""Read a diagnostic JSONL file; report observations, not pedal intent or fault cause."""
import argparse
import bisect
import json
import math
import statistics
from pathlib import Path


def distribution(values):
    values = sorted(values)
    if not values:
        return None
    return {"count": len(values), "min": values[0], "median": statistics.median(values),
            "p95": values[min(len(values)-1, math.ceil(len(values)*.95)-1)], "max": values[-1]}


def analyze(path):
    records = []
    with Path(path).open(encoding="utf-8") as stream:
        for line_number, line in enumerate(stream, 1):
            if line.strip():
                try:
                    records.append(json.loads(line))
                except json.JSONDecodeError as error:
                    raise ValueError(f"line {line_number}: invalid/truncated JSON") from error
    starts = [r for r in records if r.get("type") == "session_start"]
    if len(starts) != 1:
        raise ValueError("Expected exactly one session_start; do not merge sessions")
    mode = starts[0]["mode"]
    if any(r.get("mode") != mode for r in records):
        raise ValueError("Mixed DEMO/REAL session")
    speed = [r for r in records if r["type"] == "speed_sample"]
    source_times = [r["data"]["sourceElapsedMs"] for r in speed]
    intervals = [b-a for a, b in zip(source_times, source_times[1:])]
    observations = [r for r in records if r["type"] == "brake_observation"]
    transitions = []
    last_state = None
    for r in observations:
        if r["data"]["state"] != last_state:
            last_state = r["data"]["state"]
            transitions.append({"hostElapsedMs": r["hostElapsedMs"], "state": last_state})
    events = [dict(r["data"], hostElapsedMs=r["hostElapsedMs"]) for r in records if r["type"] == "rapid_speed_growth"]
    transition_times = [r["hostElapsedMs"] for r in transitions]
    for event in events:
        index = bisect.bisect_right(transition_times, event["hostElapsedMs"])-1
        # This is NOT driver reaction time. It is relative to an observed state transition.
        event["sinceObservedBrakeTransitionMs"] = (
            event["hostElapsedMs"] - transitions[index]["hostElapsedMs"]
            if index >= 0 and event["brakeObservation"] != "UNKNOWN" else None)
    brake_reads = [r["data"]["requestDurationMs"] for r in records if r["type"] == "brake_read"]
    configs = [r["data"] for r in records if r["type"] == "property_config"]
    received_properties = sorted({r["data"]["propertyId"] for r in records
                                  if r["type"] == "aaos_property" and r["data"].get("status") == 0
                                  and r["data"].get("value") is not None})
    return {
        "mode": mode,
        "completeSession": bool(records and records[-1]["type"] == "session_end"),
        "scope": "observational_only_no_misapplication_or_safety_claim",
        "recordCount": len(records),
        "speedSourceIntervalMs": distribution([x for x in intervals if x > 0]),
        "speedGapsOver500ms": sum(x > 500 for x in intervals),
        "speedDuplicateOrReordered": sum(x <= 0 for x in intervals),
        "speedSourceAgeMs": distribution([r["data"]["sourceAgeMs"] for r in speed]),
        "brakeReadRoundTripMs": distribution(brake_reads),
        "unknownBrakeObservationCount": sum(r["data"]["state"] == "UNKNOWN" for r in observations),
        "brakeObservationCount": len(observations),
        "brakeTimingLimit": "host receipt only; no vehicle-source timestamp; no reaction-time inference",
        "propertyConfigurations": configs,
        "propertiesWithAvailableSamples": received_properties,
        "acceleratorSamplesActuallyReceived": mode == "REAL" and 0x1160030f in received_properties,
        "brakeTransitions": transitions,
        "rapidSpeedGrowthEvents": events,
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("session", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    report = json.dumps(analyze(args.session), ensure_ascii=False, indent=2)
    if args.output:
        args.output.write_text(report + "\n", encoding="utf-8")
    else:
        print(report)
