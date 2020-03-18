package ibot.vectors;

import ibot.bot.utils.MathsUtils;

public class Vector2 {

	public static Vector2 X = new Vector2(1, 0);
	public static Vector2 Y = new Vector2(0, 1);

	public final double x;
	public final double y;

	public Vector2(double x, double y){
		this.x = x;
		this.y = y;
	}

	public Vector2(){
		this(0, 0);
	}

	public Vector2(Vector2 other){
		this(other.x, other.y);
	}

	public Vector2 plus(Vector2 other){
		return new Vector2(x + other.x, y + other.y);
	}

	public Vector2 minus(Vector2 other){
		return new Vector2(x - other.x, y - other.y);
	}

	public Vector2 scale(double scale){
		return new Vector2(x * scale, y * scale);
	}

	public Vector2 lerp(Vector2 other, double t){
		return this.plus(other.minus(this).scale(t));
	}

	public Vector2 multiply(Vector2 other){
		return new Vector2(x * other.x, y * other.y);
	}

	/**
	 * If magnitude is negative, we will return a vector facing the opposite
	 * direction.
	 */
	public Vector2 scaleToMagnitude(double magnitude){
		if(isZero()){
			// throw new IllegalStateException("Cannot scale up a vector with length
			// zero!");
			return new Vector2(0, 0);
		}
		double scaleRequired = magnitude / magnitude();
		return scale(scaleRequired);
	}

	public double distance(Vector2 other){
		double xDiff = x - other.x;
		double yDiff = y - other.y;
		return Math.sqrt(xDiff * xDiff + yDiff * yDiff);
	}

	/**
	 * This is the length of the vector.
	 */
	public double magnitude(){
		return Math.sqrt(magnitudeSquared());
	}

	public double magnitudeSquared(){
		return x * x + y * y;
	}

	public Vector2 normalised(){
		if(isZero()){
			// throw new IllegalStateException("Cannot normalize a vector with length
			// zero!");
			return new Vector2(0, 0);
		}
		return scale(1 / magnitude());
	}

	public double dot(Vector2 other){
		return x * other.x + y * other.y;
	}

	public boolean isZero(){
		return x == 0 && y == 0;
	}

	/**
	 * The correction angle is how many radians you need to rotate this vector to
	 * make it line up with the "ideal" vector. This is very useful for deciding
	 * which direction to steer.
	 */
	public double correctionAngle(Vector2 ideal){
		double currentRad = Math.atan2(y, x);
		double idealRad = Math.atan2(ideal.y, ideal.x);
		return MathsUtils.correctAngle(idealRad - currentRad);
	}

	/**
	 * Will always return a positive value <= Math.PI
	 */
	public static double angle(Vector2 a, Vector2 b){
		return Math.abs(a.correctionAngle(b));
	}

	public double angle(Vector2 other){
		return angle(this, other);
	}

	public Vector2 reciprocal(){
		return new Vector2(1 / this.x, 1 / this.y);
	}

	@Override
	public String toString(){
		if(this.magnitude() <= 1){
			return "(" + MathsUtils.round(x, 2) + ", " + MathsUtils.round(y, 2) + ")";
		}else{
			return "(" + (int)x + ", " + (int)y + ")";
		}
	}

	public Vector2 withX(double x){
		return new Vector2(x, y);
	}

	public Vector2 withY(double y){
		return new Vector2(x, y);
	}

	public Vector3 withZ(double z){
		return new Vector3(x, y, z);
	}

	public Vector2 cross(boolean left){
		if(left){
			return new Vector2(-this.y, this.x);
		}else{
			return new Vector2(this.y, -this.x);
		}
	}

	public Vector2 cross(){
		return this.cross(false);
	}

	public Vector2 rotate(double angle){
		return new Vector2(this.x * Math.cos(angle) - this.y * Math.sin(angle),
				this.x * Math.sin(angle) + this.y * Math.cos(angle));
	}

	public Vector3 withAngleZ(double radians){
		double magnitude = this.magnitude();
		double z = (Math.tan(radians) * magnitude);
		// System.out.println("vec3 with mag=" + (int)magnitude + "uu at incline=" +
		// (int)Math.toDegrees(radians) + "deg makes z=" + (int)z + "uu");
		return this.withZ(z);
	}

}
