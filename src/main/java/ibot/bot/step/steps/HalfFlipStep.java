package ibot.bot.step.steps;

import java.awt.Color;

import ibot.bot.controls.AirControl;
import ibot.bot.input.Bundle;
import ibot.bot.step.Priority;
import ibot.bot.step.Step;
import ibot.bot.utils.maths.MathsUtils;
import ibot.input.Car;
import ibot.input.DataPacket;
import ibot.output.Controls;
import ibot.output.Output;
import ibot.vectors.Vector2;
import ibot.vectors.Vector3;

public class HalfFlipStep extends Step {

	private static final double[] TIMING = new double[] { 0.1, 0.12, 0.15, 0.3, 0.9 };

	private static final double MIN_RADIANS = Math.toRadians(160), MAX_RADIANS = Math.toRadians(173);

	private Vector3 directionGlobal;
	private double pitch, yaw;

	public HalfFlipStep(Bundle bundle, Vector3 directionGlobal){
		super(bundle);

		Car car = bundle.packet.car;

		this.directionGlobal = directionGlobal;

		double radians = Vector2.Y.correctionAngle(MathsUtils.local(car.orientation, this.directionGlobal).flatten());
		radians = Math.copySign(MathsUtils.clamp(Math.abs(radians), MIN_RADIANS, MAX_RADIANS), radians);

		this.pitch = -Math.cos(radians);
		this.yaw = Math.sin(radians);
	}

	public HalfFlipStep(Bundle bundle){
		this(bundle, bundle.packet.car.orientation.forward.withZ(0).scaleToMagnitude(-1));
	}

	@Override
	public Output getOutput(){
		DataPacket packet = this.bundle.packet;
		Car car = packet.car;

		double timeElapsed = (packet.time - this.getStartTime());

		boolean boost = (car.orientation.forward.dot(this.directionGlobal) > 0.8);

		double[] orient = AirControl.getRollPitchYaw(car, this.directionGlobal);

		this.bundle.pencil.stackRenderString((int)Math.toDegrees(Math.asin(this.yaw)) + " degrees", Color.BLACK);

		if(timeElapsed < TIMING[0]){
			return new Controls().withJump(true).withPitch(this.pitch).withBoost(boost);
		}else if(timeElapsed < TIMING[4]){
			Controls controls = new Controls().withJump(timeElapsed > TIMING[1]).withBoost(boost);

			if(!car.hasDoubleJumped){
				controls.withPitch(this.pitch).withYaw(this.yaw);
			}else if(timeElapsed > TIMING[3]){
				controls.withOrient(orient);
			}else if(timeElapsed > TIMING[2]){
				controls.withPitch(-this.pitch).withRoll(car.angularVelocity.roll);
			}

			return controls;
		}

		this.setFinished(car.hasWheelContact || timeElapsed > TIMING[4] + 0.4);

		return new Controls().withOrient(orient).withJump(false).withBoost(boost);
	}

	@Override
	public int getPriority(){
		return Priority.ACTION;
	}

}
