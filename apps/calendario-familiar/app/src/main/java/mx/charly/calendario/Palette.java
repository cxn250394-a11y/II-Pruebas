package mx.charly.calendario;

import android.graphics.Color;

public final class Palette {
    public static final String[] GROUPS = {"Pasteles","Vivos","Neón"};
    public static final String[] NAMES = {
      "Durazno suave","Cielo","Menta","Rosa té","Lavanda","Vainilla","Aguamarina","Coral suave",
      "Mandarina","Verde fresco","Azul claro","Violeta","Frambuesa","Ámbar","Turquesa","Periwinkle",
      "Limón neón","Menta neón","Rosa neón","Cian neón","Lila brillante","Naranja neón","Lima neón","Amarillo eléctrico"
    };
    public static final int[] COLORS = {
      0xFFFFDEC8,0xFFD6EBFA,0xFFD4F5DF,0xFFF8DCEB,0xFFE7DDFA,0xFFFFECC2,0xFFCFF3EC,0xFFFADBD3,
      0xFFFF9A5F,0xFF68CE91,0xFF64A8E6,0xFFA18BE3,0xFFE97A9E,0xFFE8C052,0xFF4FC8C6,0xFF889FEA,
      0xFFE4FC56,0xFF5DF8BD,0xFFFF69B4,0xFF6DDEFF,0xFFBC98FF,0xFFFFAD53,0xFF91EE6B,0xFFFFE56C
    };
    public static final int DEFAULT_DAY=0xFFF2F3F7;
    public static final int INK=0xFF253044;
    private Palette(){}
    public static int text(int background) {
        double luminance=luminance(background);
        double black=(luminance+0.05)/0.05, white=1.05/(luminance+0.05);
        return black >= white ? INK : Color.WHITE;
    }
    public static double luminance(int color) {
        double r=component(Color.red(color)/255.0);
        double g=component(Color.green(color)/255.0);
        double b=component(Color.blue(color)/255.0);
        return 0.2126*r+0.7152*g+0.0722*b;
    }
    private static double component(double v) {return v<=0.04045?v/12.92:Math.pow((v+0.055)/1.055,2.4);}
    public static boolean isAvailable(int color) {
        for(int c:COLORS)if(c==color)return true;
        return color==DEFAULT_DAY;
    }
}
