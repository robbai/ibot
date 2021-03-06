package ibot.vectors;

import rlbot.gamestate.DesiredVector3;

import com.google.flatbuffers.FlatBufferBuilder;

import ibot.bot.utils.maths.Plane;
import ibot.bot.utils.rl.Constants;

public class Vector3 extends rlbot.vector.Vector3 {

	public static Vector3 X = new Vector3(1, 0, 0);
	public static Vector3 Y = new Vector3(0, 1, 0);
	public static Vector3 Z = new Vector3(0, 0, 1);

	public Vector3(double x, double y, double z){
		super((float)x, (float)y, (float)z);
	}

	public Vector3(){
		this(0, 0, 0);
	}

	public Vector3(rlbot.flat.Vector3 vec){
		this(-vec.x(), vec.y(), vec.z());
	}

	public Vector3(double[] vec){
		this(vec[0], vec[1], vec[2]);
	}

	public Vector3(Vector3 vec){
		this(vec.x, vec.y, vec.z);
	}

	public int toFlatbuffer(FlatBufferBuilder builder){
		return rlbot.flat.Vector3.createVector3(builder, -x, y, z);
	}

	public Vector3 plus(Vector3 other){
		return new Vector3(x + other.x, y + other.y, z + other.z);
	}

	public Vector3 minus(Vector3 other){
		return new Vector3(x - other.x, y - other.y, z - other.z);
	}

	public Vector3 plus(Vector2 other){
		return new Vector3(x + other.x, y + other.y, z);
	}

	public Vector3 minus(Vector2 other){
		return new Vector3(x - other.x, y - other.y, z);
	}

	public Vector3 scale(double scale){
		return new Vector3(x * scale, y * scale, z * scale);
	}

	/**
	 * If magnitude is negative, we will return a vector facing the opposite
	 * direction.
	 */
	public Vector3 scaleToMagnitude(double magnitude){
		if(isZero()){
			// throw new IllegalStateException("Cannot scale up a vector with length
			// zero!");
			return new Vector3();
		}
		double scaleRequired = magnitude / magnitude();
		return scale(scaleRequired);
	}

	public double distance(Vector3 other){
		double xDiff = x - other.x;
		double yDiff = y - other.y;
		double zDiff = z - other.z;
		return Math.sqrt(xDiff * xDiff + yDiff * yDiff + zDiff * zDiff);
	}

	public double magnitude(){
		return Math.sqrt(magnitudeSquared());
	}

	public double magnitudeSquared(){
		return x * x + y * y + z * z;
	}

	public Vector3 normalised(){
		if(isZero()){
			// throw new IllegalStateException("Cannot normalize a vector with length
			// zero!");
			return new Vector3(0, 0, 0);
		}
		return this.scale(1 / magnitude());
	}

	public double dot(Vector3 other){
		return x * other.x + y * other.y + z * other.z;
	}

	public boolean isZero(){
		return x == 0 && y == 0 && z == 0;
	}

	public Vector2 flatten(){
		return new Vector2(x, y);
	}

	public double angle(Vector3 other){
		double mag2 = magnitudeSquared();
		double vmag2 = other.magnitudeSquared();
		double dot = dot(other);
		return Math.acos(dot / Math.sqrt(mag2 * vmag2));
	}

	public Vector3 cross(Vector3 other){
		double tx = y * other.z - z * other.y;
		double ty = z * other.x - x * other.z;
		double tz = x * other.y - y * other.x;
		return new Vector3(tx, ty, tz);
	}

	public rlbot.vector.Vector3 fbs(){
		return new rlbot.vector.Vector3(-x, y, z);
	}

	public Vector3 lerp(Vector3 other, double t){
		return this.plus(other.minus(this).scale(t));
	}

	public Vector3 multiply(Vector3 scale){
		return new Vector3(x * scale.x, y * scale.y, z * scale.z);
	}

	public Vector3 reciprocal(){
		return new Vector3(1 / this.x, 1 / this.y, 1 / this.z);
	}

	@Override
	public String toString(){
		if(this.magnitude() <= 1){
			return "(" + Math.round(x * 100D) / 100D + ", " + Math.round(y * 100D) / 100D + ", "
					+ Math.round(z * 100D) / 100D + ")";
		}else{
			return "(" + (int)x + ", " + (int)y + ", " + (int)z + ")";
		}
	}

	public Vector3 withX(double x){
		return new Vector3(x, y, z);
	}

	public Vector3 withY(double y){
		return new Vector3(x, y, z);
	}

	public Vector3 withZ(double z){
		return new Vector3(x, y, z);
	}

	public Vector3 setDistanceFrom(Vector3 other, double distance){
		return this.minus(other).scaleToMagnitude(distance).plus(other);
	}

	public Vector3 clamp(double x, double y){
		return new Vector3(Math.copySign(Math.min(Math.abs(this.x), x), this.x),
				Math.copySign(Math.min(Math.abs(this.y), y), this.y), this.z);
	}

	public Vector3 clamp(){
		return clamp(Constants.PITCH_WIDTH_SOCCAR - 250, Constants.PITCH_LENGTH_SOCCAR - 250);
	}

	/**
	 * https://github.com/tarehart/ReliefBot/blob/9a0646d40e3ea16b431dd351177ff30dbe12f302/src/main/java/tarehart/rlbot/math/vector/Vector3.kt#L128
	 */
	public Vector3 projectToPlane(Vector3 normal){
		return this.plus(normal.scale(-this.dot(normal)));
	}

	/**
	 * https://github.com/tarehart/ReliefBot/blob/9a0646d40e3ea16b431dd351177ff30dbe12f302/src/main/java/tarehart/rlbot/math/vector/Vector3.kt#L134
	 */
	public Vector3 shadowOntoPlane(Plane plane){
		return this.minus(plane.normal.scale(plane.getNormalDistance(this)));
	}

	public DesiredVector3 toDesired(){
		return new DesiredVector3((float)this.x, (float)this.y, (float)this.z);
	}

	@Override
	public boolean equals(Object obj){
		if(this == obj)
			return true;
		if(obj == null)
			return false;
		if(getClass() != obj.getClass())
			return false;
		Vector3 other = (Vector3)obj;
		if(Double.doubleToLongBits(x) != Double.doubleToLongBits(other.x))
			return false;
		if(Double.doubleToLongBits(y) != Double.doubleToLongBits(other.y))
			return false;
		if(Double.doubleToLongBits(z) != Double.doubleToLongBits(other.z))
			return false;
		return true;
	}

}
