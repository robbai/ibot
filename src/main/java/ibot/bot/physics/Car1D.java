package ibot.bot.physics;

import ibot.bot.utils.Constants;
import ibot.input.Car;
import ibot.vectors.Vector3;

public class Car1D {

	private static final double DT = Constants.DT * 2;

	private double time, displacement, velocity, boost, maximumSpeed = Constants.MAX_CAR_VELOCITY;

	public Car1D(double time, double displacement, double velocity, double boost){
		super();
		this.time = time;
		this.displacement = displacement;
		this.velocity = velocity;
		this.boost = boost;
	}

	public Car1D(){
		this(0, 0, 0, 100);
	}

	public Car1D(Car car){
		this(car.time, 0, car.forwardVelocity, car.boost);
	}

	public Car1D(Car car, Vector3 target){
		this(car.time, 0, car.velocity.dot(target.minus(car.position).normalised()), car.boost);
	}

	public double getTime(){
		return time;
	}

	public Car1D withTime(double time){
		this.time = time;
		return this;
	}

	public double getDisplacement(){
		return displacement;
	}

	public Car1D withDisplacement(double displacement){
		this.displacement = displacement;
		return this;
	}

	public double getVelocity(){
		return velocity;
	}

	public Car1D withVelocity(double velocity){
		this.velocity = velocity;
		return this;
	}

	public double getBoost(){
		return boost;
	}

	public Car1D withBoost(double boost){
		this.boost = boost;
		return this;
	}

	public double getMaximumSpeed(){
		return displacement;
	}

	public Car1D withMaximumSpeed(double maximumSpeed){
		this.maximumSpeed = Math.abs(maximumSpeed);
		return this;
	}

	public Car1D step(double throttle, boolean boost){
		boost &= this.boost > 0;

		double acceleration = DrivePhysics.determineAcceleration(this.velocity, throttle, boost);
		double deltaVelocity = acceleration * DT;

		// Don't exceed the speed limit.
		if(Math.abs(this.velocity + deltaVelocity) > this.maximumSpeed){
			deltaVelocity = Math.copySign(this.maximumSpeed, velocity) - velocity;
			boost &= deltaVelocity * Math.signum(acceleration) > 10;
		}

		this.velocity += deltaVelocity;
		this.displacement += this.velocity * DT;
		if(boost)
			this.boost -= Constants.BOOST_USAGE * DT;
		this.time += DT;

		return this;
	}

	public Car1D stepTime(double throttle, boolean boost, double time){
		while(this.time < time){
			this.step(throttle, boost);
		}
		return this;
	}

	public Car1D stepDisplacement(double throttle, boolean boost, double displacement){
		double sign = (boost || throttle >= 0 ? 1 : -1);
		while(sign * (displacement - this.displacement) > 0){
			this.step(throttle, boost);
		}
		return this;
	}

	public Car1D stepVelocity(double throttle, boolean boost, double velocity){
		double sign = (boost || throttle >= 0 ? 1 : -1);
		while(sign * (velocity - this.velocity) > 0){
			this.step(throttle, boost);
		}
		return this;
	}

	public Car1D stepMaxVelocity(double throttle, boolean boost){
		double lastVelocity = this.velocity;
		while(true){
			this.step(throttle, boost);
			if(Math.abs(this.velocity - lastVelocity) < 1)
				break;
			lastVelocity = this.velocity;
		}
		return this;
	}

}