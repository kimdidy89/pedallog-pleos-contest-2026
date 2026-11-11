package dev.pedalprobe

import android.animation.ValueAnimator
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 오프라인 캐릭터 이미지와 네이티브 UI로 구성한 제품 소개. 예시는 실차 신호와 분리한다. */
@Composable
internal fun PedalIntro(
    step:Int, theme:String, splash:Boolean,
    onStep:(Int)->Unit, onTheme:(String)->Unit,
    onFinish:(Boolean)->Unit, brand:@Composable ()->Unit
) {
    val c=MaterialTheme.colorScheme
    val largeText=LocalDensity.current.fontScale>1.3f
    Surface(Modifier.fillMaxSize(),color=c.background) {
        if(splash) {
            Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                FoxPortrait(Modifier.size(200.dp),motion=false)
                Spacer(Modifier.height(24.dp));brand()
                Text("주행의 순간을, 나의 기록으로.",color=c.onSurfaceVariant,fontSize=14.sp,modifier=Modifier.padding(top=12.dp))
            }
        } else BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide=maxWidth>=760.dp
            val sceneHeight=if(largeText)360.dp else 220.dp
            val gutter=if(wide)40.dp else 24.dp
            Column(Modifier.fillMaxSize().padding(horizontal=gutter,vertical=12.dp)) {
                if(largeText) {
                    brand()
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        IntroThemeMenu(theme,onTheme);Spacer(Modifier.weight(1f))
                        TextButton(onClick={onFinish(false)},modifier=Modifier.heightIn(min=48.dp)){Text("건너뛰기",fontSize=12.sp)}
                    }
                } else Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    brand();Spacer(Modifier.weight(1f));IntroThemeMenu(theme,onTheme)
                    TextButton(onClick={onFinish(false)},modifier=Modifier.heightIn(min=48.dp)){Text("건너뛰기",fontSize=12.sp)}
                }
                AnimatedContent(step,modifier=Modifier.weight(1f).fillMaxWidth(),transitionSpec={fadeIn(tween(300)) togetherWith fadeOut(tween(140))},label="제품 소개 장면") { page ->
                    if(wide) {
                        Row(Modifier.fillMaxSize(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(40.dp)) {
                            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(22.dp)) {IntroCopy(page,true);IntroFacts(page)}
                            IntroScene(page,Modifier.weight(1.05f).fillMaxHeight())
                        }
                    } else {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top=8.dp,bottom=16.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                            IntroScene(page,Modifier.fillMaxWidth().height(if(page==2&&!largeText)180.dp else sceneHeight))
                            IntroCopy(page,false);IntroFacts(page)
                        }
                    }
                }
                Column(Modifier.widthIn(max=760.dp).fillMaxWidth().align(Alignment.CenterHorizontally),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    if(step==2) Text("수동 기록 데모 · 서버 미연결",color=c.onSurfaceVariant,fontSize=11.sp,modifier=Modifier.fillMaxWidth(),textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
                        listOf("기록하기","다시 보기","시작하기").forEachIndexed { index,label ->
                            TextButton(onClick={onStep(index)},modifier=Modifier.weight(1f).heightIn(min=48.dp).semantics{selected=step==index;contentDescription="${index+1} / 3 · $label"}) {
                                Box(Modifier.size(if(step==index)7.dp else 5.dp).background(if(step==index)c.primary else c.outline,CircleShape))
                                Spacer(Modifier.width(6.dp));Text(if(largeText)"${index+1}" else label,color=if(step==index)c.primary else c.onSurfaceVariant,fontSize=12.sp,fontWeight=if(step==index)FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        if(step>0) OutlinedButton(onClick={onStep(step-1)},modifier=Modifier.heightIn(min=56.dp),shape=RoundedCornerShape(18.dp)){Text("이전")}
                        Button(onClick={if(step<2)onStep(step+1) else onFinish(false)},modifier=Modifier.weight(1f).heightIn(min=56.dp),shape=RoundedCornerShape(18.dp)) {
                            Text(if(step<2)"다음" else "10초 체험으로 시작",fontSize=16.sp,fontWeight=FontWeight.SemiBold)
                        }
                    }
                    if(step==2) TextButton(onClick={onFinish(true)},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("간편가입으로 시작")}
                }
            }
        }
    }
}

@Composable private fun IntroThemeMenu(theme:String,onTheme:(String)->Unit) {
    var expanded by remember {mutableStateOf(false)}
    Box {
        TextButton(onClick={expanded=true},modifier=Modifier.heightIn(min=48.dp)){Text(when(theme){"dark"->"다크";"light"->"라이트";else->"자동 테마"},fontSize=12.sp)}
        DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) {
            listOf("system" to "기기 설정 따르기","light" to "라이트","dark" to "다크").forEach {(value,label)->
                DropdownMenuItem(text={Text(label)},onClick={onTheme(value);expanded=false},modifier=Modifier.semantics{selected=theme==value})
            }
        }
    }
}

@Composable private fun IntroCopy(step:Int,wide:Boolean) {
    val c=MaterialTheme.colorScheme
    val headings=listOf("갑작스러운 가속,\n기록으로 확인하세요.","필요한 순간을,\n쉽게 찾아보세요.","내 기록으로,\n차분히 확인하세요.")
    val descriptions=listOf("급발진이 의심되는 상황의 속도와 브레이크 입력을\n남기고 다시 확인하는 앱이에요.","지난 기록에서 원하는 순간을 골라\n속도와 브레이크를 나란히 확인하세요.","갑작스러운 상황이 생겼을 때,\n기록한 사실부터 차근차근 살펴보세요.")
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(listOf("급발진 의심 상황 기록","시간을 따라 다시 보는 주행","이제, 페달로그와 함께")[step],fontSize=12.sp,fontWeight=FontWeight.SemiBold,color=c.primary,letterSpacing=.4.sp)
        Text(headings[step],fontSize=if(wide)38.sp else 29.sp,lineHeight=if(wide)50.sp else 39.sp,fontWeight=FontWeight.Bold,letterSpacing=(-1).sp,color=c.onSurface)
        Text(descriptions[step],fontSize=if(wide)16.sp else 14.sp,lineHeight=24.sp,color=c.onSurfaceVariant)
    }
}

@Composable private fun IntroFacts(step:Int) {
    val c=MaterialTheme.colorScheme
    Surface(shape=RoundedCornerShape(20.dp),color=c.surface,modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            val pairs=when(step){
                0->listOf("01" to "속도와 브레이크를 하나의 기록에","02" to "신호가 없던 구간도 구분해서")
                1->listOf("01" to "처음·가장 빠를 때·마지막으로 이동","02" to "기록 원본은 파일로 내보내기")
                else->listOf("01" to "차량 없이도 10초 기록 체험","02" to "카카오·네이버 가입 데모 또는 둘러보기")
            }
            pairs.forEach {(n,text)->Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){Text(n,color=c.primary,fontSize=12.sp,fontWeight=FontWeight.Bold);Text(text,fontSize=13.sp,color=c.onSurface)}}
            if(step==2) Text("지금은 수동 기록 데모예요. 시동 자동 기록·서버 동기화는 준비 중입니다.",fontSize=12.sp,lineHeight=19.sp,color=c.onSurfaceVariant)
        }
    }
}

@Composable internal fun FoxPortrait(modifier:Modifier=Modifier,motion:Boolean=true) {
    val animate=motion&&ValueAnimator.areAnimatorsEnabled()
    val floating=if(animate) {
        val transition=rememberInfiniteTransition(label="캐릭터의 가벼운 움직임")
        transition.animateFloat(0f,1f,infiniteRepeatable(tween(2800,easing=FastOutSlowInEasing),RepeatMode.Reverse),label="캐릭터 위치").value
    } else 0.5f
    FoxMascot(MascotPose.WELCOME,modifier=modifier.graphicsLayer{translationY=(floating-.5f)*10.dp.toPx();rotationZ=(floating-.5f)*1.2f})
}

@Composable private fun IntroScene(step:Int,modifier:Modifier) {
    val c=MaterialTheme.colorScheme
    BoxWithConstraints(modifier.clip(RoundedCornerShape(32.dp)).background(Brush.radialGradient(listOf(c.primaryContainer,c.background))),contentAlignment=Alignment.Center) {
        val short=maxHeight<250.dp
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(c.primary.copy(alpha=.05f),size.minDimension*.43f,Offset(size.width*.55f,size.height*.43f))
            drawCircle(c.tertiary.copy(alpha=.07f),size.minDimension*.18f,Offset(size.width*.15f,size.height*.25f))
        }
        FoxMascot(when(step){0->MascotPose.RECORDING;1->MascotPose.HISTORY;else->MascotPose.ACCOUNT},Modifier.fillMaxWidth(.82f).fillMaxHeight(.9f).align(Alignment.TopCenter))
        Surface(color=c.surface.copy(alpha=.97f),shape=RoundedCornerShape(20.dp),shadowElevation=4.dp,modifier=Modifier.align(Alignment.BottomCenter).padding(horizontal=12.dp,vertical=8.dp).widthIn(max=340.dp).fillMaxWidth()) {
            Column(Modifier.padding(horizontal=16.dp,vertical=if(short)10.dp else 14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("기능 미리보기 · 예시 데이터",fontSize=10.sp,color=c.onSurfaceVariant)
                when(step) {
                    0->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                        Column {Text("속도",fontSize=11.sp,color=c.onSurfaceVariant);Text("42.0 km/h",fontSize=22.sp,fontWeight=FontWeight.Bold)}
                        Surface(shape=RoundedCornerShape(12.dp),color=c.tertiary.copy(alpha=.12f)){Text("브레이크 입력",fontSize=12.sp,fontWeight=FontWeight.SemiBold,color=c.tertiary,modifier=Modifier.padding(12.dp))}
                    }
                    1->{
                        Text("시속 42.0 km",fontSize=22.sp,fontWeight=FontWeight.Bold)
                        Text("브레이크 입력 있음",fontSize=14.sp,color=c.primary)
                        Text("원하는 순간을 눌러 확인하세요",fontSize=11.sp,color=c.onSurfaceVariant)
                    }
                    else->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Surface(shape=RoundedCornerShape(12.dp),color=c.primaryContainer){Text("기록",color=c.primary,fontSize=12.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(12.dp))}
                        Column {Text("필요한 순간, 다시 열어보기",fontSize=14.sp,fontWeight=FontWeight.SemiBold);Text("시간 선택 · 기록 내보내기",fontSize=11.sp,color=c.onSurfaceVariant)}
                    }
                }
            }
        }
    }
}
