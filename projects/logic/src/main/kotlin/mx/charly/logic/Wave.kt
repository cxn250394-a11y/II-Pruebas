package mx.charly.logic

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class WaveFormat(val rate:Int,val channels:Int,val bits:Int,val dataBytes:Long){ val frameBytes get()=channels*bits/8 }
object Wave {
    private fun read(i:InputStream,n:Int):ByteArray {val b=ByteArray(n);var k=0;while(k<n){val r=i.read(b,k,n-k);require(r>0){"WAV truncado"};k+=r};return b}
    private fun uint(b:ByteArray,offset:Int)=ByteBuffer.wrap(b,offset,4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffffffffL
    private fun short(b:ByteArray,offset:Int)=ByteBuffer.wrap(b,offset,2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 65535
    private fun skip(i:InputStream,n:Long){var left=n;while(left>0){val got=i.skip(left);if(got>0)left-=got else {require(i.read()!=-1){"WAV truncado"};left--}}}
    /** Leaves stream at PCM data. Only plain PCM16/24/32 WAV; rejects float and compressed files. */
    fun header(i:InputStream):WaveFormat{
        val first=read(i,12);require(String(first,0,4,Charsets.US_ASCII)=="RIFF"&&String(first,8,4,Charsets.US_ASCII)=="WAVE"){"Solo WAV RIFF PCM"}
        var rate=0;var channels=0;var bits=0;var format=0;var budget=0L
        while(budget<8_000_000){val c=read(i,8);val name=String(c,0,4,Charsets.US_ASCII);val length=uint(c,4)
            if(name=="data"){require(format==1&&channels in 1..2&&bits in listOf(16,24,32)&&rate in 8000..384000){"Formato WAV no compatible"};require(length%(channels*bits/8)==0L){"Datos PCM desalineados"};return WaveFormat(rate,channels,bits,length)}
            require(length<=8_000_000-budget){"Cabecera WAV demasiado grande"}
            if(name=="fmt "){require(length>=16);val f=read(i,16);format=short(f,0);channels=short(f,2);rate=uint(f,4).toInt();bits=short(f,14);require(short(f,12)==channels*bits/8){"Alineación inválida"};skip(i,length-16)}else skip(i,length)
            if(length%2==1L)skip(i,1);budget+=length+8
        };error("No se encontró audio PCM")
    }
}
