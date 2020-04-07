package ibot.bot.step.steps;

import ibot.bot.input.Bundle;
import ibot.bot.step.Priority;
import ibot.bot.step.Step;
import ibot.output.Controls;
import ibot.output.Output;

public class IdleStep extends Step {

	public IdleStep(Bundle bundle){
		super(bundle);
	}

	@Override
	public Output getOutput(){
		return new Controls();
	}

	@Override
	public int getPriority(){
		return Priority.IDLE;
	}

}
