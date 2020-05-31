package ibot.bot.utils;

import java.util.Random;

import ibot.input.Car;
import ibot.input.CarOrientation;
import ibot.vectors.Vector2;
import ibot.vectors.Vector3;

public class MathsUtils extends StaticClass {

	public static final double EPSILON = 0.000001;

	public static final Random RAND = new Random();

	public static boolean between(double a, double bound1, double bound2){
		return a >= Math.min(bound1, bound2) && a <= Math.max(bound1, bound2);
	}

	public static Vector3 local(CarOrientation orientation, Vector3 relative){
		return new Vector3(orientation.right.dot(relative), orientation.forward.dot(relative),
				orientation.up.dot(relative));
	}

	public static Vector3 local(Car car, Vector3 vector){
		Vector3 relative = vector.minus(car.position);
		return local(car.orientation, relative);
	}

	public static double lerp(double a, double b, double t){
		return a + (b - a) * t;
	}

	public static double round(double value, int digits){
		if(Double.isNaN(value))
			return value;
		double tens = Math.pow(10, digits);
		return Math.round(value * tens) / tens;
	}

	public static double round(double value){
		return round(value, 2);
	}

	public static double clamp(double value, double bound1, double bound2){
		return Math.max(Math.min(Math.max(bound1, bound2), value), Math.min(bound1, bound2));
	}

	public static double random(double a, double b){
		return lerp(a, b, RAND.nextDouble());
	}

	public static double invertAngle(double radians){
		if(radians == 0){
			return Math.PI;
		}
		return -radians + Math.copySign(Math.PI, radians);
	}

	public static double correctAngle(double angle){
		if(Math.abs(angle) > Math.PI){
			angle -= Math.copySign(Math.PI * 2, angle);
		}
		return angle;
	}

	public static Vector3 global(Car car, Vector3 local){
		return car.position.plus(car.orientation.right.scale(local.x)).plus(car.orientation.forward.scale(local.y))
				.plus(car.orientation.up.scale(local.z));
	}

	/**
	 * https://math.stackexchange.com/a/3128850
	 */
	public static Pair<Double, Double> closestPointToLineSegment(Vector2 P, Pair<Vector2, Vector2> lineSegment){
		Vector2 A = lineSegment.getOne(), B = lineSegment.getTwo();

		Vector2 v = B.minus(A);
		Vector2 u = A.minus(P);

		double vu = v.dot(u);
		double vv = v.dot(v);

		double t = -vu / vv;

		if(t >= 0 && t <= 1)
			return new Pair<Double, Double>(P.distance(A.lerp(B, t)), t);

		double distA = P.distance(A), distB = P.distance(B);
		return new Pair<Double, Double>(Math.min(distA, distB), MathsUtils.clamp(t, 0, 1));
	}

	public static double shorterAngle(double radians){
		return Math.copySign(Math.min(Math.abs(radians), Math.PI - Math.abs(radians)), radians);
	}

	public static Vector2 traceToX(Vector2 start, Vector2 direction, double targetX){
		if(direction.x * (targetX - start.x) <= 0)
			return null;
		direction = direction.normalised();
		return start.plus(direction.scale(Math.abs(targetX - start.x) / Math.abs(direction.x)));
	}

	public static Vector2 traceToY(Vector2 start, Vector2 direction, double targetY){
		if(direction.y * (targetY - start.y) <= 0)
			return null;
		direction = direction.normalised();
		return start.plus(direction.scale(Math.abs(targetY - start.y) / Math.abs(direction.y)));
	}

	public static double clampMagnitude(double value, double bound){
		return clamp(value, -bound, bound);
	}

	public static Vector2 traceToWall(Vector2 start, Vector2 direction, double targetX, double targetY){
		Vector2 x = traceToX(start, direction, targetX), y = traceToY(start, direction, targetY);
		if(x == null)
			return y;
		if(y == null)
			return x;
		return x.distance(start) < y.distance(start) ? x : y;
	}

}
