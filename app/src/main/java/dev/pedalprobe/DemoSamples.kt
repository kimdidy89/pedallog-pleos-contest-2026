package dev.pedalprobe

import java.io.File
import java.time.ZonedDateTime
import org.json.JSONObject

/** 실제 기록과 섞이지 않는 오프라인 예시. 고정 파일명으로 재실행 중복을 막는다. */
internal object DemoSamples {
    fun install(directory:File,now:ZonedDateTime=ZonedDateTime.now()) {
        directory.mkdirs()
        val examples=listOf(
            Triple("city","시내 주행 예시",180),
            Triple("braking","브레이크 입력 확인 예시",120),
            Triple("signal-gap","신호가 잠시 끊긴 예시",90)
        )
        examples.forEachIndexed { index,(id,name,seconds)->
            val destination=File(directory,"demo-example-v1-$id.jsonl")
            if(!destination.exists()) {
                val temporary=File.createTempFile("example-v1-",".tmp",directory)
                try {
                    val wall=now.minusDays(index.toLong()).minusMinutes(20L+index*30)
                    temporary.bufferedWriter(Charsets.UTF_8).use { writer->
                        fun row(t:Long,type:String,data:JSONObject) {
                            writer.append(JSONObject().put("mode","DEMO").put("hostElapsedMs",10000+t)
                                .put("type",type).put("data",data).toString()).append('\n')
                        }
                        row(0,"session_start",JSONObject().put("schemaVersion",1).put("mode","DEMO")
                            .put("wallClockUtc",wall.toInstant().toString()).put("exampleData",true).put("exampleName",name))
                        for(t in 0..seconds*4) {
                            val ms=t*250L;val second=t/4.0
                            if(index==2&&second in 35.0..40.0)continue
                            val phase=second%45.0
                            val speed=when {
                                phase<5->0.0
                                phase<18->(phase-5)/13*(if(index==1)52.0 else 38.0)
                                phase<28->if(index==1)52.0 else 38.0
                                phase<38->(38-phase)/10*(if(index==1)52.0 else 38.0)
                                else->0.0
                            }
                            val brake=if(phase>=28||phase<5)"PRESSED" else "RELEASED"
                            row(ms,"brake_observation",JSONObject().put("state",brake).put("timeKind","host_receipt_only"))
                            row(ms,"speed_sample",JSONObject().put("metresPerSecond",speed/3.6).put("sourceAgeMs",0))
                        }
                        row(seconds*1000L,"session_end",JSONObject().put("reason","미리 준비한 예시 데이터"))
                    }
                    // 기존 파일을 덮어쓰지 않는다. 임시 파일에 완성한 뒤만 목록에 노출한다.
                    if(!destination.exists()&&!temporary.renameTo(destination))throw java.io.IOException("예시 기록을 저장하지 못했습니다")
                } finally {temporary.delete()}
            }
        }
    }
}
