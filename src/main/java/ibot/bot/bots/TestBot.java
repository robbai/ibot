package ibot.bot.bots;

import ibot.bot.step.Step;
import ibot.bot.step.steps.IdleStep;

public class TestBot extends ABot {

	public TestBot(int index, int team){
		super(index, team);
	}

	@Override
	protected Step fallbackStep(){
		return new IdleStep(this.bundle);
	}

}
