package ibot.bot.utils.maths;

import ibot.input.Car;
import ibot.vectors.Vector3;

public class Plane {

	public Vector3 normal, centre;

	public Plane(Vector3 normal, Vector3 centre){
		super();
		this.normal = normal.normalised();
		this.centre = centre;
	}

	public double getNormalDistance(Vector3 vec){
		return vec.minus(this.centre).dot(this.normal);
	}

	public boolean differentNormal(Vector3 normal){
		return this.normal.dot(normal) < 0.925;
	}

	public boolean differentNormal(Plane plane){
		return this.differentNormal(plane.normal);
	}

	public static Plane asCar(Car car){
		return new Plane(car.orientation.up, new Vector3(car.position));
	}

}
