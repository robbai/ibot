package ibot.bot.stack;

import ibot.bot.step.Step;

/**
 * Swaps the top step with a new step.
 */
public class SwapStack extends PushStack {

	public SwapStack(Step step){
		super(step);
	}

}
