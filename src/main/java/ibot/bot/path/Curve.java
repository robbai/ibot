package ibot.bot.path;

import java.awt.Color;

import ibot.bot.input.Pencil;
import ibot.bot.utils.maths.MathsUtils;
import ibot.bot.utils.rl.Constants;
import ibot.vectors.Vector2;

public abstract class Curve {

	public abstract Vector2 T(double t);

	public abstract double getLength();

	public Vector2[] discretise(int n){
		Vector2[] d = new Vector2[n];
		for(int i = 0; i < n; i++)
			d[i] = this.T((double)i / (n - 1));
		return d;
	}

	public Vector2 S(double s){
		return this.T(MathsUtils.clamp(s / this.getLength(), 0, 1));
	}

	public void render(Pencil pencil, Color colour, int n){
		if(n < 2)
			return;
		Vector2[] points = this.discretise(n);
		for(int i = 0; i < (points.length - 1); i++){
			Vector2 a = points[i], b = points[Math.min(points.length - 1, i + 1)];
			pencil.renderer.drawLine3d(colour, a.withZ(Constants.CAR_HEIGHT), b.withZ(Constants.CAR_HEIGHT));
		}
	}

}
