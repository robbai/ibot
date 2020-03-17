package ibot.bot.intercept;

import ibot.bot.utils.Constants;
import ibot.input.Car;
import ibot.vectors.Vector3;

public class CarTrajectory {
	
	public static final double STEP = (1D / 60), MAX_TIME = 5;
	
	public final int index;
	
//	private Vector3 lastPosition;
	private Vector3 lastVelocity;
	private double lastElapsed;

	public CarTrajectory(int index, Car car){
		super();
		this.index = index;
//		lastPosition = car.position;
		lastVelocity = car.velocity;
		lastElapsed = car.time;
	}
	
	public CarSlice[] estimateTrajectory(Car car){
		double dt = (car.time - lastElapsed);
		
		// Determine acceleration.
		Vector3 acceleration = car.velocity.minus(lastVelocity).scale(1D / dt);
		double maxAcceleration = maxAcceleration(car);
		if(acceleration.magnitude() > maxAcceleration) {
			acceleration = acceleration.scaleToMagnitude(maxAcceleration);
		}
		
		// Simulate.
		CarSlice[] path = new CarSlice[(int)(MAX_TIME / STEP)];
		Vector3 carPosition = car.position, carVelocity = car.velocity;
//		this.lastPosition = carPosition;
		this.lastVelocity = car.velocity;
		this.lastElapsed = car.time;
		double time = car.time;
		path[0] = new CarSlice(carPosition, carVelocity, time);
		int i = 1;
		while(true){
			carVelocity = carVelocity.plus(acceleration.scale(STEP));
			if(carVelocity.magnitude() > Constants.MAX_CAR_VELOCITY){
				carVelocity = carVelocity.scaleToMagnitude(Constants.MAX_CAR_VELOCITY);
			}
			
			carPosition = carPosition.plus(carVelocity.scale(STEP));
			
			path[i] = new CarSlice(carPosition, carVelocity, time);
			
			time += STEP;
			
			i++;
			if(i >= path.length) break;
		}
		
		return path;
	}
	
	private static double maxAcceleration(Car car){
		return (car.hasWheelContact ? Constants.BRAKE_ACCELERATION : Constants.BOOST_AIR_ACCELERATION);
	}

}
