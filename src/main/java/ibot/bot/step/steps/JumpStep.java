package ibot.bot.step.steps;

import ibot.bot.controls.AirControl;
import ibot.bot.input.Bundle;
import ibot.bot.step.Priority;
import ibot.bot.step.Step;
import ibot.bot.utils.Constants;
import ibot.bot.utils.MathsUtils;
import ibot.input.DataPacket;
import ibot.output.Controls;
import ibot.output.Output;

public class JumpStep extends Step {

	public static final double DOUBLE_JUMP_DELAY = (Constants.DT * 3);

	private static final double ORIENT_DELAY = 0.1;

	private final double holdTime;

	public JumpStep(Bundle bundle, double holdTime){
		super(bundle);
		this.holdTime = MathsUtils.clamp(holdTime, 0, Constants.JUMP_MAX_HOLD);
	}

	@Override
	public Output getOutput(){
		DataPacket packet = this.bundle.packet;
		double timeElapsed = (packet.time - this.getStartTime());
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
