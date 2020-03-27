package ibot.bot.actions;

import ibot.bot.controls.AirControl;
import ibot.bot.input.Bundle;
import ibot.bot.utils.MathsUtils;
import ibot.input.Car;
import ibot.input.DataPacket;
import ibot.output.ControlsOutput;
import ibot.vectors.Vector2;
import ibot.vectors.Vector3;

public class FastDodge extends Action {

	private static final double[] TIMING = new double[] { 0.11, 0.16, 0.2, 0.9, 1.3 };

	private Vector3 directionGlobal;
	private double pitch, yaw;

	public FastDodge(Bundle bundle, Vector3 directionGlobal){
		super(bundle);

		this.directionGlobal = directionGlobal.normalised();

		double radians = Vector2.Y
				.correctionAngle(MathsUtils.local(bundle.packet.car.orientation, directionGlobal).flatten());
		this.pitch = -Math.cos(radians);
		this.yaw = -Math.sin(radians) * 2;
	}

	@Override
	public ControlsOutput getOutput(){
		DataPacket packet = this.bundle.packet;
		Car car = this.bundle.packet.car;

		double timeElapsed = (packet.time - this.getStartTime());

		double dot = car.orientation.forward.dot(this.directionGlobal);

		boolean boost = (dot > 0.75);

		if(timeElapsed < TIMING[0]){
			return new ControlsOutput().withJump(true).withBoost(boost);
		}else if(timeElapsed < TIMING[2]){
			ControlsOutput controls = new ControlsOutput().withJump(timeElapsed > TIMING[1]).withBoost(boost);
			if(packet.car.hasDoubleJumped)
				return controls;
			return controls.withPitch(pitch).withYaw(yaw);
		}else if(timeElapsed < TIMING[3]){
			return new ControlsOutput().withJump(false).withBoost(boost);
		}

		this.setFinished(car.hasWheelContact || dot > 0.925 || timeElapsed > TIMING[4]);

		return new ControlsOutput().withOrient(AirControl.getRollPitchYaw(car, this.directionGlobal)).withJump(false)
				.withBoost(boost);
	}

}
