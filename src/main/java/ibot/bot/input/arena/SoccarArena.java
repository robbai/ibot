package ibot.bot.input.arena;

import ibot.bot.utils.maths.Plane;
import ibot.bot.utils.rl.Constants;
import ibot.bot.utils.rl.Mode;
import ibot.vectors.Vector3;

public class SoccarArena extends Arena {

	/**
	 * https://github.com/tarehart/ReliefBot/blob/4a64239aee5da8b0957fb941c536ad82c4875ce0/src/main/java/tarehart/rlbot/physics/ArenaModel.kt
	 */

	public SoccarArena(){
		super(Mode.SOCCAR, 10);

		this.width = Constants.PITCH_WIDTH_SOCCAR;
		this.length = Constants.PITCH_LENGTH_SOCCAR;
		this.height = Constants.CEILING;

		// Floor.
		this.planes.add(new Plane(Vector3.Z, new Vector3()));

		// Side walls.
		this.planes.add(new Plane(Vector3.X, Vector3.X.scale(-Constants.PITCH_WIDTH_SOCCAR)));
		this.planes.add(new Plane(Vector3.X.scale(-1), Vector3.X.scale(Constants.PITCH_WIDTH_SOCCAR)));

		// Ceiling.
		this.planes.add(new Plane(Vector3.Z.scale(-1), Vector3.Z.scale(Constants.CEILING)));

		// Corners.
		this.planes.add(new Plane(new Vector3(1, 1, 0),
				new Vector3(-Constants.PITCH_WIDTH_SOCCAR + Constants.PITCH_CORNER_WIDTH_SOCCAR,
						-Constants.PITCH_LENGTH_SOCCAR + Constants.PITCH_CORNER_WIDTH_SOCCAR, 0)));
		this.planes.add(new Plane(new Vector3(-1, 1, 0),
				new Vector3(Constants.PITCH_WIDTH_SOCCAR - Constants.PITCH_CORNER_WIDTH_SOCCAR,
						-Constants.PITCH_LENGTH_SOCCAR + Constants.PITCH_CORNER_WIDTH_SOCCAR, 0)));
		this.planes.add(new Plane(new Vector3(1, -1, 0),
				new Vector3(-Constants.PITCH_WIDTH_SOCCAR + Constants.PITCH_CORNER_WIDTH_SOCCAR,
						Constants.PITCH_LENGTH_SOCCAR - Constants.PITCH_CORNER_WIDTH_SOCCAR, 0)));
		this.planes.add(new Plane(new Vector3(-1, -1, 0),
				new Vector3(Constants.PITCH_WIDTH_SOCCAR - Constants.PITCH_CORNER_WIDTH_SOCCAR,
						Constants.PITCH_LENGTH_SOCCAR - Constants.PITCH_CORNER_WIDTH_SOCCAR, 0)));

		// Back walls.
		this.planes.add(new Plane(Vector3.Y, Vector3.Y.scale(-Constants.PITCH_LENGTH_SOCCAR)));
		this.planes.add(new Plane(Vector3.Y.scale(-1), Vector3.Y.scale(Constants.PITCH_LENGTH_SOCCAR)));
	}

}
