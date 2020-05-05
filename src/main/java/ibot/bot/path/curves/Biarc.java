package ibot.bot.path.curves;

import java.util.OptionalDouble;

import ibot.bot.path.Curve;
import ibot.bot.utils.Circle;
import ibot.bot.utils.MathsUtils;
import ibot.bot.utils.Pair;
import ibot.vectors.Vector2;

/**
 * http://www.ryanjuckett.com/programming/biarc-interpolation/ I thank
 * whatisaphone for linking this in his bot's README
 */

public class Biarc extends Curve {

	private final Vector2 start, startDir, end, endDir, connection;
	private final double dOne, arcOneLength, arcTwoLength;
	private final OptionalDouble dTwo;
	private final Circle circleOne, circleTwo;

	public Biarc(Vector2 start, Vector2 startDir, Vector2 end, Vector2 endDir){
		this.start = start;
		this.startDir = startDir.normalised();
		this.end = end;
		this.endDir = endDir.normalised();

		this.dOne = this.chooseD();
		this.dTwo = this.solveD(this.dOne);

		this.connection = this.findConnection();

		Pair<Circle, Circle> circles = this.findCircles();
		this.circleOne = circles.getOne();
		this.circleTwo = circles.getTwo();

		this.arcOneLength = this.circleOne.getSectorCircumference(
				this.start.minus(this.circleOne.getCentre()).angle(this.connection.minus(this.circleOne.getCentre())));
		this.arcTwoLength = this.circleTwo.getSectorCircumference(
				this.end.minus(this.circleTwo.getCentre()).angle(this.connection.minus(this.circleTwo.getCentre())));
	}

	private double chooseD(){
		Vector2 v = this.end.minus(this.start);
		Vector2 t = this.startDir.plus(this.endDir);

		double denominator = 2 * (1 - this.startDir.dot(this.endDir));
		if(Math.abs(denominator) > MathsUtils.EPSILON){
			double square = Math.pow(v.dot(t), 2) + denominator * v.dot(v);
			return (-v.dot(t) + Math.sqrt(square)) / denominator;
		}

		// Note: I didn't actually deal with case 3, since it's so rare to occur in
		// these conditions
		return v.dot(v) / (4 * v.dot(this.endDir));
	}

	private OptionalDouble solveD(double otherD){
		Vector2 v = this.end.minus(this.start);

		double denominator = v.dot(this.endDir) - otherD * (this.startDir.dot(this.endDir) - 1);
		if(Math.abs(denominator) > MathsUtils.EPSILON){
			return OptionalDouble.of((0.5 * v.dot(v) - otherD * v.dot(this.startDir)) / denominator);
		}

		return OptionalDouble.empty();
	}

	private Pair<Circle, Circle> findCircles(){
		Vector2 nOne = this.startDir.cross();
		double cOneRadius = this.connection.minus(this.start).dot(this.connection.minus(this.start))
				/ (nOne.scale(2).dot(this.connection.minus(this.start)));
		Vector2 cOneCentre = this.start.plus(nOne.scale(cOneRadius));

		Vector2 nTwo = this.endDir.cross();
		double cTwoRadius = this.connection.minus(this.end).dot(this.connection.minus(this.end))
				/ (nTwo.scale(2).dot(this.connection.minus(this.end)));
		Vector2 cTwoCentre = this.end.plus(nTwo.scale(cTwoRadius));

		Circle cOne = new Circle(cOneCentre, cOneRadius);
		Circle cTwo = new Circle(cTwoCentre, cTwoRadius);

		return new Pair<Circle, Circle>(cOne, cTwo);
	}

	private Vector2 findConnection(){
		if(this.dTwo.isPresent()){
			double dTwoVal = this.dTwo.getAsDouble();
			return (this.start.plus(this.startDir.scale(dOne))).scale(dTwoVal / (this.dOne + dTwoVal))
					.plus((this.end.minus(this.endDir.scale(dTwoVal))).scale(this.dOne / (this.dOne + dTwoVal)));
		}

		Vector2 v = this.end.minus(this.start);
		return this.start.plus(this.startDir.scale(dOne))
				.plus(this.endDir.scale(v.dot(this.endDir) - this.startDir.scale(this.dOne).dot(this.endDir)));
	}

	@Override
	public Vector2 T(double t){
		t = MathsUtils.clamp(t, 0, 1);

		double arcDivide = (this.arcOneLength / this.getLength());

		if(t < arcDivide){
			double sector = (t / arcDivide);
			Vector2 toStart = this.start.minus(this.circleOne.getCentre());
			Vector2 toConnection = this.connection.minus(this.circleOne.getCentre());
			return this.circleOne.getCentre().plus(toStart.rotate(toStart.correctionAngle(toConnection) * sector));
		}else{
			double sector = ((t - arcDivide) / (1 - arcDivide));
			Vector2 toEnd = this.end.minus(this.circleTwo.getCentre());
			Vector2 toConnection = this.connection.minus(this.circleTwo.getCentre());
			return this.circleTwo.getCentre().plus(toConnection.rotate(toConnection.correctionAngle(toEnd) * sector));
		}
	}

	@Override
	public double getLength(){
		return this.arcOneLength + this.arcTwoLength;
	}

	public Pair<Double, Double> getRadii(){
		return new Pair<Double, Double>(this.circleOne.getRadius(), this.circleTwo.getRadius());
	}

}
