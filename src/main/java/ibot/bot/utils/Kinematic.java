package ibot.bot.utils;

import java.util.OptionalDouble;

public class Kinematic {

	private OptionalDouble displacement, initialVelocity, finalVelocity, acceleration, time;

	public Kinematic(){
		this.displacement = OptionalDouble.empty();
		this.initialVelocity = OptionalDouble.empty();
		this.finalVelocity = OptionalDouble.empty();
		this.acceleration = OptionalDouble.empty();
		this.time = OptionalDouble.empty();
	}

	public Kinematic withDisplacement(double displacement){
		this.displacement = OptionalDouble.of(displacement);
		return this;
	}

	public Kinematic withInitialVelocity(double initialVelocity){
		this.initialVelocity = OptionalDouble.of(initialVelocity);
		return this;
	}

	public Kinematic withFinalVelocity(double finalVelocity){
		this.finalVelocity = OptionalDouble.of(finalVelocity);
		return this;
	}

	public Kinematic withAcceleration(double acceleration){
		this.acceleration = OptionalDouble.of(acceleration);
		return this;
	}

	public Kinematic withTime(double time){
		this.time = OptionalDouble.of(time);
		return this;
	}

	public OptionalDouble getDisplacement(){
		if(displacement.isPresent()){
			return displacement;
		}else if(!initialVelocity.isPresent()){
			return OptionalDouble.of(finalVelocity.getAsDouble() * time.getAsDouble()
					- 0.5 * acceleration.getAsDouble() * Math.pow(time.getAsDouble(), 2));
		}else if(!finalVelocity.isPresent()){
			return OptionalDouble.of(initialVelocity.getAsDouble() * time.getAsDouble()
					+ 0.5 * acceleration.getAsDouble() * Math.pow(time.getAsDouble(), 2));
		}else if(!acceleration.isPresent()){
			return OptionalDouble
					.of(0.5 * (initialVelocity.getAsDouble() + finalVelocity.getAsDouble()) * time.getAsDouble());
		}else if(!time.isPresent()){
			return OptionalDouble
					.of((Math.pow(finalVelocity.getAsDouble(), 2) - Math.pow(initialVelocity.getAsDouble(), 2))
							/ (2 * acceleration.getAsDouble()));
		}
		return OptionalDouble.empty();
	}

	public OptionalDouble getInitialVelocity(){
		if(initialVelocity.isPresent()){
			return initialVelocity;
		}else if(!displacement.isPresent()){
			return OptionalDouble.of(finalVelocity.getAsDouble() - acceleration.getAsDouble() * time.getAsDouble());
		}else if(!finalVelocity.isPresent()){
			return OptionalDouble.of(displacement.getAsDouble() / time.getAsDouble()
					- (acceleration.getAsDouble() * time.getAsDouble()) / 2);
		}else if(!acceleration.isPresent()){
			return OptionalDouble
					.of((2 * displacement.getAsDouble()) / time.getAsDouble() - finalVelocity.getAsDouble());
		}else if(!time.isPresent()){
			return OptionalDouble.of(Math.sqrt(Math.pow(finalVelocity.getAsDouble(), 2)
					- 2 * acceleration.getAsDouble() * displacement.getAsDouble()));
		}
		return OptionalDouble.empty();
	}

	public OptionalDouble getFinalVelocity(){
		if(finalVelocity.isPresent()){
			return finalVelocity;
		}else if(!displacement.isPresent()){
			return OptionalDouble.of(initialVelocity.getAsDouble() + acceleration.getAsDouble() * time.getAsDouble());
		}else if(!initialVelocity.isPresent()){
			return OptionalDouble.of(displacement.getAsDouble() / time.getAsDouble()
					+ (acceleration.getAsDouble() * time.getAsDouble()) / 2);
		}else if(!acceleration.isPresent()){
			return OptionalDouble
					.of((2 * displacement.getAsDouble()) / time.getAsDouble() - initialVelocity.getAsDouble());
		}else if(!time.isPresent()){
			return OptionalDouble.of(Math.sqrt(2 * acceleration.getAsDouble() * displacement.getAsDouble()
					+ Math.pow(initialVelocity.getAsDouble(), 2)));
		}
		return OptionalDouble.empty();
	}

	public OptionalDouble getAcceleration(){
		if(acceleration.isPresent()){
			return acceleration;
		}else if(!displacement.isPresent()){
			return OptionalDouble
					.of((finalVelocity.getAsDouble() - initialVelocity.getAsDouble()) / time.getAsDouble());
		}else if(!initialVelocity.isPresent()){
			return OptionalDouble
					.of((2 * (finalVelocity.getAsDouble() * time.getAsDouble() - displacement.getAsDouble()))
							/ Math.pow(time.getAsDouble(), 2));
		}else if(!finalVelocity.isPresent()){
			return OptionalDouble
					.of((2 * (displacement.getAsDouble() - initialVelocity.getAsDouble() * time.getAsDouble()))
							/ Math.pow(time.getAsDouble(), 2));
		}else if(!time.isPresent()){
			return OptionalDouble
					.of((Math.pow(finalVelocity.getAsDouble(), 2) - Math.pow(initialVelocity.getAsDouble(), 2))
							/ (2 * displacement.getAsDouble()));
		}
		return OptionalDouble.empty();
	}

	public OptionalDouble getTime(){
		if(time.isPresent()){
			return time;
		}else if(!displacement.isPresent()){
			return OptionalDouble
					.of((finalVelocity.getAsDouble() - initialVelocity.getAsDouble()) / acceleration.getAsDouble());
		}else if(!initialVelocity.isPresent()){
			return OptionalDouble.of((finalVelocity.getAsDouble() - Math.sqrt(Math.pow(finalVelocity.getAsDouble(), 2)
					- 2 * acceleration.getAsDouble() * displacement.getAsDouble())) / acceleration.getAsDouble());
		}else if(!finalVelocity.isPresent()){
			return OptionalDouble.of((Math.sqrt(2 * acceleration.getAsDouble() * displacement.getAsDouble()
					+ Math.pow(initialVelocity.getAsDouble(), 2)) - initialVelocity.getAsDouble())
					/ acceleration.getAsDouble());
		}else if(!acceleration.isPresent()){
			return OptionalDouble.of(
					(2 * displacement.getAsDouble()) / (initialVelocity.getAsDouble() + finalVelocity.getAsDouble()));
		}
		return OptionalDouble.empty();
	}

}
