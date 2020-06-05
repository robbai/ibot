package ibot.bot.utils.maths;

import ibot.bot.utils.StaticClass;

/**
 * Icecore's atan2
 * http://www.java-gaming.org/topics/extremely-fast-atan2/36467/msg/346145/view.html#msg346145
 */
public class Atan2 extends StaticClass {

	private static final int Size_Ac = 100000;
	private static final int Size_Ar = Size_Ac + 1;
	private static final float Pi = (float)Math.PI;
	private static final float Pi_H = Pi / 2;

	private static final float Atan2[] = new float[Size_Ar];
	private static final float Atan2_PM[] = new float[Size_Ar];
	private static final float Atan2_MP[] = new float[Size_Ar];
	private static final float Atan2_MM[] = new float[Size_Ar];

	private static final float Atan2_R[] = new float[Size_Ar];
	private static final float Atan2_RPM[] = new float[Size_Ar];
	private static final float Atan2_RMP[] = new float[Size_Ar];
	private static final float Atan2_RMM[] = new float[Size_Ar];

	static{
		for(int i = 0; i <= Size_Ac; i++){
			double d = (double)i / Size_Ac;
			double x = 1;
			double y = x * d;
			float v = (float)Math.atan2(y, x);
			Atan2[i] = v;
			Atan2_PM[i] = Pi - v;
			Atan2_MP[i] = -v;
			Atan2_MM[i] = -Pi + v;

			Atan2_R[i] = Pi_H - v;
			Atan2_RPM[i] = Pi_H + v;
			Atan2_RMP[i] = -Pi_H + v;
			Atan2_RMM[i] = -Pi_H - v;
		}
	}

	public static final float atan2(float y, float x){
		if(y < 0){
			if(x < 0){
				// (y < x) because == (-y > -x)
				if(y < x){
					return Atan2_RMM[(int)(x / y * Size_Ac)];
				}else{
					return Atan2_MM[(int)(y / x * Size_Ac)];
				}
			}else{
				y = -y;
				if(y > x){
					return Atan2_RMP[(int)(x / y * Size_Ac)];
				}else{
					return Atan2_MP[(int)(y / x * Size_Ac)];
				}
			}
		}else{
			if(x < 0){
				x = -x;
				if(y > x){
					return Atan2_RPM[(int)(x / y * Size_Ac)];
				}else{
					return Atan2_PM[(int)(y / x * Size_Ac)];
				}
			}else{
				if(y > x){
					return Atan2_R[(int)(x / y * Size_Ac)];
				}else{
					return Atan2[(int)(y / x * Size_Ac)];
				}
			}
		}
	}

	public static final double atan2(double y, double x){
		return (double)atan2((float)y, (float)x);
	}

}
