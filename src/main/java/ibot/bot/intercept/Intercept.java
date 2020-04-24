package ibot.bot.intercept;

import ibot.bot.utils.Plane;
import ibot.input.Car;
import ibot.prediction.Slice;
import ibot.vectors.Vector3;

public class Intercept extends Slice {

	/**
	 * Represents the position with added offset.
	 */
	public final Vector3 intersectPosition;
	public final Plane plane;
	public final Car car;

	public Intercept(Vector3 position, Car car, Vector3 intersectPosition, Plane plane, double time){
		super(position, time);
		this.car = car;
		this.plane = plane;
		this.intersectPosition = intersectPosition;
	}

	public Intercept withIntersectPosition(Vector3 intersectPosition){
		return new Intercept(this.position, this.car, intersectPosition, this.plane, this.time);
	}

	public Vector3 getOffset(){
		return this.intersectPosition.minus(this.position);
	}

}
