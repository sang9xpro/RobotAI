package com.robotai.domain

import kotlin.math.*

/** python_speech_features defaults: rectangular 25 ms window, 10 ms stride, 26 mel filters, 13 cepstra. */
object TalkNetMfcc {
    fun compute(samples: FloatArray): FloatArray {
        require(samples.size >= 16000)
        val signal = DoubleArray(16160) { i -> if(i < samples.size) samples[i].toDouble()*32768 else 0.0 }
        val emphasized = DoubleArray(signal.size) { i -> signal[i] - if(i>0) .97*signal[i-1] else 0.0 }
        fun mel(hz: Double) = 2595*log10(1+hz/700)
        fun hz(m: Double) = 700*(10.0.pow(m/2595)-1)
        val bins = IntArray(28) { floor(513*hz(it*mel(8000.0)/27)/16000).toInt() }
        return FloatArray(1300).also { result ->
            repeat(100) { frame ->
                val re=DoubleArray(512) { i -> if(i<400 && frame*160+i<emphasized.size) emphasized[frame*160+i] else 0.0 }
                val im=DoubleArray(512)
                fft(re,im)
                val power=DoubleArray(257) { (re[it]*re[it]+im[it]*im[it])/512 }
                val energy=max(power.sum(), Math.ulp(1.0))
                val filters=DoubleArray(26) { band ->
                    var value=0.0
                    for(i in bins[band] until bins[band+1]) value += power[i]*(i-bins[band]).toDouble()/max(1,bins[band+1]-bins[band])
                    for(i in bins[band+1] until bins[band+2]) value += power[i]*(bins[band+2]-i).toDouble()/max(1,bins[band+2]-bins[band+1])
                    ln(max(value,Math.ulp(1.0)))
                }
                repeat(13) { cep ->
                    val value=filters.indices.sumOf { filters[it]*cos(PI*cep*(2*it+1)/52) } *
                        (if(cep==0) sqrt(1.0/26) else sqrt(2.0/26)) * (1+11*sin(PI*cep/22))
                    result[frame*13+cep]=(if(cep==0) ln(energy) else value).toFloat()
                }
            }
        }
    }
    private fun fft(re: DoubleArray, im: DoubleArray) {
        var j=0
        for(i in 1 until re.size) {
            var bit=re.size shr 1
            while(j and bit != 0) { j=j xor bit; bit=bit shr 1 }
            j=j xor bit
            if(i<j) { val v=re[i];re[i]=re[j];re[j]=v }
        }
        var size=2
        while(size<=re.size) {
            val angle=-2*PI/size
            for(start in re.indices.step(size)) for(k in 0 until size/2) {
                val a=start+k;val b=a+size/2
                val wr=cos(angle*k);val wi=sin(angle*k)
                val r=wr*re[b]-wi*im[b];val t=wr*im[b]+wi*re[b]
                re[b]=re[a]-r;im[b]=im[a]-t;re[a]+=r;im[a]+=t
            }
            size=size shl 1
        }
    }
}
