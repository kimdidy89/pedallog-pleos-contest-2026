package dev.pedalprobe

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal data class RecordOverview(val wallStartMs:Long?,val durationMs:Long,val mode:String,val fastest:Double?,val ended:Boolean,val exampleName:String?=null)
internal data class RecordEntry(val file:File,val data:RecordOverview?,val error:String?) {
    val day:LocalDate? get()=data?.wallStartMs?.let{Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()}
    companion object {
        fun read(file:File)=runCatching {
            val p=RecordingPreview.read(file)
            RecordEntry(file,RecordOverview(p.wallStartMs,p.durationMs,p.mode,p.fastest?.kmh,p.ended,p.exampleName),null)
        }
            .getOrElse{RecordEntry(file,null,"기록 내용을 읽지 못했어요")}
    }
}
internal fun clockText(ms:Long?,seconds:Boolean=false):String=ms?.let {
    Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(if(seconds)"a h:mm:ss" else "a h:mm",Locale.KOREA))
}?:"시각 확인 필요"
internal fun durationText(ms:Long):String {
    val total=ms.coerceAtLeast(0)/1000
    return when {total>=3600->"${total/3600}시간 ${(total%3600)/60}분";total>=60->"${total/60}분 ${total%60}초";else->"${total}초"}
}
private fun speedText(value:Double?)=value?.let{String.format(Locale.KOREA,"%.1f",it)}?:"—"
private fun brakeText(state:SignalEngine.Brake)=when(state){
    SignalEngine.Brake.PRESSED->"브레이크 입력 있음"
    SignalEngine.Brake.RELEASED->"브레이크 입력 없음"
    else->"브레이크 정보 없음"
}

@OptIn(ExperimentalLayoutApi::class,ExperimentalMaterial3Api::class)
@Composable internal fun EasyHistory(entries:List<RecordEntry>,loading:Boolean,scroll:androidx.compose.foundation.ScrollState,chosen:LocalDate?,onChoose:(LocalDate?)->Unit,onOpen:(File)->Unit,onRefresh:()->Unit,onTry:()->Unit) {
    val c=MaterialTheme.colorScheme
    var picker by remember {mutableStateOf(false)}
    val filtered=entries.filter {chosen==null||it.day==chosen}.sortedByDescending {it.data?.wallStartMs?:Long.MIN_VALUE}
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal=24.dp).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        MascotHeading(MascotPose.HISTORY,"지난 기록","날짜별로 기록을 찾아보세요.")
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            FilterChip(selected=chosen==null,onClick={onChoose(null)},label={Text("전체")},modifier=Modifier.heightIn(min=48.dp))
            FilterChip(selected=chosen==LocalDate.now(),onClick={onChoose(LocalDate.now())},label={Text("오늘")},modifier=Modifier.heightIn(min=48.dp))
            OutlinedButton(onClick={picker=true},modifier=Modifier.heightIn(min=48.dp)){Text(if(chosen!=null&&chosen!=LocalDate.now())chosen.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일",Locale.KOREA)) else "날짜 선택")}
        }
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text(if(loading)"기록을 불러오고 있어요" else "${filtered.size}개의 기록",color=c.onSurfaceVariant,modifier=Modifier.weight(1f))
            TextButton(onClick=onRefresh,enabled=!loading){Text("새로고침")}
        }
        if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(!loading&&filtered.isEmpty()) {
            Surface(shape=RoundedCornerShape(24.dp),color=c.surface) {
                Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    MascotMessage(MascotPose.EMPTY,if(entries.isEmpty())"아직 저장한 기록이 없어요" else "이 날짜에는 기록이 없어요",if(entries.isEmpty())"10초 체험으로 첫 기록을 남겨보세요." else "다른 날짜를 골라 보세요.\n전체 기록도 볼 수 있어요.")
                    if(entries.isEmpty()) Button(onClick=onTry){Text("10초 체험하기")} else OutlinedButton(onClick={onChoose(null)}){Text("전체 기록 보기")}
                }
            }
        }
        filtered.groupBy{it.day}.forEach {(day,group)->
            val label=day?.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일 EEEE",Locale.KOREA))?:"날짜 확인이 필요한 기록"
            Text((if(day==LocalDate.now())"오늘 · " else "")+label,fontSize=16.sp,fontWeight=FontWeight.Bold)
            group.forEach {RecordRow(it,onOpen)}
        }
        Text("기기에 남은 기록이에요. 서버 보관은 아직 연결되지 않았어요.",fontSize=12.sp,color=c.onSurfaceVariant,lineHeight=20.sp)
    }
    if(picker) {
        val config=android.content.res.Configuration(LocalConfiguration.current).apply{setLocale(Locale.KOREA)}
        val context=LocalContext.current
        val koreanContext=remember(config,context){context.createConfigurationContext(config)}
        CompositionLocalProvider(LocalConfiguration provides config,LocalContext provides koreanContext) {
        val state=remember {DatePickerState(locale=Locale.KOREA,initialSelectedDateMillis=(chosen?:LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())}
        DatePickerDialog(onDismissRequest={picker=false},confirmButton={TextButton(onClick={state.selectedDateMillis?.let{onChoose(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())};picker=false}){Text("이 날짜 보기")}},dismissButton={TextButton(onClick={picker=false}){Text("취소")}}) {
            CompositionLocalProvider(LocalConfiguration provides config,LocalContext provides koreanContext) {
            DatePicker(state=state,title={Text("기록 날짜 선택",modifier=Modifier.padding(24.dp))},showModeToggle=false)
            }
        }
        }
    }
}

@Composable internal fun RecordRow(entry:RecordEntry,onOpen:(File)->Unit) {
    val c=MaterialTheme.colorScheme
    Surface(onClick={onOpen(entry.file)},shape=RoundedCornerShape(22.dp),color=c.surface,modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text(clockText(entry.data?.wallStartMs),fontSize=21.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                Text(if(entry.data?.exampleName!=null)"예시 데이터" else when(entry.data?.mode){"DEMO"->"체험 기록";"REAL"->"차량 연결 기록";else->"내용 확인 필요"},fontSize=12.sp,color=c.primary)
            }
            entry.data?.let {data->
                data.exampleName?.let{Text(it,fontSize=17.sp,fontWeight=FontWeight.SemiBold)}
                Text("${durationText(data.durationMs)} 동안 기록",fontSize=15.sp)
                Text(data.fastest?.let{"가장 빠른 속도 ${speedText(it)} km/h"}?:"받은 속도 정보가 없어요",fontSize=13.sp,color=c.onSurfaceVariant)
                if(!data.ended)Text("기록이 끝난 시점은 확인이 필요해요",fontSize=12.sp,color=c.onSurfaceVariant)
            }?:Text(entry.error?:"내용 확인 필요",color=c.onSurfaceVariant)
            Text("기록 살펴보기  ›",fontSize=14.sp,color=c.primary,fontWeight=FontWeight.SemiBold)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun EasyRecordDetail(file:File,onBack:()->Unit,onExport:()->Unit,graph:@Composable (RecordingPreview,Long)->Unit) {
    val c=MaterialTheme.colorScheme
    var data by remember(file){mutableStateOf<RecordingPreview?>(null)}
    var failed by remember(file){mutableStateOf(false)}
    var cursor by remember(file){mutableFloatStateOf(0f)}
    var playing by remember(file){mutableStateOf(false)}
    var advanced by remember(file){mutableStateOf(false)}
    var moreMoments by remember(file){mutableIntStateOf(6)}
    LaunchedEffect(file){withContext(Dispatchers.IO){runCatching{RecordingPreview.read(file)}}.onSuccess{data=it;cursor=((it.speeds.firstOrNull{s->s.kmh!=null}?.time?:it.start)-it.start)/1000f}.onFailure{failed=true}}
    LaunchedEffect(playing,data){
        val record=data
        if(playing&&record!=null) {
            val end=record.durationMs/1000f
            while(cursor<end){delay(1000);cursor=(cursor+1).coerceAtMost(end)}
            playing=false
        }
    }
    BackHandler(onBack=onBack)
    Surface(Modifier.fillMaxSize(),color=c.background) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.widthIn(max=900.dp).fillMaxWidth().align(Alignment.CenterHorizontally).padding(horizontal=16.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                TextButton(onClick=onBack){Text("뒤로")}
                Text("기록 살펴보기",fontSize=20.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                FoxMascot(MascotPose.HISTORY,Modifier.size(52.dp))
            }
            Column(Modifier.weight(1f).widthIn(max=900.dp).fillMaxWidth().align(Alignment.CenterHorizontally).verticalScroll(rememberScrollState()).padding(horizontal=24.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                data?.let {record->
                    val moment=record.start+(cursor*1000).toLong().coerceIn(0,record.durationMs)
                    val (speed,brake)=record.at(moment)
                    Text(record.exampleName?.let{"예시 데이터 · $it"}?:if(record.mode=="DEMO")"체험으로 만든 기록" else "차량 연결로 받은 기록",color=c.primary,fontSize=13.sp,fontWeight=FontWeight.SemiBold)
                    Text(record.wallStartMs?.let{Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy년 M월 d일 a h:mm",Locale.KOREA))+"에 시작"}?:"시작 시각 확인 필요",fontSize=24.sp,fontWeight=FontWeight.Bold)
                    Text("${durationText(record.durationMs)} 동안 기록했어요.  ${if(record.ended)"기록이 종료됐어요." else "종료 정보는 없어요."}",fontSize=14.sp,color=c.onSurfaceVariant)
                    Text("원하는 순간으로 바로 가기",fontSize=17.sp,fontWeight=FontWeight.Bold)
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(onClick={playing=false;cursor=0f}){Text("처음")}
                        record.fastest?.let{peak->OutlinedButton(onClick={playing=false;cursor=(peak.time-record.start)/1000f}){Text("가장 빠를 때")}}
                        OutlinedButton(onClick={playing=false;cursor=record.durationMs/1000f}){Text("마지막")}
                    }
                    Surface(shape=RoundedCornerShape(24.dp),color=c.surface) {
                        Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){Text("이때 어떤 상태였나요?",fontSize=20.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));FoxMascot(if(speed==null||brake==SignalEngine.Brake.UNKNOWN)MascotPose.UNKNOWN else MascotPose.HISTORY,Modifier.size(64.dp))}
                            Text(if(record.wallStartMs!=null)clockText(record.wallStartMs!!+(moment-record.start),true) else "시작 후 ${durationText(moment-record.start)}",fontSize=15.sp,color=c.primary)
                            Text(if(speed==null)"속도 정보 없음" else "시속 ${speedText(speed)} km",fontSize=30.sp,fontWeight=FontWeight.Bold)
                            Text(brakeText(brake),fontSize=20.sp,fontWeight=FontWeight.SemiBold,color=if(brake==SignalEngine.Brake.UNKNOWN)c.onSurfaceVariant else c.primary)
                            Text(if(speed==null||brake==SignalEngine.Brake.UNKNOWN)"이 시점에 받지 못한 정보는 추측해서 채우지 않아요." else "이 시점에 앱이 받은 정보예요.",fontSize=13.sp,color=c.onSurfaceVariant,lineHeight=21.sp)
                            val seconds=record.durationMs/1000f
                            if(seconds>0)Slider(value=cursor.coerceIn(0f,seconds),onValueChange={playing=false;cursor=it},valueRange=0f..seconds,modifier=Modifier.semantics{contentDescription="보고 싶은 시간 선택"})
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("시작",fontSize=12.sp,color=c.onSurfaceVariant);Text("시작 후 ${durationText(moment-record.start)} / 전체 ${durationText(record.durationMs)}",fontSize=12.sp,color=c.onSurfaceVariant)}
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick={playing=false;cursor=(cursor-1).coerceAtLeast(0f)},modifier=Modifier.weight(1f).heightIn(min=48.dp),contentPadding=PaddingValues(8.dp)){Text("1초 이전",fontSize=13.sp)}
                                FilledTonalButton(onClick={if(!playing&&cursor>=seconds)cursor=0f;playing=!playing},enabled=seconds>0,modifier=Modifier.weight(1f).heightIn(min=48.dp),contentPadding=PaddingValues(8.dp)){Text(if(playing)"멈춤" else "재생",fontSize=13.sp)}
                                OutlinedButton(onClick={playing=false;cursor=(cursor+1).coerceAtMost(seconds)},modifier=Modifier.weight(1f).heightIn(min=48.dp),contentPadding=PaddingValues(8.dp)){Text("1초 이후",fontSize=13.sp)}
                            }
                            Text("재생을 누르면 1초씩 따라 볼 수 있어요.",fontSize=12.sp,color=c.onSurfaceVariant)

                        }
                    }
                    val changes=remember(record){record.brakeChanges()}
                    Text("브레이크 정보가 바뀐 순간",fontSize=18.sp,fontWeight=FontWeight.Bold)
                    if(changes.isEmpty())Text("이 기록에서는 브레이크 정보를 받지 못했어요.",color=c.onSurfaceVariant,lineHeight=24.sp)
                    changes.take(moreMoments).forEach {point->
                        Surface(onClick={playing=false;cursor=(point.time-record.start)/1000f},shape=RoundedCornerShape(16.dp),color=c.surface,modifier=Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                Text("시작 후 ${durationText(point.time-record.start)}",color=c.primary,fontSize=13.sp)
                                Text(brakeText(point.state),fontSize=16.sp,fontWeight=FontWeight.SemiBold)
                                Text("눌러서 이때의 속도도 확인하세요",fontSize=12.sp,color=c.onSurfaceVariant)
                            }
                        }
                    }
                    if(changes.size>moreMoments)TextButton(onClick={moreMoments+=10}){Text("바뀐 순간 더 보기")}
                    TextButton(onClick={advanced=!advanced},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(if(advanced)"그래프와 파일 정보 접기" else "그래프와 파일 정보 펼치기")}
                    if(advanced) {
                        Text("선택한 시점까지의 최근 12초",fontSize=15.sp,fontWeight=FontWeight.SemiBold)
                        graph(record,moment)
                        Text("선: 속도  ·  아래 띠: 브레이크 입력 상태\n시각은 앱에서 신호를 받은 때를 기준으로 해요.",fontSize=13.sp,color=c.onSurfaceVariant,lineHeight=21.sp)
                        Text(file.name,fontSize=12.sp,color=c.onSurfaceVariant)
                        Text("기록 항목 ${record.count}개 · ${if(record.ended)"종료 정보 있음" else "종료 정보 없음"}",fontSize=12.sp,color=c.onSurfaceVariant)
                    }
                }?:if(failed)MascotMessage(MascotPose.UNKNOWN,"기록을 확인하지 못했어요","원본 파일을 내보내 확인할 수 있어요.")else CircularProgressIndicator()
                OutlinedButton(onClick={playing=false;onExport()},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text("기록 파일 내보내기")}
                Text("기록은 당시 받은 정보를 보여줘요. 운전자의 의도나 사고 원인을 판단하지 않아요.",fontSize=12.sp,color=c.onSurfaceVariant,lineHeight=20.sp)
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
