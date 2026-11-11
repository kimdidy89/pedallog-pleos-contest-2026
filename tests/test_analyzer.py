import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("analyzer", Path(__file__).parents[1] / "analyze_session.py")
analyzer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(analyzer)


class AnalyzerTest(unittest.TestCase):
    def run_report(self, rows):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "session.jsonl"
            path.write_text("\n".join(json.dumps(r) for r in rows), encoding="utf-8")
            return analyzer.analyze(path)

    def row(self, kind, t=0, data=None, mode="DEMO"):
        return dict(type=kind, hostElapsedMs=t, data=data or {}, mode=mode)

    def test_demo_cannot_establish_accelerator_support(self):
        report = self.run_report([self.row("session_start"), self.row("aaos_property", data={
            "propertyId": 0x1160030f, "status": 0, "value": 80}), self.row("session_end")])
        self.assertFalse(report["acceleratorSamplesActuallyReceived"])

    def test_actual_sample_required(self):
        for value, expected in [(None, False), (0, True), (42, True)]:
            rows = [self.row("session_start", mode="REAL"), self.row("aaos_property", mode="REAL", data={
                "propertyId": 0x1160030f, "status": 0, "value": value}), self.row("session_end",mode="REAL")]
            self.assertEqual(expected, self.run_report(rows)["acceleratorSamplesActuallyReceived"])

    def test_missing_and_out_of_order(self):
        rows = [self.row("session_start")]
        for t in [0,100,100,50,900]:
            rows.append(self.row("speed_sample",t,{"sourceElapsedMs":t,"sourceAgeMs":0}))
        rows.append(self.row("brake_observation",900,{"state":"UNKNOWN"}))
        report = self.run_report(rows)
        self.assertEqual(2, report["speedDuplicateOrReordered"])
        self.assertEqual(1, report["speedGapsOver500ms"])
        self.assertEqual(1, report["unknownBrakeObservationCount"])
        self.assertFalse(report["completeSession"])

    def test_mixed_mode_rejected(self):
        with self.assertRaises(ValueError):
            self.run_report([self.row("session_start"),self.row("session_end",mode="REAL")])

    def test_unknown_does_not_get_transition_duration(self):
        report = self.run_report([self.row("session_start"),
            self.row("brake_observation",100,{"state":"UNKNOWN"}),
            self.row("rapid_speed_growth",200,{"brakeObservation":"UNKNOWN"})])
        self.assertIsNone(report["rapidSpeedGrowthEvents"][0]["sinceObservedBrakeTransitionMs"])


if __name__ == "__main__":
    unittest.main()
