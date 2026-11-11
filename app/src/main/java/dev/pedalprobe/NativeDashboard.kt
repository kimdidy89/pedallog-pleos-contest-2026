package dev.pedalprobe

import android.app.Activity
import android.text.Editable
import android.text.TextWatcher
import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Compose 화면과 기존 Java 기록기 사이의 연결. 실제 인증 없이 명시적인 데모 가입만 제공한다. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
class NativeDashboard internal constructor(
    private val activity: Activity,
    private val ui: UiKit,
    private val actions: Actions
) {
    private companion object {var openingSeen=false}
    private var opening by mutableStateOf(!openingSeen)
    private var openingReady by mutableStateOf(android.os.Build.VERSION.SDK_INT<31||openingSeen)
    interface Actions {
        fun startDemo()
        fun connectVehicle()
        fun diagnostics()
        fun stopRecording()
        fun exportFile(file: File)
    }
    @JvmField val root = ComposeView(activity)
    @JvmField val chart = SignalChart(activity)
    private var modeText by mutableStateOf("수동 기록 대기")
    private var diagnosticText by mutableStateOf("신호 수신 전")
    private var logText by mutableStateOf("")
    @JvmField val mode = bridge {
        modeText=it
        if(it.contains("연결 불가"))displayError="차량 신호에 연결하지 못했어요. 지원 차량에서 다시 확인하거나 체험 기록을 이용해 주세요."
        if(it.contains("기록 실패")||it.contains("기록 파일 생성 실패"))displayError="기록 파일을 만들거나 쓰지 못했어요. 저장 공간을 확인해 주세요."
    }
    @JvmField val diagnostics = bridge { diagnosticText = it }
    @JvmField val log = bridge { logText = it }
    private var page by mutableIntStateOf(0)
    private var active by mutableStateOf(false)
    private var chartVersion by mutableIntStateOf(0)
    private var source by mutableStateOf("NONE")
    private var speed by mutableStateOf("—")
    private var brake by mutableStateOf("미확인")
    private var count by mutableIntStateOf(0)
    private var currentFile by mutableStateOf<File?>(null)
    private var files by mutableStateOf(listOf<File>())
    private var catalogRevision by mutableIntStateOf(0)
    private var recordEntries by mutableStateOf(listOf<RecordEntry>())
    private var catalogLoading by mutableStateOf(false)
    private val historyScroll=androidx.compose.foundation.ScrollState(0)
    private var historyDay by mutableStateOf<java.time.LocalDate?>(null)
    private var selectedFile by mutableStateOf<File?>(null)
    private var provider by mutableStateOf<String?>(null)
    private val preferences = activity.getSharedPreferences("demo_profile", Activity.MODE_PRIVATE)
    private var demoProvider by mutableStateOf(preferences.getString("provider", null))
    private var consentTerms by mutableStateOf(false)
    private var consentPrivacy by mutableStateOf(false)
    private var consentOptional by mutableStateOf(false)
    private var message by mutableStateOf<String?>(null)
    private val experience=activity.getSharedPreferences("app_experience",Activity.MODE_PRIVATE)
    private var themeMode by mutableStateOf(experience.getString("theme_mode","system")?:"system")
    private var onboarding by mutableStateOf(experience.getInt("onboarding_version",0)<9 || !experience.getBoolean("onboarding_complete",false))
    private var introStep by mutableIntStateOf(if(experience.getInt("onboarding_version",0)<9)0 else experience.getInt("onboarding_step",0).coerceIn(0,2))
    private var completedFile by mutableStateOf<File?>(null)
    private var displayError by mutableStateOf<String?>(null)
    private var elapsed by mutableLongStateOf(0L)
    private var recordingStart=0L
    private val ink:Color @Composable get()=MaterialTheme.colorScheme.onSurface
    private val muted:Color @Composable get()=MaterialTheme.colorScheme.onSurfaceVariant
    private val blue:Color @Composable get()=MaterialTheme.colorScheme.primary
    private val pale:Color @Composable get()=MaterialTheme.colorScheme.primaryContainer
    private val paper:Color @Composable get()=MaterialTheme.colorScheme.background
    private val green:Color @Composable get()=MaterialTheme.colorScheme.tertiary
    private val surface:Color @Composable get()=MaterialTheme.colorScheme.surface
    private val font = FontFamily(
        Font("fonts/Pretendard-Regular.otf", activity.assets, FontWeight.Normal),
        Font("fonts/Pretendard-SemiBold.otf", activity.assets, FontWeight.SemiBold),
        Font("fonts/Pretendard-Bold.otf", activity.assets, FontWeight.Bold)
    )

    init {
        if(android.os.Build.VERSION.SDK_INT>=31&&!openingSeen) {
            activity.splashScreen.setOnExitAnimationListener {view->view.remove();openingReady=true}
        }
        refreshFiles()
        chart.setOnDataChanged { chartVersion++ }
        root.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        root.setContent {
            val typography = Typography().let { t ->
                t.copy(
                    displayLarge=t.displayLarge.copy(fontFamily=font), displayMedium=t.displayMedium.copy(fontFamily=font),
                    headlineLarge=t.headlineLarge.copy(fontFamily=font), headlineMedium=t.headlineMedium.copy(fontFamily=font),
                    titleLarge=t.titleLarge.copy(fontFamily=font), titleMedium=t.titleMedium.copy(fontFamily=font),
                    titleSmall=t.titleSmall.copy(fontFamily=font), bodyLarge=t.bodyLarge.copy(fontFamily=font),
                    bodyMedium=t.bodyMedium.copy(fontFamily=font), bodySmall=t.bodySmall.copy(fontFamily=font),
                    labelLarge=t.labelLarge.copy(fontFamily=font), labelMedium=t.labelMedium.copy(fontFamily=font),
                    labelSmall=t.labelSmall.copy(fontFamily=font)
                )
            }
            val systemDark=isSystemInDarkTheme()
            val dark=themeMode=="dark" || (themeMode=="system"&&systemDark)
            fun c(name:String)=Color(ui.themeColor(name,dark))
            val scheme=if(dark)darkColorScheme(
                primary=c("primary"),onPrimary=Color(0xFF3B1D0C),secondary=c("primary"),onSecondary=Color(0xFF3B1D0C),secondaryContainer=c("primarySoft"),onSecondaryContainer=c("primary"),primaryContainer=c("primarySoft"),onPrimaryContainer=c("primary"),
                background=c("background"),onBackground=c("foreground"),surface=c("surface"),onSurface=c("foreground"),
                surfaceVariant=c("unknownSoft"),onSurfaceVariant=c("muted"),outline=c("border"),outlineVariant=c("border"),
                tertiary=c("pressed"),onTertiary=Color(0xFF073A2B),surfaceContainer=c("surface"),surfaceContainerHigh=c("unknownSoft")
            )else lightColorScheme(
                primary=c("primary"),onPrimary=Color.White,secondary=c("primary"),onSecondary=Color.White,secondaryContainer=c("primarySoft"),onSecondaryContainer=c("primary"),primaryContainer=c("primarySoft"),onPrimaryContainer=c("primary"),
                background=c("background"),onBackground=c("foreground"),surface=c("surface"),onSurface=c("foreground"),
                surfaceVariant=c("unknownSoft"),onSurfaceVariant=c("muted"),outline=c("border"),outlineVariant=c("border"),
                tertiary=c("pressed"),surfaceContainer=c("surface"),surfaceContainerHigh=c("unknownSoft")
            )
            SideEffect {ui.window(activity,dark)}
            MaterialTheme(colorScheme=scheme,typography=typography) {
                when {opening->FoxOpening(openingReady){openingSeen=true;opening=false};onboarding->Welcome();else->Shell()}
            }

        }
    }
    private fun bridge(update: (String) -> Unit): TextView = TextView(activity).apply {
        addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { update(s?.toString().orEmpty()) }
            override fun afterTextChanged(s: Editable?) {}
        })
    }
    fun show(next: Int) { page=next; if(next==1) refreshFiles() }
    fun update(recording: Boolean, origin: String, kmh: Double, state: String, records: Int, file: File?) {
        if(recording&&!active){recordingStart=android.os.SystemClock.elapsedRealtime();completedFile=null;displayError=null}
        if(active&&!recording&&file!=null&&records>0&&origin=="DEMO"){completedFile=file;refreshFiles()}
        if(recording)elapsed=(android.os.SystemClock.elapsedRealtime()-recordingStart)/1000
        active=recording;source=origin;speed=if(kmh.isFinite()) String.format(Locale.KOREA,"%.1f",kmh) else "—"
        brake=state;count=records;currentFile=file
    }
    private fun openRecord(file:File) {
        if(active&&file==currentFile)actions.stopRecording()
        selectedFile=file
    }
    private fun completeIntro(account:Boolean) {
        onboarding=false;introStep=0
        experience.edit().putBoolean("onboarding_complete",true).putInt("onboarding_version",9).putInt("onboarding_step",0).apply()
        show(if(account)3 else 0)
    }
    private fun chooseTheme(choice:String) {themeMode=choice;experience.edit().putString("theme_mode",choice).apply()}
    private fun refreshFiles() {
        files=activity.filesDir.listFiles { f -> f.isFile && f.extension=="jsonl" && (f.name.startsWith("demo-") || f.name.startsWith("real-")) }
            ?.sortedByDescending { it.lastModified() }.orEmpty()
        catalogRevision++
    }
    @Composable private fun Glyph(id: Int, tint: Color=muted, size: Int=22) {
        Icon(painterResource(id), contentDescription=null, tint=tint, modifier=Modifier.size(size.dp))
    }
    @Composable private fun Tile(modifier: Modifier=Modifier, content: @Composable ColumnScope.()->Unit) {
        Surface(modifier, shape=RoundedCornerShape(24.dp), color=surface, shadowElevation=1.dp) {
            Column(Modifier.padding(22.dp), verticalArrangement=Arrangement.spacedBy(10.dp), content=content)
        }
    }
    @Composable private fun Pill(text: String, tint: Color=blue, background: Color=pale) {
        Row(Modifier.clip(RoundedCornerShape(50)).background(background).padding(horizontal=12.dp,vertical=7.dp), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(5.dp).background(tint,CircleShape))
            Text(text, color=tint, fontSize=12.sp, fontWeight=FontWeight.SemiBold)
        }
    }
    @Composable private fun Shell() {
        LaunchedEffect(catalogRevision) {
            catalogLoading=true
            val prepared=withContext(Dispatchers.IO){
                val examples=runCatching{DemoSamples.install(activity.filesDir)}
                val actual=activity.filesDir.listFiles{f->f.isFile&&f.extension=="jsonl"&&(f.name.startsWith("demo-")||f.name.startsWith("real-"))}.orEmpty()
                actual.map{RecordEntry.read(it)} to examples.isFailure
            }
            recordEntries=prepared.first
            if(prepared.second)displayError="예시 기록을 준비하지 못했어요. 기기의 저장 공간을 확인해 주세요."
            catalogLoading=false
        }
        selectedFile?.let {file->
            EasyRecordDetail(file,onBack={selectedFile=null},onExport={selectedFile=null;actions.exportFile(file)},graph={data,time->Timeline(data,time)})
            return
        }
        val wide=LocalConfiguration.current.screenWidthDp>=840
        BackHandler(enabled=page!=0 && provider==null && selectedFile==null && message==null) { show(0) }
        Surface(Modifier.fillMaxSize(), color=paper) {
            Row {
                if(wide) Rail()
                Column(Modifier.weight(1f)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal=if(wide)32.dp else 24.dp,vertical=16.dp), verticalAlignment=Alignment.CenterVertically) {
                        if(!wide) { Brand();Spacer(Modifier.weight(1f)) }
                        else { Text(when(page){0->"내 주행 기록";1->"지난 기록";2->"설정";else->"내 계정"},color=muted,fontSize=14.sp);Spacer(Modifier.weight(1f)) }
                        if(page==3 && demoProvider==null) TextButton(onClick={show(0)}) { Text("닫기") }
                        else Surface(onClick={page=3}, shape=CircleShape,color=surface) {
                            Row(Modifier.padding(horizontal=12.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                Glyph(R.drawable.ic_ui_person,blue,18)
                                Text(if(demoProvider==null)"로그인" else "데모 계정",fontSize=13.sp,fontWeight=FontWeight.SemiBold)
                            }
                        }
                    }
                    AnimatedContent(page,modifier=Modifier.weight(1f),transitionSpec={fadeIn(tween(160)) togetherWith fadeOut(tween(90))},label="화면 전환") { target ->
                        when(target) {0->Home(wide);1->History();2->Settings();else->Account(wide)}
                    }
                    if(!wide && (page!=3 || demoProvider!=null)) BottomNavigation()
                }
            }
        }
        if(provider!=null) ConsentSheet()
        message?.let { text -> AlertDialog(onDismissRequest={message=null},title={Text("데모 안내")},text={Text(text)},confirmButton={TextButton(onClick={message=null}){Text("확인")}}) }
    }
    @Composable private fun Brand() {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            FoxPortrait(Modifier.size(40.dp),motion=false)
            Text("페달로그",fontSize=21.sp,fontWeight=FontWeight.Bold,letterSpacing=(-0.6).sp)
        }
    }
    private val navTitles=listOf("홈","지난 기록","설정","내 계정")
    private val navIcons=listOf(R.drawable.ic_ui_home,R.drawable.ic_ui_history,R.drawable.ic_ui_settings,R.drawable.ic_ui_person)
    @Composable private fun Rail() {
        Column(Modifier.width(212.dp).fillMaxHeight().background(surface).padding(20.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Spacer(Modifier.height(12.dp));Brand();Spacer(Modifier.height(38.dp))
            Text("나의 기록",color=muted,fontSize=12.sp,modifier=Modifier.padding(start=12.dp,bottom=4.dp))
            navTitles.forEachIndexed { index,title ->
                Surface(onClick={show(index)},shape=RoundedCornerShape(16.dp),color=if(page==index)pale else surface,modifier=Modifier.fillMaxWidth().semantics{selected=page==index}) {
                    Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Glyph(navIcons[index],if(page==index)blue else muted,21);Text(title,color=if(page==index)blue else muted,fontWeight=if(page==index)FontWeight.SemiBold else FontWeight.Normal,fontSize=15.sp)
                    }
                }
            }
            Spacer(Modifier.weight(1f));FoxMascot(when(page){1->MascotPose.HISTORY;2->MascotPose.SETTINGS;3->MascotPose.ACCOUNT;else->if(active)MascotPose.RECORDING else MascotPose.WELCOME},Modifier.size(96.dp).align(Alignment.CenterHorizontally))
            Text("급발진 의심 상황,\n기록으로 확인하세요.",fontSize=14.sp,color=muted,lineHeight=23.sp)
            Text("1차 데모 · 수동 기록",fontSize=11.sp,color=muted)
        }
    }
    @Composable private fun BottomNavigation() {
        NavigationBar(containerColor=surface,tonalElevation=0.dp,windowInsets=WindowInsets(0,0,0,0)) {
            navTitles.forEachIndexed { index,title -> NavigationBarItem(
                selected=page==index,onClick={show(index)},icon={Glyph(navIcons[index],if(page==index)blue else muted,23)},
                label={Text(title,fontSize=11.sp,fontWeight=if(page==index)FontWeight.SemiBold else FontWeight.Normal)},
                colors=NavigationBarItemDefaults.colors(indicatorColor=pale,selectedTextColor=blue,unselectedTextColor=muted)) }
        }
    }
    @Composable private fun Heading(kicker: String,title: String,description: String) {
        Column(verticalArrangement=Arrangement.spacedBy(7.dp)) {
            Text(kicker,color=blue,fontSize=12.sp,fontWeight=FontWeight.SemiBold,letterSpacing=0.5.sp)
            Text(title,fontSize=28.sp,fontWeight=FontWeight.Bold,lineHeight=39.sp,letterSpacing=(-0.8).sp)
            Text(description,color=muted,fontSize=14.sp,lineHeight=22.sp)
        }
    }
    @Composable private fun Home(wide: Boolean) {
        var graphVisible by remember {mutableStateOf(false)}
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=if(wide)32.dp else 24.dp).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
            FoxHomeHero(active,onHistory={show(1)},onGuide={if(active)actions.stopRecording();introStep=0;onboarding=true;experience.edit().putBoolean("onboarding_complete",false).putInt("onboarding_step",0).apply()})
            if(wide) Row(horizontalArrangement=Arrangement.spacedBy(20.dp),verticalAlignment=Alignment.Top) {
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(16.dp)){StartPanel();if(active){SpeedCard();BrakeCard()}}
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(16.dp)){RecentRecords();StorageCard()}
            } else {
                StartPanel()
                if(active){SpeedCard();BrakeCard()}
                RecentRecords()
                if(active||currentFile!=null)StorageCard()
            }
            if(active)TextButton(onClick={graphVisible=!graphVisible}){Text(if(graphVisible)"자세한 그래프 접기" else "자세한 그래프 보기")}
            if(graphVisible&&active)PlotCard()
            Text("지금은 체험용 앱이에요. 자동 기록과 서버 보관은 준비 중이에요.",fontSize=12.sp,lineHeight=20.sp,color=muted)
        }
    }
    @Composable private fun RecentRecords() {
        Text("가장 최근 기록",fontSize=18.sp,fontWeight=FontWeight.Bold)
        if(catalogLoading)Text("기록을 불러오고 있어요",color=muted)
        else if(recordEntries.isEmpty())MascotMessage(MascotPose.EMPTY,"아직 첫 기록을 기다리고 있어요","위의 체험 버튼으로 10초 동안 기록을 남겨보세요.")
        else recordEntries.sortedByDescending{it.data?.wallStartMs?:Long.MIN_VALUE}.take(2).forEach {RecordRow(it){file->openRecord(file)}}
    }
    @Composable private fun Welcome() {
        LaunchedEffect(Unit) {
            experience.edit().putInt("onboarding_version",9).putBoolean("onboarding_complete",false).apply()
        }
        BackHandler(enabled=introStep>0) {introStep--;experience.edit().putInt("onboarding_step",introStep).apply()}
        PedalIntro(step=introStep,theme=themeMode,splash=false,
            onStep={introStep=it;experience.edit().putInt("onboarding_step",it).apply()},
            onTheme={chooseTheme(it)},onFinish={completeIntro(it)},brand={Brand()})
    }
    @Composable private fun ThemePicker(compact:Boolean=false) {
        Tile(Modifier.fillMaxWidth()) {
            Text(if(compact)"편한 화면을 선택하세요" else "화면 스타일",fontSize=17.sp,fontWeight=FontWeight.SemiBold)
            Text("선택한 설정은 바로 적용되고 다음 실행에도 유지돼요.",fontSize=12.sp,color=muted)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                listOf("system" to "기기 설정 따르기","light" to "라이트","dark" to "다크").forEach {(value,label)->
                    FilterChip(selected=themeMode==value,onClick={chooseTheme(value)},label={Text(label)},modifier=Modifier.heightIn(min=48.dp).semantics {contentDescription="화면 스타일 $label"})
                }
            }
        }
    }
    @Composable private fun StartPanel() {
        if(active) {
            Surface(shape=RoundedCornerShape(20.dp),color=pale,modifier=Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(9.dp).background(green,CircleShape))
                    Column(Modifier.weight(1f)){Text(if(source=="DEMO")"체험 기록 중" else "차량 신호 확인 중",fontWeight=FontWeight.SemiBold);Text(String.format(Locale.KOREA,"%02d:%02d · %,d개 항목",elapsed/60,elapsed%60,count),fontSize=13.sp,color=muted)}
                    FilledTonalButton(onClick={actions.stopRecording()}){Text("기록 마치기")}
                }
            }
        } else {
            Tile(Modifier.fillMaxWidth()) {
                completedFile?.let { file ->
                    Text("체험 기록이 만들어졌어요",fontSize=18.sp,fontWeight=FontWeight.Bold,color=green)
                    Text("방금의 속도와 브레이크를 다시 확인해 보세요.",fontSize=13.sp,color=muted)
                    Button(onClick={openRecord(file)},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),shape=RoundedCornerShape(16.dp)){Text("방금 기록 보기")}
                }
                if(completedFile==null) {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){Glyph(R.drawable.ic_ui_play,blue);Column{Text("10초 기록 체험",fontSize=19.sp,fontWeight=FontWeight.Bold);Text("기록이 움직이고 파일로 남는 과정을 확인하세요.",fontSize=13.sp,color=muted)}}
                }
                displayError?.let {Text(it,color=MaterialTheme.colorScheme.error,fontSize=13.sp,lineHeight=21.sp)}
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Button(onClick={show(0);actions.startDemo()},modifier=Modifier.weight(1f).heightIn(min=54.dp),shape=RoundedCornerShape(16.dp)){Text(if(completedFile==null)"체험 기록 시작" else "한 번 더 체험")}
                    OutlinedButton(onClick={displayError=null;actions.connectVehicle()},modifier=Modifier.weight(1f).heightIn(min=54.dp),shape=RoundedCornerShape(16.dp)){Text("차량 연결")}
                }
                Text("체험에는 모의 데이터가 사용됩니다. 차량 연결은 정차 상태에서 확인하세요.",fontSize=11.sp,color=muted,lineHeight=18.sp)
            }
        }
    }
    @Composable private fun SpeedCard() {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(Color(0xFFAC4414),Color(0xFFD96B28))))) {
            Canvas(Modifier.matchParentSize()) {
                drawCircle(Color.White.copy(alpha=.07f),radius=size.width*.42f,center=Offset(size.width*.98f,size.height*.22f))
                drawCircle(Color.White.copy(alpha=.07f),radius=size.width*.28f,center=Offset(size.width*.98f,size.height*.22f),style=Stroke(1.dp.toPx()))
                drawLine(Color.White.copy(alpha=.15f),Offset(size.width*.66f,size.height*.9f),Offset(size.width*.98f,size.height*.62f),strokeWidth=2.dp.toPx(),cap=StrokeCap.Round)
            }
            Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text("현재 속도",color=Color.White.copy(alpha=.85f),fontSize=14.sp);Spacer(Modifier.weight(1f))
                    Pill(if(active)if(source=="DEMO")"모의 입력" else "API 진단" else "수신 대기",Color.White,Color(0xFF79320F).copy(alpha=.65f))
                }
                Row(verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text(speed,color=Color.White,fontSize=64.sp,lineHeight=74.sp,fontWeight=FontWeight.Bold,letterSpacing=(-2).sp,modifier=Modifier.semantics{contentDescription="현재 속도 $speed 킬로미터 매 시"})
                    Text("km/h",color=Color.White.copy(alpha=.9f),fontSize=17.sp,modifier=Modifier.padding(bottom=10.dp))
                }
                Text(if(active)"지금의 움직임을 기록하고 있어요." else "기록을 시작하면 속도가 표시됩니다.",color=Color.White.copy(alpha=.85f),fontSize=12.sp)
            }
        }
    }
    @Composable private fun BrakeCard() {
        val stateColor=if(brake.startsWith("입력"))green else if(brake.startsWith("해제"))blue else muted
        Tile(Modifier.fillMaxWidth()) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(50.dp).background(MaterialTheme.colorScheme.tertiary.copy(alpha=.12f),RoundedCornerShape(16.dp)),contentAlignment=Alignment.Center){Glyph(R.drawable.ic_ui_brake,green,26)}
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                    Text("브레이크",fontSize=13.sp,color=muted)
                    Text(when{brake.startsWith("입력")->"입력 있음";brake.startsWith("해제")->"입력 없음";else->"정보 없음"},fontSize=23.sp,fontWeight=FontWeight.Bold,color=stateColor)
                }
                Box(Modifier.size(9.dp).background(stateColor,CircleShape))
            }
            Text("받은 신호가 없을 때는 정보 없음으로 표시해요.",fontSize=12.sp,color=muted)
        }
    }
    @Composable private fun StorageCard() {
        Tile(Modifier.fillMaxWidth()) {
            Row(verticalAlignment=Alignment.CenterVertically){Glyph(R.drawable.ic_ui_cloud,blue);Spacer(Modifier.width(8.dp));Text("저장 상태",fontSize=15.sp,fontWeight=FontWeight.SemiBold)}
            InfoRow("기기 기록",if(active)"파일 기록 중" else if(currentFile!=null)"기록 파일 있음" else "대기 중")
            Text("기록은 이 기기에 남아요. 서버 보관은 준비 중이에요.",fontSize=13.sp,color=muted,lineHeight=22.sp)
            InfoRow("서버 동기화","미연결")
        }
    }
    @Composable private fun InfoRow(label:String,value:String) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {Text(label,color=muted,fontSize=13.sp,modifier=Modifier.weight(1f));Text(value,fontSize=13.sp,fontWeight=FontWeight.SemiBold)}
    }
    @Composable private fun PlotCard() {
        Tile(Modifier.fillMaxWidth()) {
            Row(verticalAlignment=Alignment.CenterVertically) {Column(Modifier.weight(1f)){Text("주행 타임라인",fontSize=18.sp,fontWeight=FontWeight.Bold);Text("최근 12초 · 수신 관측 시각 기준",fontSize=12.sp,color=muted)};Glyph(R.drawable.ic_ui_history,blue)}
            Timeline()
            FlowRow(horizontalArrangement=Arrangement.spacedBy(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                listOf("속도 km/h" to blue,"입력" to green,"해제" to Color(0xFF86A8EE),"미확인" to muted).forEach { (label,tint)->
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.size(6.dp).background(tint,CircleShape));Text(label,fontSize=11.sp,color=muted)
                    }
                }
            }
            HorizontalDivider(color=paper)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.Top){Glyph(R.drawable.ic_ui_info,muted,16);Text("미확인 구간은 해제로 처리하지 않습니다.\n운전자 의도나 사고 원인을 판정하지 않습니다.",fontSize=12.sp,color=muted,lineHeight=19.sp)}
        }
    }
    @Composable private fun Timeline(preview:RecordingPreview?=null,windowEnd:Long?=null) {
        val paint=remember {android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {typeface=ui.regular}}
        val axis=muted;val lineColor=blue;val pressed=green;val grid=MaterialTheme.colorScheme.outlineVariant
        val band=MaterialTheme.colorScheme.surfaceVariant
        Canvas(Modifier.fillMaxWidth().height(200.dp).semantics {contentDescription="속도와 브레이크 관측 그래프. 미수신 구간은 연결하지 않습니다."}) {
            chartVersion
            val end=windowEnd?:chart.endTime();val start=end-12000L
            val speeds=(preview?.speeds?:chart.snapshotSpeeds().map {PreviewSpeed(it.time(),it.kmh())}).filter {it.time in start..end}
            val brakes=(preview?.brakes?:chart.snapshotBrakes().map {PreviewBrake(it.time(),it.state())}).filter {it.time<=end&&it.time>=start-SignalEngine.MAX_BRAKE_AGE_MS}
            val left=40.dp.toPx();val right=size.width-8.dp.toPx();val top=20.dp.toPx();val bottom=size.height-56.dp.toPx()
            paint.textSize=11.sp.toPx();paint.color=android.graphics.Color.argb(255,(axis.red*255).toInt(),(axis.green*255).toInt(),(axis.blue*255).toInt())
            if(end<0 || speeds.isEmpty()) {
                paint.textAlign=android.graphics.Paint.Align.CENTER
                drawContext.canvas.nativeCanvas.drawText(if(preview==null)"기록 시작을 기다리고 있어요" else "이 구간에는 속도 기록이 없어요",size.width/2,size.height/2,paint)
                return@Canvas
            }
            val maximum=maxOf(20.0,speeds.mapNotNull {it.kmh}.maxOfOrNull {kotlin.math.ceil(it/10)*10}?:20.0)
            paint.textAlign=android.graphics.Paint.Align.RIGHT
            for(i in 0..2) {
                val y=bottom-(bottom-top)*i/2
                drawLine(grid.copy(alpha=.65f),Offset(left,y),Offset(right,y),1.dp.toPx())
                drawContext.canvas.nativeCanvas.drawText(String.format(Locale.KOREA,"%.0f",maximum*i/2),left-10.dp.toPx(),y+4.dp.toPx(),paint)
            }
            fun x(t:Long)=left+(right-left)*(t-start)/12000f
            val line=Path();var prior=-1L
            speeds.forEach {sample->
                val value=sample.kmh
                if(value==null){prior=-1L}else {
                    val px=x(sample.time);val py=bottom-(value/maximum).toFloat()*(bottom-top)
                    if(prior<0||sample.time-prior>SignalEngine.MAX_SPEED_GAP_MS)line.moveTo(px,py) else line.lineTo(px,py)
                    prior=sample.time
                }
            }
            clipRect(left,top,right,bottom) {drawPath(line,lineColor,style=Stroke(width=2.5.dp.toPx(),cap=StrokeCap.Round))}
            val bandY=bottom+12.dp.toPx();val bandH=9.dp.toPx()
            drawRoundRect(band,Offset(left,bandY),Size(right-left,bandH),CornerRadius(3.dp.toPx()))
            brakes.forEachIndexed { index,point ->
                val until=minOf(end,brakes.getOrNull(index+1)?.time?:end,point.time+SignalEngine.MAX_BRAKE_AGE_MS)
                val x1=maxOf(left,x(point.time));val x2=minOf(right,x(until))
                val color=when(point.state){SignalEngine.Brake.PRESSED->pressed;SignalEngine.Brake.RELEASED->lineColor.copy(alpha=.32f);else->axis}
                if(x2>x1)drawRect(color,Offset(x1,bandY),Size(x2-x1,bandH))
            }
            listOf(left to "−12초",(left+right)/2 to "−6초",right to "선택 시점").forEachIndexed { index,(px,label) ->
                paint.textAlign=when(index){0->android.graphics.Paint.Align.LEFT;1->android.graphics.Paint.Align.CENTER;else->android.graphics.Paint.Align.RIGHT}
                drawContext.canvas.nativeCanvas.drawText(label,px,size.height-5.dp.toPx(),paint)
            }
        }
    }
    @Composable private fun History() {
        EasyHistory(recordEntries,catalogLoading,scroll=historyScroll,chosen=historyDay,onChoose={historyDay=it},onOpen={openRecord(it)},onRefresh={refreshFiles()},onTry={show(0);actions.startDemo()})
    }
    @Composable private fun Settings() {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=24.dp).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            MascotHeading(MascotPose.SETTINGS,"내 기록에 맞춘 설정","화면과 계정을 설정해요.")
            Surface(onClick={page=3},shape=RoundedCornerShape(24.dp),color=surface,modifier=Modifier.fillMaxWidth()) {
                Row(Modifier.padding(22.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.size(52.dp).background(pale,CircleShape),contentAlignment=Alignment.Center){Glyph(R.drawable.ic_ui_person,blue,28)}
                    Column(Modifier.weight(1f)){Text(if(demoProvider==null)"내 계정 연결하기" else "체험 사용자",fontSize=18.sp,fontWeight=FontWeight.SemiBold);Text(if(demoProvider==null)"카카오 · 네이버 간편가입 데모" else "$demoProvider · 데모 계정",fontSize=12.sp,color=muted)};Glyph(R.drawable.ic_ui_arrow)
                }
            }
            Tile(Modifier.fillMaxWidth()) {Text("앱 연결 상태",fontSize=17.sp,fontWeight=FontWeight.SemiBold);InfoRow("자동 기록","준비 중");InfoRow("서버 동기화","미연결");InfoRow("실차 API","권한·지원 확인 필요")}
            Tile(Modifier.fillMaxWidth()) {Text("도움말과 정보",fontSize=17.sp,fontWeight=FontWeight.SemiBold);TextButton(onClick={ui.licenses(activity)}){Text("글꼴 라이선스")};TextButton(onClick={ui.nativeNotices(activity)}){Text("오픈소스 고지")};Text("페달로그 0.10 · 체험 버전",fontSize=12.sp,color=muted)}
            OutlinedButton(onClick={opening=true},modifier=Modifier.fillMaxWidth().heightIn(min=50.dp)){Text("오프닝 다시 보기")}
            ThemePicker()
            TextButton(onClick={if(active)actions.stopRecording();introStep=0;onboarding=true;experience.edit().putBoolean("onboarding_complete",false).putInt("onboarding_step",0).apply()}){Text("처음 안내 다시 보기")}
            OutlinedButton(onClick={actions.diagnostics()},modifier=Modifier.fillMaxWidth().heightIn(min=50.dp),shape=RoundedCornerShape(16.dp)){Text("진단 도구")}
            if(diagnosticText!="신호 수신 전" || logText.isNotEmpty()) Tile(Modifier.fillMaxWidth()) {Text("개발 진단",fontSize=15.sp,fontWeight=FontWeight.SemiBold);Text(modeText,fontSize=12.sp,color=muted);Text(diagnosticText,fontSize=12.sp,color=muted);Text(logText,fontSize=11.sp,color=muted)}
        }
    }
    @Composable private fun Account(wide: Boolean) {
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=24.dp).padding(bottom=28.dp),contentAlignment=Alignment.TopCenter) {
            Column(Modifier.widthIn(max=500.dp).fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(14.dp)) {
                Pill("간편가입 데모")
                FoxMascot(MascotPose.ACCOUNT,Modifier.fillMaxWidth().height(if(wide)140.dp else 120.dp))
                if(demoProvider==null) {
                    Text("나의 주행 기록,\n한곳에서 이어서.",fontSize=30.sp,lineHeight=42.sp,fontWeight=FontWeight.Bold,letterSpacing=(-.8).sp)
                    Text("익숙한 계정으로 가볍게 시작하세요.\n기록은 그대로, 나의 계정과 함께.",fontSize=14.sp,lineHeight=23.sp,color=muted)
                    Spacer(Modifier.height(2.dp))
                    ProviderButton("카카오",Color(0xFFFEE500),Color(0xFF191919))
                    ProviderButton("네이버",Color(0xFF03C75A),Color(0xFF073B21))
                    Text("로그인과 회원가입이 함께 진행됩니다.",fontSize=12.sp,color=muted)
                    TextButton(onClick={show(0)}){Text("계정 없이 기기에 기록하기")}
                    Text("현재는 체험용입니다. 실제 서비스에 연결하거나\n개인정보를 수집·전송하지 않습니다.",fontSize=11.sp,color=muted,lineHeight=19.sp)
                } else {
                    Text("반가워요, 체험 사용자님.",fontSize=25.sp,fontWeight=FontWeight.Bold)
                    Pill("$demoProvider · 데모 계정",green,green.copy(alpha=.12f))
                    Tile(Modifier.fillMaxWidth()){InfoRow("가입 방식",demoProvider!!);InfoRow("계정 상태","체험용 · 실제 인증 아님");InfoRow("서버 연결","미연결")}
                    Button(onClick={show(0)},shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth().heightIn(min=54.dp)){Text("내 기록으로 이동")}
                    TextButton(onClick={preferences.edit().remove("provider").apply();demoProvider=null}){Text("데모 계정 로그아웃")}
                }
            }
        }
    }
    @Composable private fun ProviderButton(name:String,background:Color,foreground:Color) {
        Button(onClick={provider=name;consentTerms=false;consentPrivacy=false;consentOptional=false},modifier=Modifier.fillMaxWidth().heightIn(min=56.dp),shape=RoundedCornerShape(16.dp),colors=ButtonDefaults.buttonColors(containerColor=background,contentColor=foreground)) {
            Text("${name}로 계속하기",fontSize=16.sp,fontWeight=FontWeight.SemiBold)
        }
    }
    @Composable private fun JourneyArt(modifier:Modifier) {
        val artBlue=blue;val artGreen=green;val artPale=pale;val roadColor=MaterialTheme.colorScheme.surface
        Canvas(modifier) {
            val w=size.width;val h=size.height
            drawCircle(artPale,h*.45f,Offset(w*.5f,h*.52f))
            val road=Path().apply {moveTo(w*.12f,h*.79f);cubicTo(w*.3f,h*.8f,w*.29f,h*.45f,w*.47f,h*.5f);cubicTo(w*.66f,h*.56f,w*.62f,h*.2f,w*.86f,h*.25f)}
            drawPath(road,roadColor,style=Stroke(h*.11f,cap=StrokeCap.Round))
            drawPath(road,Color(0xFFC4D5FF),style=Stroke(2.dp.toPx(),cap=StrokeCap.Round))
            drawRoundRect(artBlue,Offset(w*.41f,h*.34f),Size(w*.17f,h*.35f),CornerRadius(16.dp.toPx()))
            drawRoundRect(Color(0xFFB6D1FF),Offset(w*.435f,h*.39f),Size(w*.12f,h*.11f),CornerRadius(5.dp.toPx()))
            drawLine(Color.White,Offset(w*.45f,h*.62f),Offset(w*.54f,h*.62f),3.dp.toPx(),StrokeCap.Round)
            drawCircle(artGreen,7.dp.toPx(),Offset(w*.82f,h*.25f));drawCircle(Color.White,3.dp.toPx(),Offset(w*.82f,h*.25f))
        }
    }
    @Composable private fun ConsentSheet() {
        ModalBottomSheet(onDismissRequest={provider=null},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=surface) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=24.dp).padding(bottom=32.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Pill("실제 인증 없는 데모")
                Text("간편하게 시작할 준비가 됐어요.",fontSize=23.sp,fontWeight=FontWeight.Bold)
                Text("$provider 간편가입을 체험합니다.\n실제 인증과 회원 등록은 진행하지 않습니다.",fontSize=13.sp,color=muted,lineHeight=21.sp)
                Agreement("[필수] 서비스 이용약관",consentTerms,{consentTerms=it})
                Agreement("[필수] 개인정보 처리 안내",consentPrivacy,{consentPrivacy=it})
                Agreement("[선택] 서비스 소식 받기",consentOptional,{consentOptional=it})
                Button(onClick={val selected=provider!!;preferences.edit().putString("provider",selected).apply();demoProvider=selected;provider=null},enabled=consentTerms&&consentPrivacy,modifier=Modifier.fillMaxWidth().heightIn(min=54.dp),shape=RoundedCornerShape(16.dp)){Text("데모로 시작하기")}
                Text("체험 상태만 이 기기에 저장합니다.\n실제 계정·토큰은 생성하지 않습니다.",fontSize=11.sp,color=muted,lineHeight=18.sp)
            }
        }
    }
    @Composable private fun Agreement(title:String,checked:Boolean,onChange:(Boolean)->Unit) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Checkbox(checked=checked,onCheckedChange=onChange,modifier=Modifier.semantics{contentDescription=title})
            Text(title,fontSize=14.sp,modifier=Modifier.weight(1f).clickable{onChange(!checked)})
            TextButton(onClick={message="이 항목은 간편가입 흐름을 확인하기 위한 데모입니다. 정식 약관과 개인정보 처리방침은 실제 서비스 연결 전에 별도로 제공합니다."}){Text("보기",fontSize=12.sp)}
        }
    }
}
