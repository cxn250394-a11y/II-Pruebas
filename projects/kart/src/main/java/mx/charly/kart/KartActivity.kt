package mx.charly.kart
import android.os.Bundle
import android.view.WindowManager
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
class KartActivity:AndroidApplication(){
 override fun onCreate(state:Bundle?){super.onCreate(state);window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);initialize(SandriftGame(),AndroidApplicationConfiguration().apply{useAccelerometer=true;useCompass=false;useGyroscope=false;useImmersiveMode=true;numSamples=0})}
}
