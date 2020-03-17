package ibot.bot.actions;

import ibot.bot.controls.AirControl;
import ibot.bot.utils.DataBot;
import ibot.bot.utils.MathsUtils;
import ibot.input.Car;
import ibot.input.DataPacket;
import ibot.output.ControlsOutput;
import ibot.vectors.Vector2;
import ibot.vectors.Vector3;

public class HalfFlip extends Action {

	private static final double[] TIMING = new double[] {0.1, 0.15, 0.3, 0.45};

	private static final double MIN_RADIANS = Math.toRadians(155), MAX_RADIANS = Math.toRadians(170);

	private Vector3 directionGlobal;
	private double pitch, yaw;

	public HalfFlip(DataBot bot){
		super(bot);

		this.directionGlobal = bot.car.orientation.forward.withZ(0).scaleToMagnitude(-1);

		double radians = Vector2.Y.correctionAngle(MathsUtils.local(bot.car.orientation, this.directionGlobal).flatten());
		radians = Math.copySign(MathsUtils.clamp(Math.abs(radians), MIN_RADIANS, MAX_RADIANS), radians);

		this.pitch = -Math.cos(radians);
		this.yaw = Math.sin(radians);
	}

	@Override
	public ControlsOutput getOutput(DataPacket packet){
		double timeElapsed = (packet.time - this.getStartTime());

		Car car = packet.car;

		boolean boost = (car.orientation.forward.dot(this.directionGlobal) > 0.75);

		double[] orient = AirControl.getRollPitchYaw(car, this.directionGlobal);

		if(timeElapsed < TIMING[0]){
			return new ControlsOutput().withJump(true).withPitch(this.pitch).withBoost(boost);
		}else if(timeElapsed < TIMING[2]){
			ControlsOutput controls = new ControlsOutput().withJump(timeElapsed > TIMING[1]).withBoost(boost).withPitch(this.pitch);
			if(controls.holdJump() == this.bot.lastControls.holdJump()){
				controls.withRoll(orient[0]);
			}
			if(!car.hasDoubleJumped){
				return controls.withYaw(this.yaw);
			}
		}else if(timeElapsed < TIMING[3]){
//			return new ControlsOutput().withJump(false).withBoost(boost).withPitch(-this.pitch).withRoll(orient[0]); //.withYaw(orient[2]);
			return new ControlsOutput().withJump(false).withBoost(boost).withOrient(orient);
		}

		this.setFinished(car.hasWheelContact || timeElapsed > TIMING[3] + 0.4);

		return new ControlsOutput().withOrient(orient).withJump(false).withBoost(boost);
	}

}
