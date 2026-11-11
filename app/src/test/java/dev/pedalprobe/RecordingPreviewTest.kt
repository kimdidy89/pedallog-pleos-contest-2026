package dev.pedalprobe

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RecordingPreviewTest {
    private fun read(vararg rows:String):RecordingPreview {
        val file=File.createTempFile("record-preview-",".jsonl")
        try {file.writeText(rows.joinToString("\n"));return RecordingPreview.read(file)}finally{file.delete()}
    }
    private fun row(time:Long,type:String,data:String,mode:String="DEMO")="""{"hostElapsedMs":$time,"mode":"$mode","type":"$type","data":$data}"""
    @Test fun preservesUnknownAndSpeedQuality() {
        val result=read(row(1000,"session_start","{}"),row(1100,"brake_observation","""{"state":"UNKNOWN"}"""),
            row(1100,"speed_sample","""{"metresPerSecond":-2,"sourceAgeMs":10}"""),
            row(1200,"speed_sample","""{"metresPerSecond":9,"sourceAgeMs":501}"""),row(1300,"session_end","""{"reason":"사용자 중지"}"""))
        assertEquals(SignalEngine.Brake.UNKNOWN,result.brakes.single().state)
        assertEquals(7.2,result.speeds[0].kmh!!,.0001);assertNull(result.speeds[1].kmh)
        assertTrue(result.ended);assertEquals(300,result.end-result.start)
    }
    @Test fun missingBrakeIsNotReleased() {
        val result=read(row(1,"speed_sample","""{"metresPerSecond":0,"sourceAgeMs":0}"""))
        assertTrue(result.brakes.isEmpty());assertFalse(result.ended)
    }
    @Test fun negativeAgeIsNotValidSpeed() {
        val result=read(row(1,"speed_sample","""{"metresPerSecond":10,"sourceAgeMs":-1}"""))
        assertNull(result.speeds.single().kmh)
    }
    @Test(expected=IllegalArgumentException::class) fun rejectsMixedModes() {
        read(row(1,"session_start","{}"),row(2,"session_end","{}","REAL"))
    }
    @Test(expected=IllegalArgumentException::class) fun rejectsClockReversal() {
        read(row(2,"session_start","{}"),row(1,"session_end","{}"))
    }
    @Test(expected=IllegalArgumentException::class) fun rejectsEmptyFile() {read()}
    @Test fun usesRecordedStartClockInsteadOfFileModificationTime() {
        val result=read(row(1000,"session_start","""{"wallClockUtc":"2026-11-04T01:00:00Z"}"""))
        assertEquals(1793754000000L,result.wallStartMs)
    }
    @Test fun selectionPreservesStaleBrakeAndSpeed() {
        val result=read(row(1000,"speed_sample","""{"metresPerSecond":10,"sourceAgeMs":400}"""),
            row(1000,"brake_observation","""{"state":"PRESSED"}"""),row(2500,"session_end","{}"))
        assertEquals(36.0,result.at(1100).first!!,.0001)
        assertNull(result.at(1101).first)
        assertEquals(SignalEngine.Brake.UNKNOWN,result.at(2001).second)
    }
    @Test fun brakeChangesIncludeGapBeforeSameStateReturns() {
        val result=read(row(1000,"brake_observation","""{"state":"RELEASED"}"""),
            row(3000,"brake_observation","""{"state":"RELEASED"}"""),row(4001,"session_end","{}"))
        assertEquals(listOf(SignalEngine.Brake.RELEASED,SignalEngine.Brake.UNKNOWN,SignalEngine.Brake.RELEASED,SignalEngine.Brake.UNKNOWN),result.brakeChanges().map{it.state})
        assertEquals(2001L,result.brakeChanges()[1].time)
    }
    @Test fun fastestIgnoresInvalidSpeedAndMissingClockStaysUnknown() {
        val result=read(row(1000,"speed_sample","""{"metresPerSecond":2,"sourceAgeMs":0}"""),
            row(1100,"speed_sample","""{"metresPerSecond":100,"sourceAgeMs":501}"""))
        assertNull(result.wallStartMs)
        assertEquals(7.2,result.fastest!!.kmh!!,.0001)
    }
}
