package ibot.bot.step.steps;

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

public class FastDodgeStep extends Step {

	private static final double[] TIMING = new double[] { 0.11, 0.16, 0.2, 0.9, 1.3 };

	private Vector3 directionGlobal;
	private double pitch, yaw;

	public FastDodgeStep(Bundle bundle, Vector3 directionGlobal){
		super(bundle);

		this.directionGlobal = directionGlobal.normalised();

		double radians = Vector2.Y
				.correctionAngle(MathsUtils.local(bundle.packet.car.orientation, directionGlobal).flatten());
		this.pitch = -Math.cos(radians);
		this.yaw = -Math.sin(radians) * 2;
	}

	@Override
	public Output getOutput(){
		DataPacket packet = this.bundle.packet;
		Car car = this.bundle.packet.car;

		double timeElapsed = (packet.time - this.getStartTime());

		double dot = car.orientation.forward.dot(this.directionGlobal);

		boolean boost = (dot > 0.85);

		if(timeElapsed < TIMING[0]){
			return new Controls().withJump(true).withBoost(boost);
		}else if(timeElapsed < TIMING[2]){
			Controls controls = new Controls().withJump(timeElapsed > TIMING[1]).withBoost(boost);
			if(packet.car.hasDoubleJumped)
				return controls;
			return controls.withPitch(pitch).withYaw(yaw);
		}else if(timeElapsed < TIMING[3]){
			return new Controls().withJump(false).withBoost(boost);
		}

		this.setFinished(car.hasWheelContact || dot > 0.925 || timeElapsed > TIMING[4]);

		return new Controls().withOrient(AirControl.getRollPitchYaw(car, this.directionGlobal)).withJump(false)
				.withBoost(boost);
	}

	@Override
	public int getPriority(){
		return Priority.ACTION;
	}

}
