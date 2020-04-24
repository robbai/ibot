package ibot.bot.intercept;

import ibot.bot.utils.Plane;
import ibot.input.Car;
import ibot.vectors.Vector3;

public class SeamIntercept extends Intercept {

	public final Vector3 seamPosition;

	public SeamIntercept(Vector3 position, Car car, Vector3 intersectPosition, Plane plane, Vector3 seamPosition,
			double time){
		super(position, car, intersectPosition, plane, time);
		this.seamPosition = seamPosition;
	}

	public SeamIntercept withIntersectPosition(Vector3 intersectPosition){
		return new SeamIntercept(this.position, this.car, intersectPosition, this.plane, this.seamPosition, this.time);
	}

}
