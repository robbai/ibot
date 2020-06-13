package ibot.bot.step.steps.meta;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import ibot.bot.input.Bundle;
import ibot.bot.stack.PopStack;
import ibot.bot.step.Step;
import ibot.output.Output;

public class ChainStep extends Step {

	private List<Step> steps;

	public ChainStep(Bundle bundle, Step... steps){
		super(bundle);
		this.steps = Arrays.stream(steps).filter(step -> step != null).collect(Collectors.toList());
	}

	@Override
	public Output getOutput(){
		while(true){
			if(this.steps.size() == 0)
				return new PopStack();
			Output output = this.steps.get(0);
			this.steps.remove(0);
//			if(output instanceof PopStack || (this.steps.get(0).isFinished() && output instanceof Controls)) continue;
			return output;
		}
	}

	@Override
	public int getPriority(){
		return 0;
	}

}
