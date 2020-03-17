package ibot.bot.intercept;

import ibot.prediction.Slice;
import ibot.vectors.Vector3;

public class CarSlice extends Slice {

	public final Vector3 carVelocity;

	public CarSlice(Vector3 intersectPosition, Vector3 carVelocity, double time){
		super(intersectPosition, time);
		this.carVelocity = carVelocity;
	}

}
