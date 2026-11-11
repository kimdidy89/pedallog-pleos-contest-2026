package dev.pedalprobe

import java.io.File
import org.json.JSONObject

internal data class PreviewSpeed(val time:Long,val kmh:Double?,val sourceAgeMs:Long=0)
internal data class PreviewBrake(val time:Long,val state:SignalEngine.Brake)
internal data class RecordingPreview(
    val speeds:List<PreviewSpeed>,val brakes:List<PreviewBrake>,val start:Long,val end:Long,
    val mode:String,val count:Int,val ended:Boolean,val reason:String,val wallStartMs:Long?=null,val exampleName:String?=null
) {
    val durationMs:Long get()=(end-start).coerceAtLeast(0)
    val fastest:PreviewSpeed? get()=speeds.filter{it.kmh!=null}.maxByOrNull{it.kmh!!}
    fun at(time:Long):Pair<Double?,SignalEngine.Brake> {
        val cursor=time.coerceIn(start,end)
        val s=speeds.lastOrNull{it.time<=cursor}
        val b=brakes.lastOrNull{it.time<=cursor}
        return Pair(s?.takeIf{cursor-it.time<=SignalEngine.MAX_SPEED_GAP_MS-it.sourceAgeMs}?.kmh,
            b?.takeIf{cursor-it.time<=SignalEngine.MAX_BRAKE_AGE_MS}?.state?:SignalEngine.Brake.UNKNOWN)
    }
    fun brakeChanges():List<PreviewBrake> {
        val changes=mutableListOf<PreviewBrake>();var previous:PreviewBrake?=null
        brakes.forEach { point->
            val prior=previous
            if(prior!=null&&point.time-prior.time>SignalEngine.MAX_BRAKE_AGE_MS&&prior.state!=SignalEngine.Brake.UNKNOWN)
                changes.add(PreviewBrake(prior.time+SignalEngine.MAX_BRAKE_AGE_MS+1,SignalEngine.Brake.UNKNOWN))
            if(changes.lastOrNull()?.state!=point.state)changes.add(point)
            previous=point
        }
        previous?.let {if(end-it.time>SignalEngine.MAX_BRAKE_AGE_MS&&it.state!=SignalEngine.Brake.UNKNOWN)
            changes.add(PreviewBrake(it.time+SignalEngine.MAX_BRAKE_AGE_MS+1,SignalEngine.Brake.UNKNOWN))}
        return changes
    }
    companion object {
        fun read(file:File):RecordingPreview {
            require(file.length()<=32L*1024*1024){"이 파일은 앱 내 미리보기 크기 한도를 넘었습니다. 원본으로 확인해 주세요."}
            val speeds=mutableListOf<PreviewSpeed>();val brakes=mutableListOf<PreviewBrake>()
            var start=-1L;var end=-1L;var mode="UNKNOWN";var count=0;var ended=false;var reason="마감 정보 없음";var wallStart:Long?=null;var exampleName:String?=null
            file.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { line ->
                    if(line.isNotBlank()) {
                        require(++count<=100002){"기록 항목이 미리보기 한도를 넘었습니다."}
                        val row=JSONObject(line);val time=row.getLong("hostElapsedMs");val data=row.optJSONObject("data")?:JSONObject()
                        require(time>=0&&(end<0||time>=end)){"기록 시각의 순서를 확인할 수 없습니다."}
                        if(start<0)start=time
                        end=maxOf(end,time)
                        val rowMode=row.optString("mode","UNKNOWN")
                        require(rowMode=="DEMO"||rowMode=="REAL"){"기록 출처를 확인할 수 없습니다."}
                        if(mode=="UNKNOWN")mode=rowMode
                        require(rowMode==mode){"모의 기록과 차량 기록이 섞인 파일입니다."}
                        when(row.optString("type")) {
                            "session_start" -> {
                                wallStart=runCatching {java.time.Instant.parse(data.getString("wallClockUtc")).toEpochMilli()-(time-start)}.getOrNull()
                                if(mode=="DEMO"&&data.optBoolean("exampleData",false))exampleName=data.optString("exampleName","미리 준비한 예시").take(40)
                            }
                            "speed_sample" -> {
                                val value=data.optDouble("metresPerSecond",Double.NaN)
                                val age=data.optLong("sourceAgeMs",Long.MAX_VALUE)
                                val valid=value.isFinite()&&age in 0..SignalEngine.MAX_SPEED_GAP_MS
                                speeds.add(PreviewSpeed(time,if(valid)kotlin.math.abs(value)*3.6 else null,age))
                            }
                            "brake_observation" -> {
                                val state=when(data.optString("state")){"PRESSED"->SignalEngine.Brake.PRESSED;"RELEASED"->SignalEngine.Brake.RELEASED;else->SignalEngine.Brake.UNKNOWN}
                                brakes.add(PreviewBrake(time,state))
                            }
                            "session_end" -> {ended=true;reason=data.optString("reason","기록 마감")}
                        }
                    }
                }
            }
            require(count>0){"아직 기록된 항목이 없습니다."}
            return RecordingPreview(speeds,brakes,start,end,mode,count,ended,reason,wallStart,exampleName)
        }
    }
}
