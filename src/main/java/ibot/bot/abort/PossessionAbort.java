package ibot.bot.abort;

import ibot.bot.input.Bundle;
import ibot.bot.intercept.Intercept;
import ibot.bot.utils.maths.MathsUtils;
import ibot.input.Car;
import ibot.vectors.Vector2;

public class PossessionAbort extends AbortCondition {

	private final double possession, factor;

	public boolean inverted = false;

	public PossessionAbort(Bundle bundle, double possession, double factor){
		super(bundle);
		this.possession = possession;
		this.factor = factor;
	}

	public PossessionAbort(Bundle bundle, double possession){
		this(bundle, possession, -1);
	}

	@Override
	public boolean shouldAbort(){
		// Factor.
		Car car = this.bundle.packet.car;
		Intercept intercept = this.bundle.info.groundIntercept;
		Vector2 offset = MathsUtils.local(car.orientation, intercept.getOffset()).flatten().normalised();
		double factor = -offset.dot(MathsUtils.local(car, intercept.position).flatten().normalised());
		if(factor < this.factor)
			return false;

		return this.inverted ? (bundle.info.possession < this.possession) : (bundle.info.possession > this.possession);
	}

}
