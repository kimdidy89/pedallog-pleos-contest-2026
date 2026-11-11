package dev.pedalprobe

import android.animation.ValueAnimator
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** 콜드 실행 오프닝과 홈에서 같은 캐릭터·색·제품 목적을 일관되게 보여준다. */
@Composable internal fun FoxOpening(ready:Boolean,onDone:()->Unit) {
    val c=MaterialTheme.colorScheme
    val motion=ValueAnimator.areAnimatorsEnabled()
    var entered by remember{mutableStateOf(!motion)}
    val reveal by animateFloatAsState(if(entered)1f else 0f,tween(600),label="오프닝 등장")
    LaunchedEffect(ready){
        if(ready) {
            // Android 기본 시작 화면이 사라지고 실제 앱 프레임을 그린 뒤 시간을 센다.
            withFrameNanos{};withFrameNanos{}
            entered=true;delay(if(motion)3000 else 600);onDone()
        }
    }
    Surface(Modifier.fillMaxSize(),color=c.background) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val short=maxHeight<500.dp
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=28.dp,vertical=20.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                FoxPortrait(Modifier.size(if(short)150.dp else 240.dp).graphicsLayer{alpha=reveal;scaleX=.94f+.06f*reveal;scaleY=scaleX},motion=false)
                Text("페달로그",fontSize=38.sp,fontWeight=FontWeight.Bold,color=c.onSurface,letterSpacing=(-1).sp)
                Spacer(Modifier.height(12.dp))
                Text("급발진 의심 상황 기록 앱",fontSize=19.sp,fontWeight=FontWeight.SemiBold,color=c.primary,textAlign=TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Text("갑작스러운 가속,\n속도와 브레이크 기록으로 다시 확인하세요.",fontSize=15.sp,lineHeight=24.sp,color=c.onSurfaceVariant,textAlign=TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                TextButton(onClick=onDone,modifier=Modifier.heightIn(min=48.dp)){Text("바로 시작")}
            }
        }
    }
}

@Composable internal fun FoxHomeHero(active:Boolean,onHistory:()->Unit,onGuide:()->Unit) {
    val pose=if(active)MascotPose.RECORDING else MascotPose.WELCOME
    val c=MaterialTheme.colorScheme
    val large=LocalDensity.current.fontScale>1.3f
    Surface(shape=RoundedCornerShape(30.dp),color=c.primaryContainer,modifier=Modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.background(Brush.linearGradient(listOf(c.primaryContainer,c.surface))).padding(if(large)20.dp else 24.dp)) {
            val wide=maxWidth>=620.dp
            val copy:@Composable ()->Unit={
                Text("급발진 의심 상황 기록",fontSize=13.sp,fontWeight=FontWeight.Bold,color=c.primary)
                Text("갑작스러운 가속,\n그 순간을 기록하세요.",fontSize=if(wide)32.sp else 25.sp,lineHeight=if(wide)43.sp else 35.sp,fontWeight=FontWeight.Bold,color=c.onSurface,letterSpacing=(-.8).sp)
                Text("속도와 브레이크 입력을 남기고,\n필요한 순간을 쉽게 다시 확인해요.",fontSize=14.sp,lineHeight=23.sp,color=c.onSurfaceVariant)
                if(active)Text("지금 속도와 브레이크를 기록 중이에요",color=c.primary,fontWeight=FontWeight.SemiBold)
            }
            Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
                if(large) {
                    FoxMascot(pose,Modifier.size(140.dp).align(Alignment.CenterHorizontally))
                    Column(verticalArrangement=Arrangement.spacedBy(10.dp)){copy()}
                } else if(wide) Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp)){copy()}
                    FoxMascot(pose,Modifier.size(220.dp))
                } else Column(verticalArrangement=Arrangement.spacedBy(10.dp)){copy()}
                if(!wide&&!large) Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                    FoxMascot(pose,Modifier.size(126.dp))
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Button(onClick=onHistory,modifier=Modifier.fillMaxWidth().heightIn(min=54.dp),shape=RoundedCornerShape(16.dp)){Text("지난 기록 보기",fontWeight=FontWeight.SemiBold)}
                        OutlinedButton(onClick=onGuide,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),shape=RoundedCornerShape(16.dp)){Text("사용법")}
                    }
                } else if(large) Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Button(onClick=onHistory,modifier=Modifier.fillMaxWidth().heightIn(min=54.dp)){Text("지난 기록 보기")}
                    OutlinedButton(onClick=onGuide,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("사용법")}
                } else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Button(onClick=onHistory,modifier=Modifier.weight(1f).heightIn(min=54.dp),shape=RoundedCornerShape(16.dp)){Text("지난 기록 보기",fontWeight=FontWeight.SemiBold)}
                    OutlinedButton(onClick=onGuide,modifier=Modifier.heightIn(min=54.dp),shape=RoundedCornerShape(16.dp)){Text("사용법")}
                }
                Text("지금은 수동 기록 데모예요.\n자동 기록과 서버 보관은 준비 중이에요.",fontSize=12.sp,lineHeight=20.sp,color=c.onSurfaceVariant)
            }
        }
    }
}
