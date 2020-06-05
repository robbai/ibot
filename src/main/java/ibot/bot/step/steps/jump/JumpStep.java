package ibot.bot.step.steps.jump;

import java.util.OptionalDouble;

import ibot.bot.controls.AirControl;
import ibot.bot.input.Bundle;
import ibot.bot.step.Priority;
import ibot.bot.step.Step;
import ibot.bot.utils.maths.MathsUtils;
import ibot.bot.utils.rl.Constants;
import ibot.input.DataPacket;
import ibot.output.Controls;
import ibot.output.Output;

public class JumpStep extends Step {

	private static final double ORIENT_DELAY = 0.1;

	protected final double holdTime;
	protected OptionalDouble firstOutputTime = OptionalDouble.empty();

	public JumpStep(Bundle bundle, double holdTime){
		super(bundle);
		this.holdTime = MathsUtils.clamp(holdTime, 0, Constants.MAX_JUMP_HOLD_TIME);
	}

	@Override
	public Output getOutput(){
		DataPacket packet = this.bundle.packet;

		if(!this.firstOutputTime.isPresent()){
			this.firstOutputTime = OptionalDouble.of(packet.time);
			return new Controls();
		}

		double timeElapsed = (packet.time - this.firstOutputTime.getAsDouble());
		this.setFinished(timeElapsed > this.holdTime);
		Controls controls = new Controls().withJump(timeElapsed <= this.holdTime).withThrottle(0.02);
		if(timeElapsed > this.holdTime + ORIENT_DELAY)
			controls.withOrient(AirControl.getRollPitchYaw(packet.car, packet.car.velocity.withZ(0)));
		return controls;
	}

	@Override
	public int getPriority(){
		return Priority.ACTION;
	}

}
