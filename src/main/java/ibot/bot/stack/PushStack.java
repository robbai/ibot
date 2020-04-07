package ibot.bot.stack;

import ibot.bot.step.Step;

/**
 * Adds a new step.
 */
public class PushStack extends StackAction {

	public final Step step;

	public PushStack(Step step){
		this.step = step;
	}

}
