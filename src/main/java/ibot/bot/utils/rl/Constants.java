package ibot.bot.utils.rl;

import ibot.bot.utils.StaticClass;

public class Constants extends StaticClass {

	public static final double DT = (1D / 120);

	public static final double BALL_RADIUS = 92.75;

	public static final double BOOST_GROUND_ACCELERATION = (911 + (2 / 3));
	public static final double BOOST_AIR_ACCELERATION = 1060;
	public static final double COAST_ACCELERATION = 525;
	public static final double BRAKE_ACCELERATION = 3500;
	public static final double MAX_CAR_THROTTLE_VELOCITY = 1410;
	public static final double MAX_CAR_VELOCITY = 2300;
	public static final double SUPERSONIC_VELOCITY = 2200;
	public static final double CAR_HEIGHT = 17.049999237060547;
	public static final double BOOST_USAGE = 33.3;
	public static final double DODGE_IMPULSE = 500;
	public static final double MAX_DODGE_DELAY = 1.5;
	public static final double JUMP_IMPULSE = 300;
	public static final double JUMP_ACCELERATION = 1400;
	public static final double THROTTLE_AIR_ACCELERATION = (200D / 3);
	public static final double MAX_JUMP_HOLD_TIME = 0.2;
	public static final double MIN_BOOST_TIME = 0.1;
	public static final double COAST_THRESHOLD = 0.012; // https://discordapp.com/channels/348658686962696195/535605770436345857/631459919786278923

	public static final double CEILING = 2044;
	public static final double PITCH_LENGTH_HOOPS = 3586; // Half.
	public static final double PITCH_WIDTH_HOOPS = 2966.67; // Half.
	public static final double PITCH_LENGTH_SOCCAR = 5120; // Half.
	public static final double PITCH_WIDTH_SOCCAR = 4096; // Half.
	public static final double PITCH_CORNER_WIDTH_SOCCAR = 590;
	/**
	 * https://github.com/tarehart/ReliefBot/blob/4a64239aee5da8b0957fb941c536ad82c4875ce0/src/main/java/tarehart/rlbot/physics/ArenaModel.kt#L38
	 */
	public static final double GOAL_WIDTH = 892.755; // Half.
	public static final double GOAL_HEIGHT = 642.775;

}
