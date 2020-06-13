package ibot.bot.step.steps.meta;

import java.util.function.Function;

import ibot.bot.input.Bundle;
import ibot.bot.stack.PopStack;
import ibot.bot.step.Step;
import ibot.output.Output;

public class ShouldStep extends Step {

	private boolean ran = false;
	private final Step step;
	private final Function<Bundle, Boolean> condition;

	public ShouldStep(Bundle bundle, Step step, Function<Bundle, Boolean> condition){
		super(bundle);
		this.step = step;
		this.condition = condition;
	}

	@Override
	public Output getOutput(){
		if(this.ran)
			return new PopStack();
		this.ran = true;
		if(this.condition.apply(this.bundle)){
			return this.step;
		}
		return new PopStack();
	}

	@Override
	public int getPriority(){
		return 0;
	}

}
