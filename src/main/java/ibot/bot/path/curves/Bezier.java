package ibot.bot.path.curves;

import java.util.ArrayList;

import ibot.bot.path.Curve;
import ibot.vectors.Vector2;

public class Bezier extends Curve {

	private Vector2[] points;

	public Bezier(Vector2... points){
		if(points == null || points.length < 2)
			return;
		this.points = points;
	}

	public Vector2 T(double t){
		ArrayList<Vector2> p = new ArrayList<Vector2>();

		// Fixed points.
		for(Vector2 point : this.points)
			p.add(point);

		// Loop to find the final point.
		while(true){
			Vector2[] newP = new Vector2[p.size() - 1];
			for(int i = 0; i < (p.size() - 1); i++)
				newP[i] = lerp(p.get(i), p.get(i + 1), t);
			if(newP.length > 1){
				p.clear();
				for(Vector2 point : newP)
					p.add(point);
			}else{
				return newP[0];
			}
		}
	}

	private Vector2 lerp(Vector2 one, Vector2 two, double t){
		return one.plus(two.minus(one).scale(t));
	}

	@Override
	public Vector2[] discretise(int n){
		Vector2[] d = new Vector2[n];
		for(int i = 0; i < n; i++)
			d[i] = this.T((double)i / (n - 1));
		return d;
	}

	@Override
	public double getLength(){
		final int N = 100;
		final double STEP = (1D / N);

		double length = 0;
		for(double t = 0; t < t; t += STEP){
			length += this.T(t).distance(this.T(t + STEP));
		}

		return length;
	}

}
