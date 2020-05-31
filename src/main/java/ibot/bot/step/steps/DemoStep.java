package ibot.bot.step.steps;

import java.awt.Color;

import ibot.bot.input.Bundle;
import ibot.bot.input.Info;
import ibot.bot.input.Pencil;
import ibot.bot.physics.DrivePhysics;
import ibot.bot.stack.PopStack;
import ibot.bot.step.Priority;
import ibot.bot.utils.Constants;
import ibot.bot.utils.MathsUtils;
import ibot.input.Car;
import ibot.input.DataPacket;
import ibot.output.Controls;
import ibot.output.Output;
import ibot.vectors.Vector3;

public class DemoStep extends DriveStep {

	// TODO Jump.

	private static final double MAX_SIM_TIME = 3, SIM_DELTA_TIME = Constants.DT * 6, HIT_DISTANCE = 175;

	private final int victimIndex;
	private final double maxRunTime;

	private Vector3 lastVelocity;

	public DemoStep(Bundle bundle){
		super(bundle);
		DataPacket packet = this.bundle.packet;
		this.victimIndex = getVictimIndex(packet);

		this.lastVelocity = new Vector3();
		if(this.isValid()){
			Car victim = packet.cars[this.victimIndex];
			this.maxRunTime = packet.time + victim.position.distance(packet.car.position) / 1100;
		}else{
			this.maxRunTime = -10;
		}

		// Drive.
		this.dodge = false;
		this.ignoreRadius = true;
		this.reverse = false;
		this.withTargetVelocity(MathsUtils.lerp(Constants.MAX_CAR_VELOCITY, Constants.SUPERSONIC_VELOCITY, 0.5));
	}

	@Override
	public Output getOutput(){
		if(!this.isValid())
			return new PopStack();

		Info info = this.bundle.info;
		DataPacket packet = this.bundle.packet;
		Pencil pencil = this.bundle.pencil;
		Car car = packet.car;
		Car victim = packet.cars[this.victimIndex];

		// Pop.
		if(victim.isDemolished || packet.time > this.maxRunTime)
			return new PopStack();

		// Gather information on the victim.
		Vector3 velocity = victim.velocity;
		Vector3 acceleration = velocity.minus(this.lastVelocity).scale(1 / info.deltaTime);
		Vector3 localDirection = MathsUtils.local(victim.orientation, acceleration).withZ(0).normalised();
		this.lastVelocity = victim.velocity;
		double throttle = acceleration.dot(victim.orientation.forward)
				/ DrivePhysics.determineAcceleration(victim.forwardVelocity, 1, false);
		boolean boost = (throttle > 1.1);
		throttle = MathsUtils.clamp(throttle, -1, 1);

		// Simulate and intercept the opponent.
		double availableAcceleration = DrivePhysics.determineAcceleration(car.forwardVelocity, 1, car.boost >= 1);
		Vector3 target = null;
		Vector3 position = victim.position,
				forward = (victim.hasWheelContact ? velocity.normalised() : victim.orientation.forward);
		double time = 0, boostAmount = victim.boost;
		while(time < MAX_SIM_TIME){
			double forwardVelocity = forward.dot(velocity);
			double accelerationProvided;
			if(victim.hasWheelContact){
				accelerationProvided = DrivePhysics.determineAcceleration(forwardVelocity, throttle,
						boost && boostAmount >= 1);
			}else{
				accelerationProvided = (boost ? throttle * Constants.THROTTLE_AIR_ACCELERATION
						: Constants.BOOST_AIR_ACCELERATION);
			}
			acceleration = MathsUtils.global(victim, localDirection).minus(victim.position).scale(accelerationProvided);
			if(accelerationProvided < 0)
				acceleration = acceleration.withX(-acceleration.x);
			if(!victim.hasWheelContact)
				acceleration = acceleration.plus(info.arena.getGravity());
			velocity = velocity.plus(acceleration.scale(SIM_DELTA_TIME));
			velocity = velocity.scale(Math.min(1, Constants.MAX_CAR_VELOCITY / velocity.magnitude()));
			Vector3 nextPosition = position.plus(velocity.scale(SIM_DELTA_TIME));
			pencil.renderer.drawLine3d(Color.GRAY, position, nextPosition);
			pencil.renderer.drawLine3d(Color.GREEN, nextPosition, nextPosition.plus(forward.scale(100)));
			pencil.renderer.drawLine3d(Color.BLUE, nextPosition, nextPosition.plus(acceleration.scaleToMagnitude(100)));
			if(victim.hasWheelContact)
				forward = nextPosition.minus(position).scaleToMagnitude(Math.signum(accelerationProvided));
			position = nextPosition;
			boostAmount -= Constants.BOOST_USAGE * SIM_DELTA_TIME;
			time += SIM_DELTA_TIME;

			double requiredAcceleration = (2
					* (position.distance(car.position) - HIT_DISTANCE - time * car.forwardVelocityAbs))
					/ Math.pow(time, 2);
			if(requiredAcceleration < availableAcceleration * 0.85
					&& car.forwardVelocityAbs + requiredAcceleration * time < Constants.MAX_CAR_VELOCITY){
				target = position;
				break;
			}
		}
		if(target == null)
			target = position;
		this.target = target;

		// Render.
		pencil.stackRenderString("Throttle: " + MathsUtils.round(throttle), Color.WHITE);
		pencil.stackRenderString("Boost: " + boost, Color.WHITE);
		pencil.stackRenderString(MathsUtils.round(time) + "s", Color.RED);

		Controls controls = (Controls)super.getOutput();
//		controls.withBoost(!controls.holdHandbrake() && !car.isSupersonic);
		return controls;
	}

	private static int getVictimIndex(DataPacket packet){
		double shortestDistance = Double.MAX_VALUE;
		int index = -1;
		for(Car car : packet.enemies){
			double distance = car.position.distance(packet.car.position);
			if(!car.isDemolished && distance < shortestDistance){
				index = car.index;
				shortestDistance = distance;
			}
		}
		return index;
	}

	public boolean isValid(){
		return this.victimIndex != -1;
	}

	@Override
	public int getPriority(){
		return this.victimIndex == -1 ? Priority.IDLE : Priority.ACTION;
	}

}
