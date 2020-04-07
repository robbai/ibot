package ibot.bot.step.steps;

import ibot.bot.controls.AirControl;
import ibot.bot.input.Bundle;
import ibot.bot.step.Priority;
import ibot.bot.step.Step;
import ibot.bot.utils.MathsUtils;
import ibot.input.Car;
import ibot.input.DataPacket;
import ibot.output.Controls;
import ibot.output.Output;
import ibot.vectors.Vector2;
import ibot.vectors.Vector3;

public class HalfFlipStep extends Step {

	private static final double[] TIMING = new double[] { 0.075, 0.15, 0.3, 0.45 };

	private static final double MIN_RADIANS = Math.toRadians(155), MAX_RADIANS = Math.toRadians(170);

	private Vector3 directionGlobal;
	private double pitch, yaw;

	public HalfFlipStep(Bundle bundle){
		super(bundle);

		Car car = bundle.packet.car;

		this.directionGlobal = car.orientation.forward.withZ(0).scaleToMagnitude(-1);

		double radians = Vector2.Y.correctionAngle(MathsUtils.local(car.orientation, this.directionGlobal).flatten());
		radians = Math.copySign(MathsUtils.clamp(Math.abs(radians), MIN_RADIANS, MAX_RADIANS), radians);

		this.pitch = -Math.cos(radians);
		this.yaw = Math.sin(radians);
	}

	@Override
	public Output getOutput(){
		DataPacket packet = this.bundle.packet;
		Car car = packet.car;

		double timeElapsed = (packet.time - this.getStartTime());

		boolean boost = (car.orientation.forward.dot(this.directionGlobal) > 0.75);

		double[] orient = AirControl.getRollPitchYaw(car, this.directionGlobal);

		if(timeElapsed < TIMING[0]){
			return new Controls().withJump(true).withPitch(this.pitch).withBoost(boost);
		}else if(timeElapsed < TIMING[2]){
			Controls controls = new Controls().withJump(timeElapsed > TIMING[1]).withBoost(boost).withPitch(this.pitch);
			if(controls.holdJump() == this.bundle.info.lastControls.holdJump()){
				controls.withRoll(orient[0]);
			}
			if(!car.hasDoubleJumped){
				return controls.withYaw(this.yaw);
			}
		}else if(timeElapsed < TIMING[3]){
//			return new Controls().withJump(false).withBoost(boost).withPitch(-this.pitch).withRoll(orient[0]); //.withYaw(orient[2]);
			return new Controls().withJump(false).withBoost(boost).withOrient(orient);
		}

		this.setFinished(car.hasWheelContact || timeElapsed > TIMING[3] + 0.4);

		return new Controls().withOrient(orient).withJump(false).withBoost(boost);
	}

	@Override
	public int getPriority(){
		return Priority.ACTION;
	}

}
