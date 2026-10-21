package mx.charly.ui
import java.io.InputStream
import java.io.ByteArrayOutputStream
object Streams {
 fun readLimited(input:InputStream,limit:Int):ByteArray {val out=ByteArrayOutputStream();val b=ByteArray(8192);while(true){val n=input.read(b);if(n<0)break;if(n==0)continue;require(out.size()+n<=limit){"Archivo o respuesta demasiado grande"};out.write(b,0,n)};return out.toByteArray()}
}
