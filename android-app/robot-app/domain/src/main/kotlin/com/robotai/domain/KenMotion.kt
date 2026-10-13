package com.robotai.domain

import kotlin.math.*

enum class KenReaction { FINGER_HEART, TWO_HAND_HEART, WAVE, THUMBS_UP, VICTORY, FIST_BUMP,
    HIGH_FIVE, THUMBS_DOWN, POINT_UP, I_LOVE_YOU, OK_SIGN, ROCK_ON, TOUCH }

data class KenMotionInput(
    val phase: Phase = Phase.IDLE, val mood: Mood = Mood.CALM, val gaze: Float = 0f,
    val musicPlaying: Boolean = false, val speechLevel: Float = 0f,
    val reaction: KenReaction? = null, val reactionAtMs: Long = -1,
    val reducedMotion: Boolean = false, val energy: Float = .65f,
    val visualMood: Boolean = false
)

/** Coordinates match the approved 430 x 300 reference, independent of screen shape. */
data class KenPose(
    val open: Float, val happy: Float, val heart: Float, val blush: Float, val tilt: Float,
    val eyeX: Float, val faceX: Float, val y: Float, val mouth: Float, val smile: Float,
    val asymmetry: Float, val brightness: Float, val red: Float, val green: Float, val blue: Float,
    val blink: Float, val time: Float, val phase: Phase, val musicPlaying: Boolean,
    val reaction: KenReaction?, val reactionAtMs: Long, val reactionAge: Float, val reactionWeight: Float,
    val reducedMotion: Boolean
)

private fun Float.unit() = if (isFinite()) coerceIn(0f, 1f) else 0f
private fun smooth(value: Float): Float = value.unit().let { it * it * (3 - 2 * it) }
private fun mix(a: Float, b: Float, weight: Float) = a + (b - a) * weight

object KenMotionTiming {
    const val REACTION_MS = 2800L
    const val MUSIC_SECONDS = 1.2f
    fun reactionWeight(age: Float) = smooth((age - .12f) / .24f) * (1 - smooth((age - 1.9f) / .9f))
    fun blink(time: Float): Float {
        val cycle = time % 13.7f
        for (start in floatArrayOf(3.3f, 7.8f, 8.16f, 12.5f)) {
            val age = cycle - start
            if (age in 0f.. .23f) return 1 - .96f * sin(PI.toFloat() * age / .23f)
        }
        return 1f
    }
}

/** Frame-rate independent critically damped springs. A new target never resets velocity. */
class KenMotionEngine {
    private val value = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, .45f, 0f, 1f, 121f, 233f, 213f)
    private val velocity = FloatArray(value.size)
    private var initialized = false
    private var phase = Phase.IDLE
    private var time = 0f
    private var phaseAt = 0f
    private var rejectedReactionAt = -1L
    private var seenReactionAt = -1L
    private var seenReaction: KenReaction? = null
    private var reactionElapsed = -1f

    fun frame(input: KenMotionInput, nowMs: Long, deltaSeconds: Float): KenPose {
        val dt = if (deltaSeconds.isFinite()) deltaSeconds.coerceIn(0f, .05f) else 0f
        time += dt
        if (phase != input.phase) { phase = input.phase; phaseAt = time }
        val blocked = phase == Phase.SLEEPING || phase == Phase.ERROR
        if (blocked) {
            val rejectAt=if(input.reaction != null)input.reactionAtMs else seenReactionAt
            if(rejectAt>=0L)rejectedReactionAt=rejectAt
        }
        val wallAge = if (input.reactionAtMs >= 0) (nowMs - input.reactionAtMs) / 1000f else -1f
        if (seenReactionAt != input.reactionAtMs || seenReaction != input.reaction) {
            seenReactionAt=input.reactionAtMs;seenReaction=input.reaction;reactionElapsed=wallAge
        } else if(input.reaction!=null) reactionElapsed=max(reactionElapsed+dt,wallAge)
        val reactionAge = reactionElapsed
        val reaction = input.reaction?.takeIf { !blocked && input.reactionAtMs != rejectedReactionAt &&
            reactionAge >= 0f && reactionAge < KenMotionTiming.REACTION_MS / 1000f }
        val weight = if (reaction != null) KenMotionTiming.reactionWeight(reactionAge) else 0f
        val target = target(input, reaction, reactionAge, weight)
        if (!initialized) { target.copyInto(value); initialized = true }
        else for (i in value.indices) {
            val omega = when (i) { 6 -> 8f; 2, 5, 8 -> 18f; else -> 11f }
            val displacement = value[i] - target[i]
            val common = velocity[i] + omega * displacement
            val decay = exp(-omega * dt)
            value[i] = target[i] + (displacement + common * dt) * decay
            velocity[i] = (velocity[i] - omega * common * dt) * decay
        }
        return KenPose(value[0],value[1],value[2],value[3],value[4],value[5],value[6],value[7],
            value[8],value[9],value[10],value[11],value[12],value[13],value[14],
            if (input.reducedMotion || phase == Phase.SLEEPING) 1f else KenMotionTiming.blink(time),
            time, phase, input.musicPlaying && !blocked, reaction, if(reaction!=null)input.reactionAtMs else -1L,
            reactionAge, weight, input.reducedMotion)
    }

    private fun target(input: KenMotionInput, reaction: KenReaction?, age: Float, weight: Float): FloatArray {
        val e = input.energy.unit(); val reduced = input.reducedMotion
        val gaze = if (input.gaze.isFinite()) input.gaze.coerceIn(-1f,1f) else 0f
        val p = floatArrayOf(1f,0f,0f,0f,gaze*.02f,gaze*25,gaze*8,0f,0f,.45f,0f,1f,121f,233f,213f)
        when (input.mood) {
            Mood.HAPPY -> { p[1]=.9f;p[3]=.28f;p[9]=1f;p[12]=168f;p[13]=239f;p[14]=199f }
            Mood.CURIOUS -> { p[10]=.17f;p[4]=-.045f;p[0]=1.06f;p[9]=.28f }
            Mood.SLEEPY -> { p[0]=.43f;p[9]=.2f;p[11]=.78f }
            Mood.CARING -> { p[0]=.84f;p[3]=.3f;p[9]=.7f;p[4]=.04f }
            else -> Unit
        }
        when (phase) {
            Phase.LISTENING -> {
                if (!input.visualMood) { p[0]=1.1f;p[1]*=.4f;p[4]=-.025f;p[9]=.55f }
                p[7]=-2f
            }
            Phase.THINKING -> { p[5]+=if(reduced) 12f else 12+sin(time*.8f)*6;p[7]=-8f;p[10]=.18f;p[9]=.16f }
            Phase.FINALIZING -> if(!reduced)p[7]+=sin(((time-phaseAt)/1.1f).unit()*2*PI.toFloat())*5
            Phase.SPEAKING -> { p[8]=input.speechLevel.unit()*.85f;if(!reduced)p[7]=sin(time*2.3f)*1.6f*e }
            Phase.INTERRUPTED -> { p[0]=1.13f;p[9]=.25f;p[8]=0f }
            Phase.ERROR -> { p[12]=255f;p[13]=215f;p[14]=155f;p[10]=.27f;p[4]=.09f;p[9]=-.25f;p[0]=.85f;p[1]=0f }
            else -> Unit
        }
        if(input.musicPlaying && phase==Phase.IDLE && !reduced && reaction==null) {
            p[4]+=sin(time*2*PI.toFloat()/KenMotionTiming.MUSIC_SECONDS)*.055f*e
            p[7]+=cos(time*2*PI.toFloat()/KenMotionTiming.MUSIC_SECONDS)*3*e;p[1]=max(p[1],.45f);p[9]=.9f
        }
        if(reaction!=null) {
            p[3]=max(p[3],.7f*weight);p[1]=max(p[1],.9f*weight);p[9]=max(p[9],weight)
            when(reaction) {
                KenReaction.FINGER_HEART,KenReaction.TWO_HAND_HEART,KenReaction.I_LOVE_YOU -> {
                    p[2]=weight;p[1]*=1-weight;p[12]=mix(p[12],242f,weight);p[13]=mix(p[13],184f,weight);p[14]=mix(p[14],189f,weight)
                }
                KenReaction.THUMBS_DOWN -> {p[1]=0f;p[10]=.24f*weight;p[9]=-.25f*weight;p[4]=-.07f*weight;p[0]=.8f}
                KenReaction.POINT_UP -> {p[1]=.15f;p[0]=1.17f;p[10]=.14f;p[7]=-7*weight}
                else -> Unit
            }
            if(!reduced) {
                val anticipation=if(age<.16f)sin(age/.16f*PI.toFloat()) else 0f
                p[0]-=.1f*anticipation*e;p[7]+=3*anticipation*e
                val bounce=sin(age*11)*exp(-age*2)*weight*e
                when(reaction) {
                    KenReaction.WAVE -> p[4]+=sin(age*8)*.12f*weight*e
                    KenReaction.THUMBS_UP,KenReaction.OK_SIGN -> p[7]+=sin(age*8)*7*weight*e
                    KenReaction.FIST_BUMP -> {p[7]+=bounce*10;p[0]+=bounce*.1f}
                    KenReaction.HIGH_FIVE,KenReaction.VICTORY -> p[7]-=sin(min(age/.7f,1f)*PI.toFloat())*14*e
                    KenReaction.ROCK_ON -> p[4]+=sin(age*13)*.1f*weight*e
                    KenReaction.TOUCH -> {p[0]-=.2f*weight;p[4]+=.09f*weight;p[7]+=bounce*7}
                    KenReaction.TWO_HAND_HEART -> {p[0]*=1-.12f*weight;p[7]+=weight*3}
                    else -> Unit
                }
            }
        }
        if(phase==Phase.SLEEPING) {
            p[0]=.035f;p[1]=0f;p[2]=0f;p[3]=0f;p[11]=.3f;p[8]=0f;p[9]=.15f;p[10]=0f;p[4]=0f;p[5]=0f;p[6]=0f
            p[7]=if(reduced)0f else sin(time*2*PI.toFloat()/5.5f)*1.4f
        } else if(!reduced)p[7]+=sin(time*2*PI.toFloat()/4.7f)*1.8f*e
        return p
    }
}

/** RMS in a 20 ms PCM window, with a silence floor; contains no microphone data. */
object PcmSpeechEnvelope {
    fun level(pcm: ShortArray, start: Int, count: Int): Float {
        require(start >= 0 && count >= 0 && start + count <= pcm.size)
        if(count==0)return 0f
        var sum=0.0
        for(i in start until start+count){val v=pcm[i]/32768.0;sum+=v*v}
        return ((sqrt(sum/count)-.006)/.12).toFloat().unit()
    }
}
