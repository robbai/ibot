package ibot.bot.step.steps.jump;

import ibot.bot.input.Bundle;
import ibot.bot.utils.rl.Constants;
import ibot.input.Car;
import ibot.output.Controls;
import ibot.output.Output;

public class DoubleJumpStep extends JumpStep {

	public static final double DOUBLE_JUMP_DELAY = (Constants.DT * 3);

	public DoubleJumpStep(Bundle bundle){
		super(bundle, Constants.MAX_JUMP_HOLD_TIME);
	}

	@Override
	public Output getOutput(){
		Car car = this.bundle.packet.car;

		// First jump.
		if(!this.firstOutputTime.isPresent())
			return super.getOutput();
		double timeElapsed = (car.time - this.firstOutputTime.getAsDouble());
		if(timeElapsed < this.holdTime)
			return super.getOutput();

		// Second jump.
		this.setFinished(car.hasDoubleJumped);
		return new Controls().withJump(timeElapsed > this.holdTime + DOUBLE_JUMP_DELAY);
	}

}
