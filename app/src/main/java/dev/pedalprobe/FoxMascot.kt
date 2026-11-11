package dev.pedalprobe

import android.content.res.AssetManager
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal enum class MascotPose(val assetPath:String,val label:String) {
    WELCOME("intro/fox-guide.png","환영"),
    RECORDING("mascot/fox-recording.png","기록 중"),
    HISTORY("mascot/fox-history.png","기록 확인"),
    EMPTY("mascot/fox-empty.png","빈 기록"),
    ACCOUNT("mascot/fox-account.png","계정 안내"),
    SETTINGS("mascot/fox-settings.png","설정 안내"),
    UNKNOWN("mascot/fox-unknown.png","정보 확인 필요")
}

/** 페이지를 오갈 때마다 PNG를 다시 해독하지 않도록 최대 24MiB만 공유한다. */
private object MascotImages {
    private val cache=object:LruCache<String,ImageBitmap>(24*1024*1024) {
        override fun sizeOf(key:String,value:ImageBitmap)=value.width*value.height*4
    }
    @Synchronized fun load(assets:AssetManager,path:String):ImageBitmap {
        cache.get(path)?.let{return it}
        val image=runCatching {assets.open(path).use{requireNotNull(BitmapFactory.decodeStream(it)).asImageBitmap()}}
            .getOrElse {assets.open(MascotPose.WELCOME.assetPath).use{requireNotNull(BitmapFactory.decodeStream(it)).asImageBitmap()}}
        cache.put(path,image)
        return image
    }
}

@Composable internal fun FoxMascot(pose:MascotPose,modifier:Modifier=Modifier) {
    val assets=LocalContext.current.assets
    val bitmap=remember(assets,pose){MascotImages.load(assets,pose.assetPath)}
    // 같은 내용의 제목·상태 설명을 별도로 제공하므로 장식 그림을 중복 낭독하지 않는다.
    Image(bitmap,contentDescription=null,contentScale=ContentScale.Fit,modifier=modifier)
}

@Composable internal fun MascotHeading(pose:MascotPose,title:String,body:String) {
    val c=MaterialTheme.colorScheme
    val large=LocalDensity.current.fontScale>1.3f
    if(large) Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        FoxMascot(pose,Modifier.size(100.dp))
        Text(title,fontSize=26.sp,fontWeight=FontWeight.Bold)
        Text(body,fontSize=15.sp,color=c.onSurfaceVariant,lineHeight=24.sp)
    } else Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(title,fontSize=26.sp,fontWeight=FontWeight.Bold)
            Text(body,fontSize=15.sp,color=c.onSurfaceVariant,lineHeight=24.sp)
        }
        FoxMascot(pose,Modifier.size(100.dp))
    }
}

@Composable internal fun MascotMessage(pose:MascotPose,title:String,body:String) {
    val c=MaterialTheme.colorScheme
    val large=LocalDensity.current.fontScale>1.3f||LocalConfiguration.current.screenWidthDp<480
    Surface(shape=RoundedCornerShape(20.dp),color=c.surface,modifier=Modifier.fillMaxWidth()) {
        if(large) Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            FoxMascot(pose,Modifier.size(92.dp).align(Alignment.CenterHorizontally))
            Text(title,fontSize=18.sp,fontWeight=FontWeight.SemiBold)
            Text(body,fontSize=14.sp,lineHeight=23.sp,color=c.onSurfaceVariant)
        } else Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            FoxMascot(pose,Modifier.size(92.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(title,fontSize=18.sp,fontWeight=FontWeight.SemiBold)
                Text(body,fontSize=14.sp,lineHeight=23.sp,color=c.onSurfaceVariant)
            }
        }
    }
}
