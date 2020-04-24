package ibot.bot.utils;

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
		return this.normal.dot(normal) < 0.95;
	}

	public boolean differentNormal(Plane plane){
		return this.differentNormal(plane.normal);
	}

	public boolean differentNormal(Car car){
		return this.normal.dot(car.orientation.up) < 0.6;
	}

}
