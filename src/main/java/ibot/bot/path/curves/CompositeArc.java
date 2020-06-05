package ibot.bot.path.curves;

import ibot.bot.path.Curve;
import ibot.bot.physics.Car1D;
import ibot.bot.physics.DrivePhysics;
import ibot.bot.utils.Pair;
import ibot.bot.utils.rl.Constants;
import ibot.input.Car;
import ibot.vectors.Vector2;

/**
 * https://github.com/samuelpmish/RLUtilities/blob/7c645db1c7450ee793510c3acbb8bc61f8825b74/src/simulation/composite_arc.cc
 */
public class CompositeArc extends Curve {

	private static final boolean RESCALE = false;

	private static final double[] SIGNS = new double[] { 1, -1 };

	public Vector2 p1, p2, t1, t2, n1, n2, o1, o2, q1, q2;
	private double length, r1, r2, phi1, phi2;

	private double[] L = new double[5];

	private CompositeArc(double _L0, Vector2 _p1, Vector2 _t1, double _r1, double _L4, Vector2 _p2, Vector2 _t2,
			double _r2){
		this.p1 = _p1.plus(_t1.scale(_L0));
		this.t1 = new Vector2(_t2);
		this.n1 = this.t1.cross();
		this.r1 = _r1;
		this.o1 = this.p1.plus(this.n1.scale(this.r1));

		this.p2 = _p2.minus(_t2.scale(_L0));
		this.t2 = new Vector2(_t2);
		this.n2 = this.t2.cross();
		this.r2 = _r2;
		this.o2 = this.p2.plus(this.n2.scale(this.r2));

		Vector2 oDelta = this.o2.minus(this.o1);

		double sign = -Math.signum(this.r1) * Math.signum(this.r2);
		double R = Math.abs(this.r1) + sign * Math.abs(this.r2);
		double o1o2 = oDelta.magnitude();

		if(RESCALE){
			double beta = 0.97D;
			if((Math.pow(R, 2) / Math.pow(o1o2, 2)) > beta){
				Vector2 pDelta = this.p2.minus(this.p1);
				Vector2 nDelta = this.n2.scale(this.r2).minus(this.n1.scale(this.r1));

				double a = beta * nDelta.dot(nDelta) - Math.pow(R, 2);
				double b = 2D * beta * nDelta.dot(pDelta);
				double c = beta * pDelta.dot(pDelta);

				double alpha = (-b - Math.sqrt(Math.pow(b, 2) - 4D * a * c)) / (2D * a);

				this.r1 *= alpha;
				this.r2 *= alpha;
				R *= alpha;

				this.o1 = this.p1.plus(this.n1.scale(this.r1));
				this.o2 = this.p2.plus(this.n2.scale(this.r2));

				oDelta = this.o2.minus(this.o1);
				o1o2 = oDelta.magnitude();
			}
		}

		Vector2 e1 = oDelta.normalised();
		Vector2 e2 = e1.cross().scale(-Math.signum(this.r1));

		double H = Math.sqrt(Math.pow(o1o2, 2) - Math.pow(R, 2));

		this.q1 = this.o1.plus((e1.scale(R / o1o2).plus(e2.scale(H / o1o2))).scale(Math.abs(this.r1)));
		this.q2 = this.o2.minus((e1.scale(R / o1o2).plus(e2.scale(H / o1o2)).scale(Math.abs(this.r2) * sign)));

		Vector2 pq1 = this.q1.minus(this.p1).normalised();
		this.phi1 = 2D * Math.signum(pq1.dot(this.t1)) * Math.asin(Math.abs(pq1.dot(this.n1)));
		if(this.phi1 < 0)
			this.phi1 += 2D * Math.PI;

		Vector2 pq2 = this.q2.minus(this.p2).normalised();
		this.phi2 = -2D * Math.signum(pq2.dot(this.t2)) * Math.asin(Math.abs(pq2.dot(this.n2)));
		if(this.phi2 < 0)
			this.phi2 += 2D * Math.PI;

		L[0] = _L0;
		L[1] = this.phi1 * Math.abs(this.r1);
		L[2] = this.q1.distance(this.q2);
		L[3] = this.phi2 * Math.abs(this.r2);
		L[4] = _L4;
		length = L[0] + L[1] + L[2] + L[3] + L[4];
	}

	public static CompositeArc create(Car car, Vector2 ballPosition, Vector2 goal, double finalVelocity, double L0,
			double L4){
		// Sanitise.
		L0 = Math.max(1, Math.abs(L0));
		L4 = Math.max(1, Math.abs(L4));

		Vector2 carDirection = car.orientation.forward.flatten().normalised();
		Vector2 carPosition = car.position.flatten();

		Vector2 goalDirection = goal.minus(ballPosition).normalised();
		double playerTurnRadius = DrivePhysics
				.getTurnRadius(Math.max(Constants.MAX_CAR_THROTTLE_VELOCITY, car.forwardVelocityAbs)),
				ballTurnRadius = DrivePhysics.getTurnRadius(finalVelocity);

		// Find the shortest composite-arc based on its length.
		CompositeArc shortestCompositeArc = null;
		double shortestLength = Double.MAX_VALUE;
		for(double playerTurn : SIGNS){
			for(double ballTurn : SIGNS){
				CompositeArc compositeArc;
				try{
					compositeArc = new CompositeArc(L0, carPosition, carDirection, playerTurn * playerTurnRadius, L4,
							ballPosition, goalDirection, ballTurn * ballTurnRadius);
				}catch(Exception e){
					compositeArc = null;
					e.printStackTrace();
				}

				if(compositeArc != null && Double.isFinite(compositeArc.length)){
					if(compositeArc.length < shortestLength){
						shortestCompositeArc = compositeArc;
						shortestLength = compositeArc.length;
					}
				}
			}
		}

		return shortestCompositeArc;
	}

	public static CompositeArc create(Car car, Vector2 ball, Vector2 goal, double L0, double L4){
		return create(car, ball, goal, new Car1D(car).stepMaxVelocity(1, true).getVelocity(), L0, L4);
	}

	public Vector2[] discretise(int n){
		Vector2 r;
		Pair<Vector2, Vector2> Q; // Matrix 2x2

		double ds = (this.length / n);

		int[] segments = new int[5];
		int capacity = 1;
		for(int i = 0; i < 5; i++){
			segments[i] = (int)Math.ceil(this.L[i] / ds);
			capacity += segments[i];
		}

		Vector2[] points = new Vector2[capacity];

		int id = 0;

		Vector2 m1 = p1.minus(t1.scale(L[0]));
		Vector2 m2 = p2.plus(t2.scale(L[4]));

		ds = L[0] / segments[0];
		for(int i = 0; i < segments[0]; i++){
			points[id] = m1.lerp(p1, (double)i / segments[0]);
			id++;
		}

		ds = L[1] / segments[1];
		r = p1.minus(o1);
		Q = rotation(-Math.signum(r1) * phi1 / segments[1]);
		for(int i = 0; i < segments[1]; i++){
			points[id] = o1.plus(r);
			id++;
			r = dot(Q, r);
		}

		ds = L[2] / segments[2];
		for(int i = 0; i < segments[2]; i++){
			points[id] = q1.lerp(q2, (double)i / segments[2]);
			id++;
		}

		ds = L[3] / segments[3];
		r = q2.minus(o2);
		Q = rotation(-Math.signum(r2) * phi2 / segments[3]);
		for(int i = 0; i < segments[3]; i++){
			points[id] = o2.plus(r);
			id++;
			r = dot(Q, r);
		}

		ds = L[4] / segments[4];
		for(int i = 0; i <= segments[4]; i++){
			points[id] = p2.lerp(m2, (double)i / segments[4]);
			id++;
		}

		return points;
	}

	/**
	 * Matrix 2x2
	 */
	private static Pair<Vector2, Vector2> rotation(double theta){
		return new Pair<Vector2, Vector2>(new Vector2(Math.cos(theta), -Math.sin(theta)),
				new Vector2(Math.sin(theta), Math.cos(theta)));
	}

	/**
	 * Matrix 2x2 dot product.
	 */
	private static Vector2 dot(Pair<Vector2, Vector2> mat, Vector2 vec){
		return new Vector2(mat.getOne().dot(vec), mat.getTwo().dot(vec));
	}

	public double getLength(){
		return this.length;
	}

	public double getL(int i){
		return L[i];
	}

	public double getR1(){
		return r1;
	}

	public double getR2(){
		return r2;
	}

	@Override
	public Vector2 T(double t){
		// TODO
		return null;
	}

}
