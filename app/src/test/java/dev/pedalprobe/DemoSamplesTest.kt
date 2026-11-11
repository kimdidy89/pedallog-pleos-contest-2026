package dev.pedalprobe

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.ZonedDateTime

class DemoSamplesTest {
    @get:Rule val folder=TemporaryFolder()
    private val now=ZonedDateTime.parse("2026-11-08T14:00:00+09:00")
    @Test fun preparesThreeClearlyMarkedExamples() {
        DemoSamples.install(folder.root,now)
        val files=folder.root.listFiles()!!.filter{it.extension=="jsonl"}
        assertEquals(3,files.size)
        files.forEach {file->
            val data=RecordingPreview.read(file)
            assertEquals("DEMO",data.mode);assertNotNull(data.exampleName)
            assertNotNull(data.wallStartMs);assertTrue(data.ended)
        }
    }
    @Test fun rerunDoesNotDuplicateOrChangeExistingRecords() {
        val existing=folder.newFile("real-user-record.jsonl");existing.writeText("사용자 기록 보존")
        DemoSamples.install(folder.root,now)
        val first=folder.root.listFiles()!!.associate{it.name to it.readText()}
        DemoSamples.install(folder.root,now.plusDays(1))
        assertEquals(first,folder.root.listFiles()!!.associate{it.name to it.readText()})
    }
    @Test fun exampleGapRemainsUnknown() {
        DemoSamples.install(folder.root,now)
        val data=RecordingPreview.read(java.io.File(folder.root,"demo-example-v1-signal-gap.jsonl"))
        assertNull(data.at(data.start+38000).first)
        assertEquals(SignalEngine.Brake.UNKNOWN,data.at(data.start+38000).second)
    }
}
