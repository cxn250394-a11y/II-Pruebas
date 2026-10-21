package mx.charly.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.WindowInsets
import android.widget.*

/** Small shared UI; each product has its own private data and Android package. */
open class Screen : Activity() {
    protected val ink = Color.rgb(12,20,32)
    protected val accent = Color.rgb(95,226,187)
    protected val soft = Color.rgb(180,194,213)
    protected lateinit var root: LinearLayout
    protected lateinit var body: LinearLayout
    override fun onCreate(state: Bundle?) { super.onCreate(state); layout("II Pruebas", "Aplicación independiente · alpha") }
    fun dp(v: Int) = (resources.displayMetrics.density*v+0.5f).toInt()
    protected fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    protected fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    protected fun text(s: String, size: Float=15f, color: Int=Color.WHITE, bold: Boolean=false) = TextView(this).apply {
        text=s; textSize=size; setTextColor(color); setPadding(0,dp(5),0,dp(5))
        if(bold) typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
    }
    protected fun label(s: String, size: Float=15f, color: Int=Color.WHITE): TextView = text(s,size,color).also { body.addView(it) }
    protected fun layout(title: String, subtitle: String) {
        root=column().apply { setBackgroundColor(ink) }
        root.setOnApplyWindowInsetsListener { v,i ->
            if(Build.VERSION.SDK_INT>=30) { val b=i.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime());v.setPadding(b.left,b.top,b.right,b.bottom) }
            else { @Suppress("DEPRECATION") v.setPadding(i.systemWindowInsetLeft,i.systemWindowInsetTop,i.systemWindowInsetRight,i.systemWindowInsetBottom) }; i
        }
        val head=column().apply { setPadding(dp(20),dp(12),dp(20),dp(8)) }
        head.addView(text(title,26f,Color.WHITE,true));head.addView(text(subtitle,12f,accent));root.addView(head)
        body=column().apply{setPadding(dp(18),0,dp(18),dp(20))}
        root.addView(ScrollView(this).apply{addView(body)},LinearLayout.LayoutParams(-1,0,1f));setContentView(root);root.requestApplyInsets()
    }
    protected fun button(s: String, strong: Boolean=false, action:()->Unit): Button = Button(this).apply {
        text=s;isAllCaps=false;minHeight=dp(48);setTextColor(if(strong) ink else Color.WHITE)
        backgroundTintList=ColorStateList.valueOf(if(strong) accent else Color.rgb(30,45,65));setOnClickListener{action()}
    }.also { body.addView(it,LinearLayout.LayoutParams(-1,-2)) }
    protected fun card(title: String, description: String, action:(()->Unit)?=null) {
        val c=column().apply { setPadding(dp(16),dp(10),dp(16),dp(12));background=GradientDrawable().apply{setColor(Color.rgb(23,36,53));cornerRadius=dp(15).toFloat()} }
        c.addView(text(title,17f,Color.WHITE,true));c.addView(text(description,13f,soft))
        if(action!=null){c.isFocusable=true;c.setOnClickListener{action()}}
        body.addView(c,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(8);bottomMargin=dp(6)})
    }
    protected fun field(hintText: String, value: String="", password: Boolean=false, numeric: Boolean=false): EditText = EditText(this).apply{
        hint=hintText;setText(value);setTextColor(Color.WHITE);setHintTextColor(soft);setSingleLine(true)
        inputType=if(password) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else if(numeric) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED else InputType.TYPE_CLASS_TEXT
        if(password){isSaveEnabled=false;importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS}
    }.also{body.addView(it)}
    protected fun menu(items:List<String>, selected:Int=0, action:(Int)->Unit):Spinner = Spinner(this).apply{
        adapter=ArrayAdapter(this@Screen,android.R.layout.simple_spinner_dropdown_item,items);setSelection(selected)
        onItemSelectedListener=object:AdapterView.OnItemSelectedListener{override fun onNothingSelected(p:AdapterView<*>?){};override fun onItemSelected(p:AdapterView<*>?,v:View?,pos:Int,id:Long){action(pos)}}
    }.also{body.addView(it)}
    protected fun prompt(title:String,value:String="",action:(String)->Unit){
        val e=EditText(this).apply{setText(value);setSingleLine(true)}
        AlertDialog.Builder(this).setTitle(title).setView(e).setNegativeButton("Cancelar",null).setPositiveButton("Guardar"){_,_->action(e.text.toString())}.show()
    }
    protected fun confirm(title:String,message:String,action:()->Unit){AlertDialog.Builder(this).setTitle(title).setMessage(message).setNegativeButton("Cancelar",null).setPositiveButton("Continuar"){_,_->action()}.show()}
    protected fun info(title:String,message:String){AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("Cerrar",null).show()}
    protected fun toast(s:String){Toast.makeText(this,s,Toast.LENGTH_LONG).show()}
    protected fun website(url:String){try{startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}catch(_:Exception){toast("No hay navegador disponible")}}
}
