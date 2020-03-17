package ibot.bot.actions;

import ibot.bot.controls.AirControl;
import ibot.bot.utils.Constants;
import ibot.bot.utils.DataBot;
import ibot.bot.utils.MathsUtils;
import ibot.input.DataPacket;
import ibot.output.ControlsOutput;

public class Jump extends Action {

	private static final double ORIENT_DELAY = 0.1;
	
	private final double holdTime;

	public Jump(DataBot bot, double holdTime){
		super(bot);
		this.holdTime = MathsUtils.clamp(holdTime, 0, Constants.JUMP_MAX_HOLD);
	}

	@Override
	public ControlsOutput getOutput(DataPacket packet){
		double timeElapsed = (packet.time - this.getStartTime());
		this.setFinished(timeElapsed > this.holdTime);
		ControlsOutput controls = new ControlsOutput().withJump(timeElapsed <= this.holdTime).withThrottle(0.02);
		if(timeElapsed > this.holdTime + ORIENT_DELAY) controls.withOrient(AirControl.getRollPitchYaw(packet.car, packet.car.velocity.withZ(0)));
		return controls;
	}

}
