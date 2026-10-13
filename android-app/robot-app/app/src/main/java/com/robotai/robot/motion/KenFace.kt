package com.robotai.robot.motion

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.robotai.domain.*
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.*

object KenColors {
    val Background=Color(0xff050e13)
    val Mint=Color(0xff79e9d5)
    val Happy=Color(0xffa8efc7)
    val Pink=Color(0xfff2b8bd)
    val Warm=Color(0xffffd79b)
}

private val LocalKenFrame=staticCompositionLocalOf<State<KenPose>?> { null }

/** Captions can follow the same reaction lifetime and priority as the visible face. */
@Composable
fun sharedKenPose():KenPose? = LocalKenFrame.current?.value

@Composable
private fun rememberReducedMotion(): Boolean {
    val resolver=LocalContext.current.contentResolver
    fun read()=runCatching { Settings.Global.getFloat(resolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)<=0f }.getOrDefault(false)
    var reduced by remember(resolver) { mutableStateOf(read()) }
    DisposableEffect(resolver) {
        val observer=object:ContentObserver(Handler(Looper.getMainLooper())) { override fun onChange(selfChange:Boolean) { reduced=read() } }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
}

@Composable
private fun rememberKenFrame(input:KenMotionInput,speech:StateFlow<Float>?=null,active:Boolean=true):State<KenPose> {
    val engine=remember { KenMotionEngine() }
    val reduced=rememberReducedMotion()
    val latest by rememberUpdatedState(input.copy(reducedMotion=input.reducedMotion||reduced))
    val latestSpeech by rememberUpdatedState(speech)
    val state=remember { mutableStateOf(engine.frame(latest,SystemClock.elapsedRealtime(),0f)) }
    val owner=LocalLifecycleOwner.current
    var started by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(owner) {
        val observer=LifecycleEventObserver { _,_-> started=owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val clock=remember { longArrayOf(0L) }
    DisposableEffect(started,active) {
        clock[0]=0L
        onDispose { clock[0]=0L }
    }
    LaunchedEffect(started,active,input,reduced) {
        if(started && active) {
            var transitionTime=0f
            val tick:(Long)->Unit = { frame ->
                val dt=if(clock[0]!=0L)((frame-clock[0])/1_000_000_000f).coerceIn(0f,.05f) else 0f
                clock[0]=frame
                transitionTime+=dt
                state.value=engine.frame(latest.copy(speechLevel=latestSpeech?.value ?: latest.speechLevel),SystemClock.elapsedRealtime(),dt)
            }
            // Finite state transitions remain testable; idle loops obey Compose's infinite
            // animation policy so UI tests can become idle with automatic clock advancement.
            while(true) {
                if(transitionTime<.75f)withFrameNanos(tick) else withInfiniteAnimationFrameNanos(tick)
            }
        }
    }
    return state
}

/** One clock survives tab changes and is shared by face, gestures and music accents. */
@Composable
fun KenMotionHost(input:KenMotionInput,speech:StateFlow<Float>?=null,active:Boolean=true,content:@Composable ()->Unit) {
    val frame=rememberKenFrame(input,speech,active)
    CompositionLocalProvider(LocalKenFrame provides frame,content=content)
}

@Composable
fun KenMotionHostIfNeeded(input:KenMotionInput,speech:StateFlow<Float>?=null,content:@Composable ()->Unit) {
    if(LocalKenFrame.current==null)KenMotionHost(input,speech,content=content) else content()
}

@Composable
fun KenFace(input:KenMotionInput,modifier:Modifier=Modifier) {
    val frame=LocalKenFrame.current ?: rememberKenFrame(input)
    Canvas(modifier) { drawKen(frame.value) }
}

@Composable
fun KenMusicWave(modifier:Modifier=Modifier) {
    val frame=LocalKenFrame.current ?: rememberKenFrame(KenMotionInput(musicPlaying=true))
    Canvas(modifier.testTag("music-effects")) {
        val p=frame.value
        if(!p.musicPlaying)return@Canvas
        val gap=size.width/7
        for(i in 0..6) {
            val height=if(p.reducedMotion)size.height*.3f else size.height*(.2f+.65f*abs(sin(p.time*2*PI.toFloat()/KenMotionTiming.MUSIC_SECONDS+i*.8f)))
            drawLine(KenColors.Happy,Offset(gap*(i+.5f),(size.height-height)/2),Offset(gap*(i+.5f),(size.height+height)/2),gap.coerceAtMost(12f)*.3f,StrokeCap.Round)
        }
    }
}

private fun DrawScope.stroke(color:Color,width:Float,build:Path.()->Unit) = drawPath(Path().apply(build),color,style=Stroke(width,cap=StrokeCap.Round,join=StrokeJoin.Round))

private fun DrawScope.heart(x:Float,y:Float,s:Float,color:Color,alpha:Float=1f) {
    withTransform({ translate(x,y);scale(s/30,s/30,Offset.Zero) }) {
        drawPath(Path().apply {moveTo(0f,12f);cubicTo(-28f,-6f,-16f,-26f,0f,-12f);cubicTo(16f,-26f,28f,-6f,0f,12f);close()},color,alpha=alpha.coerceIn(0f,1f))
    }
}
private fun DrawScope.sparkle(x:Float,y:Float,s:Float,color:Color,alpha:Float=1f) {
    drawPath(Path().apply {moveTo(x,y-s);quadraticBezierTo(x+s*.2f,y-s*.2f,x+s,y);quadraticBezierTo(x+s*.2f,y+s*.2f,x,y+s);quadraticBezierTo(x-s*.2f,y+s*.2f,x-s,y);quadraticBezierTo(x-s*.2f,y-s*.2f,x,y-s);close()},color,alpha=alpha.coerceIn(0f,1f))
}

private fun DrawScope.eye(x:Float,side:Int,p:KenPose,color:Color) {
    val w=68f;val h=max(3f,96*p.open*p.blink*(1+side*p.asymmetry))
    val happy=p.happy.coerceIn(0f,1f);val love=p.heart.coerceIn(0f,1f)
    withTransform({translate(x,0f)}) {
        val path=Path().apply {moveTo(-w/2,0f);cubicTo(-w/2,-h*.56f,w/2,-h*.56f,w/2,0f);cubicTo(w/2,h*(.56f-happy*.88f),-w/2,h*(.56f-happy*.88f),-w/2,0f);close()}
        for(layer in 4 downTo 1)drawPath(path,color,alpha=.018f*p.brightness*(1-love),style=Stroke(layer*3f))
        drawPath(path,color,alpha=(p.brightness*(1-love)).coerceIn(0f,1f))
        if(love>.001f)heart(0f,-2f,65f,color,love*p.brightness)
        drawOval(Color(0xffe5fff5),Offset(-18f,-h*.305f),Size(10f,max(2f,h*.17f)),alpha=(.33f*(1-happy)*(1-love)*p.brightness*(p.open*p.blink*3).coerceIn(0f,1f)).coerceIn(0f,1f))
    }
}

private fun DrawScope.drawKen(p:KenPose) {
    drawRect(KenColors.Background)
    val unit=min(size.width/430,size.height/300)*1.10f
    val color=Color(p.red.roundToInt().coerceIn(0,255),p.green.roundToInt().coerceIn(0,255),p.blue.roundToInt().coerceIn(0,255))
    withTransform({translate(size.width/2,size.height*.46f);scale(unit,unit,Offset.Zero)}) {
        drawCircle(Brush.radialGradient(listOf(KenColors.Mint.copy(alpha=.035f*p.brightness),Color.Transparent),Offset.Zero,165f),165f,Offset.Zero)
        withTransform({translate(p.faceX,p.y);rotate(p.tilt*180/PI.toFloat(),Offset.Zero)}) {
            eye(-62+p.eyeX*.7f,-1,p,color);eye(62+p.eyeX*.7f,1,p,color)
            for(side in listOf(-1,1))drawOval(KenColors.Pink,Offset(side*85f-13,38.5f),Size(26f,9f),alpha=(p.blush*.45f*p.brightness).coerceIn(0f,1f))
            val mo=p.mouth.coerceIn(0f,1f);val my=67f
            if(mo>.04f) {
                drawOval(color,Offset(-9-mo*5,my+mo*3-3-mo*13),Size(18+mo*10,6+mo*26),alpha=p.brightness.coerceIn(0f,1f))
                drawOval(Color(0xff102b2b),Offset(-5-mo*4,my+mo*1.7f-max(1f,mo*9)),Size(10+mo*8,max(2f,mo*18)))
            } else stroke(color.copy(alpha=p.brightness.coerceIn(0f,1f)),4f) {moveTo(-14f,my);quadraticBezierTo(0f,my+p.smile*16,14f,my)}
        }
        when(p.phase) {
            Phase.LISTENING -> {
                val pulse=if(p.reducedMotion)1f else .6f+.4f*sin(p.time*3)
                drawCircle(KenColors.Mint,4+pulse*2,Offset(0f,100f),style=Stroke(2f));drawCircle(KenColors.Mint,1.5f,Offset(0f,100f))
            }
            Phase.THINKING,Phase.FINALIZING -> for(i in 0..2)drawCircle(color,2.8f,Offset((i-1)*13f,100f),alpha=if(p.reducedMotion).6f else .3f+.6f*(.5f+.5f*sin(p.time*5-i*.9f)))
            else -> Unit
        }
        if(p.musicPlaying)for(i in 0..6) {
            val h=if(p.reducedMotion)5f else 3+abs(sin(p.time*2*PI.toFloat()/KenMotionTiming.MUSIC_SECONDS+i*.8f))*9
            drawLine(KenColors.Happy,Offset(-24+i*8f,119-h/2),Offset(-24+i*8f,119+h/2),2f,StrokeCap.Round)
        }
        effects(p)
    }
}

private fun DrawScope.effects(p:KenPose) {
    val kind=p.reaction ?: return
    val age=p.reactionAge;val a=p.reactionWeight.coerceIn(0f,1f)
    val pink=KenColors.Pink.copy(alpha=a);val mint=KenColors.Happy.copy(alpha=a);val warm=KenColors.Warm.copy(alpha=a)
    when(kind) {
        KenReaction.FINGER_HEART,KenReaction.TWO_HAND_HEART,KenReaction.I_LOVE_YOU -> {
            val count=when(kind){KenReaction.TWO_HAND_HEART->7;KenReaction.I_LOVE_YOU->5;else->3}
            for(i in 0 until count) {
                val side=if(i%2==1)-1 else 1
                val travel=if(p.reducedMotion).4f else ((age-i*.09f)/2.6f).coerceIn(0f,1f)
                heart(side*(119+(i%3)*18f)+sin(travel*3+i)*5,32-travel*118+(i%3)*22,8+(i%3)*4f,pink,1-travel*.6f)
            }
            if(kind==KenReaction.TWO_HAND_HEART)stroke(pink,2f){moveTo(-127f,20f);quadraticBezierTo(-141f,72f,-81f,79f);moveTo(127f,20f);quadraticBezierTo(141f,72f,81f,79f)}
        }
        KenReaction.VICTORY,KenReaction.HIGH_FIVE -> for(i in 0..9) {
            val angle=i*2*PI.toFloat()/10;val travel=if(p.reducedMotion).5f else (age/2).coerceIn(0f,1f)
            sparkle(cos(angle)*(100+travel*55),sin(angle)*(75+travel*32)-15,3+(i%3).toFloat(),listOf(warm,mint,pink)[i%3],1-travel*.5f)
        }
        KenReaction.WAVE -> for(i in 0..2) {
            val r=13+i*10f;drawArc(mint,-46f,86f,false,Offset(126-r,-35-r),Size(r*2,r*2),style=Stroke(2f,cap=StrokeCap.Round))
        }
        KenReaction.THUMBS_UP,KenReaction.OK_SIGN -> {
            stroke(mint,4f){moveTo(111f,-43f);lineTo(120f,-34f);lineTo(138f,-54f)};sparkle(-128f,-38f,7f,warm)
        }
        KenReaction.POINT_UP -> {
            drawCircle(warm,12f,Offset(110f,-72f));stroke(warm,3f){moveTo(105f,-54f);lineTo(115f,-54f)}
            for(i in 0..4){val angle=PI.toFloat()+i*PI.toFloat()/4;drawLine(warm,Offset(110+cos(angle)*21,-72+sin(angle)*21),Offset(110+cos(angle)*27,-72+sin(angle)*27),2f,StrokeCap.Round)}
        }
        KenReaction.THUMBS_DOWN -> stroke(Color(0xffb4dbda).copy(alpha=a),2f){moveTo(118f,-39f);quadraticBezierTo(129f,-26f,119f,-24f);quadraticBezierTo(111f,-24f,118f,-39f)}
        KenReaction.FIST_BUMP -> {
            val s=if(p.reducedMotion)1f else 1+sin(age*8)*.1f;drawCircle(warm,12*s,Offset(0f,79f),style=Stroke(2f))
            for(i in 0..3)sparkle((i-1.5f)*19,85f,3f,mint)
        }
        KenReaction.ROCK_ON -> for(side in listOf(-1,1)) {
            val x=side*122f;val y=-29+if(p.reducedMotion)0f else sin(age*8+side)*9
            stroke(warm,3f){moveTo(x,y);lineTo(x,y-19);lineTo(x+10,y-22)};drawOval(warm,Offset(x-8,y-1),Size(10f,6f))
        }
        KenReaction.TOUCH -> {sparkle(116f,-36f,7f,warm);sparkle(-122f,3f,4f,pink)}
    }
}
