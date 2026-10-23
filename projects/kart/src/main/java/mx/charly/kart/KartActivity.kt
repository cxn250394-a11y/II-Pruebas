package mx.charly.kart
import android.os.Bundle
import android.view.WindowManager
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
class KartActivity:AndroidApplication(){
 override fun onCreate(state:Bundle?){super.onCreate(state);window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  // "autostart" is only a synthetic-test shortcut (adb am start -e autostart race); it changes no data and needs no permission.
  val auto=intent?.getStringExtra("autostart")=="race"
  initialize(SandriftGame(auto),AndroidApplicationConfiguration().apply{useAccelerometer=true;useCompass=false;useGyroscope=false;useImmersiveMode=true;numSamples=0;r=8;g=8;b=8;depth=16})}
}
