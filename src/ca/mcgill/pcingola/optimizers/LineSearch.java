package ca.mcgill.pcingola.optimizers;

import ca.mcgill.pcingola.optimizers.exceptions.LineSearchException;

/**
 *An abstract class of the common function calls in all the Line-Search procedure.
 *
 *If the simulation is at point Xk,and a descent direction is Pk, findStepLength gives a scalar Ak,
 *that says how much to travel along Pk. So that Xk+1 = Xk + Ak*Pk. The coordinates of the initial point (Xk)
 *should be given in the first column of the argument 'coordinates'. The direction of search (Pk)
 *(direction + magnitude) from the initial point sould be given in the second column of 'coordinates'.
 **/

public abstract class LineSearch {

	protected boolean debug = false;

	protected Energy energy;

	public LineSearch(Energy energy) {
		this.energy = energy;
	}

	public abstract double findStepLength() throws LineSearchException;

	public void setDebug(boolean debug) {
		this.debug = debug;
	}

	@Override
	public String toString() {
		return this.getClass().getSimpleName() + " : " + energy;
	}

}
