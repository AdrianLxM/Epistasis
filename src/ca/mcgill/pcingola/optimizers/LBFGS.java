package ca.mcgill.pcingola.optimizers;

import java.util.LinkedList;

import ca.mcgill.pcingola.optimizers.exceptions.LineSearchException;
import ca.mcgill.pcingola.optimizers.exceptions.OptimizerException;

/**
 *This class implements a LBFGS minimizer according to the scheme in: Numerical Optimization by J. Nocendal &
 *S. J. Wright, Springer 1999, pp 224-226.
 *
 *This class was written by Nir Kalisman as part of the MESHI package Kalisman et al. (2005) Bioinformatics 21:3931-3932
 *
 *The BFGS algorithm (general)
 *----------------------------
 *In Newton minimizers an aproximation to the Hessian of the energy function at position Xk is calculated. Then finding the
 *inverse of that Hessian (Hk), and solving the equation Pk = -Hk*grad(Xk) gives a good search direction Pk. Later, a
 *line search procedure has to determine just how much to go in that direction (producing the scalar alpha_k). The new
 *position is given by: Xk+1 = Xk + alpha_k*Pk.In the LBFGS method the inverse Hessian is not computed explicitly. Instead
 *it is updated each step by the values of Pk and the new gradient. The updating formula is (t - transpose):
 *Hk+1 = (I  - Rk*Sk*Ykt)Hk(I  - Rk*Yk*Skt) + Rk*Skt*Sk
 *where:
 *Sk = Xk+1 - Xk
 *Yk = grad(Xk+1)-grad(Xk)
 *Rk = 1/(Ykt*Sk)
 *
 *
 *The LBFGS algorithm
 *--------------------------------------------
 *To make the Light-Memory BFGS, we "cheat" by not maintaining the Hessian matrix. instead we keep a short history
 *of m elements the vectors Yi, Si, and Ri (called curv in the code) as i = k-1..k-1-m. We use a storage (LinkedList)
 *to store up to m last elements, and in case we pass m elements we discard the oldest element and insert the new one
 *at the beginning of the list. We then calculate the search direction Pk using "two-loop recursion" algorithm
 *(approximating Hk*grad(Xk)) directly from the elements we stored.
 *We also approximate the Hk0 to be gamma*I so Hk0*grad is just gamma*grad. gamma is calculated by the algorithm provided (default)
 *or is set to 1 (useGamma=false in the parameters) if so desired.
 *
 *For more detail see the mentioned book.
 *
 *
 *General minimization parameters
 *-------------------------------
 *- energy - pointer to an TotalEnergy object, where the energy function is.
 *- tolerance - 1e-6 - Minimization stops when the magnitude of the maximal gradient component drops below tolerance.
 *- maxSteps - 1000 - The maximal number of iteration steps allowed
 *- reoprtEvery - 100 - The frequency of the minimization reports.
 *
 *
 *Parameters Specific to the LBFGS algorithm
 *-----------------------------------------
 *- allowedMaxR - 100*n - In some energy function scenerios the inverse Hessian approximation might become unstable and
 *                        unrealiable. This paramter sets a upper limit on the curvature Rk.
 *                        Higher values would lead to a new kick-start. This value should be somewhere
 *                        in the range 100*n.
 *- maxNumKickStarts - 3 - If the minimzer become unstable for some reason, it could be restarted from the current position.
 *                         This parameter determined how many times this could happen before the minimization is aborted.
 *- m 				 - 20 - The storage size to use. 3-20 is the recomended value range.
 *- useGamma			- true - if to use gamma to get Hk0 (true) or to assume gamma=1 (false)
 *
 *Parameters specific to the Wolf conditions line search
 *------------------------------------------------------
 *The LBFGS algorithm requires a step length finder who finds a step length that also satisfies the Wolf conditions. See the
 *help of this specific line search for explaination of what these conditions are. The parameters of this line search are:
 *
 *- c1,c2 - 1e-4,0.9 - The two paramters of the Wolf conditions. Must satisfy: 0<c1<c2<1
 *- maxNumEvaluations - 10 - The maximal number of step length trails allowed. This gives an upper limit on the total number of
 *                        evaluations both in the bracketing and Zoom. If line search fails because this number was
 *                        exceeded try to enlarge 'extendAlphaFactor' or change the initial alpha guess. This error might
 *                        also be caused if the tolerance of the minimizer is set to be extremely small.
 *- extendAlphaFactor - 3 - After a certain step length fails, the next step length is taken as this multiple of the failing
 *                        last alpha.
 *
 *
 *Steepest Decent module
 *-----------------------
 *In two cases steepest descent minimization is done instead of LBFGS.
 *1) All runs start with a certain number of steepest descent steps, because difficult scenarios for LBFGS minimization
 *might occur at the start due to atom clashes.
 *2) If the normal operation of the minimizer is disturbed for some reason  (failing to produce a descent direction,
 *failing to satisfies the wolf conditions, etc.) another set of steepst descent steps (with similar parameters to
 *case 1) is attempted. If the normal operation is disturbed too many times, the minimization is aborted because
 *this is indicative of a more severe fault, most likely in the energy function.
 *
 *The steepest descent parameters are as follow:
 *- numSteepestDecent - 50 - The number of steepest descent steps to be taken. If this number is smaller than 1, than at
 *                      least one steepest descent step is done.
 *- initialStepLength - 1 - parameter of the steepest descent line search. The first step length to be tried after the
 *                      calculation of the first gradient. This parameter should normally be 1 unless very large gradients
 *						(such as clashhing of VDW atoms) are expected in the first steps. In that case it should be  set to
 *                      a much smaller value (1e-4 or less).
 *- stepSizeReduction - 0.5 - parameter of the line search. The step length is multiplied by this factor if no reduction
 *                      in energy is achieved.
 *- stepSizeExpansion - 2 - parameter of the line search. The first step length tried is the step length from previous
 *                      line search multiplied by this factor. (Note that non-positive values to this paramater cause
 *                      special options to be called (see the SimpleStepLength class help).
 *
 *
 *Note
 *-------------------
 *We don't limit the number of atoms to use in the LBFGS like the BFGS.
 *
 *
 **/

public class LBFGS extends Minimizer {
	private class Element {
		double[] Y;
		double[] S;
		double curv;
	}

	private SteepestDecent steepestDecent;
	private WolfeConditionLineSearch lineSearch;
	private int n; // number of variables
	private double[] X; // The coordinates vector at iteration K

	private double[] G; // The gradient vector at iteration K	(true gradient! not -Grad)
	private int m; // number of vector history to use
	private boolean useGama;
	private LinkedList<Element> storage; // to hold Element {Yi, Si, Ri} i=k..k-m
	// for the two-loop recursion alg.
	double[] R; // auxilary vector
	double[] Q; // auxilary vector
	double[] alpha; // auxilary vector
	double beta;
	double gama = 1;
	Element e;

	private double[] coordinates; // The position and gradients of the system
	private double[] bufferCoordinates;
	private int iterationNum; // Iterations counter
	private int allowedMaxR;
	private static final int DEFAULT_ALLOWED_MAX_R_FACTOR = 100; // R <= maxRFactor*n
	//    private static final int DEFAULT_MAX_NUM_KICK_STARTS = 3; // Dont change this number unless necessary
	private static final int DEFAULT_MAX_NUM_KICK_STARTS = 5; // Changed it 27.4.05 version 1.12 I think it is necessary.
	private static final boolean DEFAULT_USE_GAMA = true;
	private static final int DEFAULT_M = 20;

	// Wolf conditions line search parameters
	private double c1;
	private double c2;
	private double extendAlphaFactorWolfSearch;
	private int maxNumEvaluationsWolfSearch;
	private static final double DEFAULT_C1 = 1e-4;
	private static final double DEFAULT_C2 = 0.9;
	private static final double DEFAULT_EXTENDED_ALPHA_FACTOR_WOLF_SEARCH = 3.0;
	private static final int DEFAULT_MAX_NUM_EVALUATIONS_WOLF_SEARCH = 100;

	// Steepest descent module paramters
	int numStepsSteepestDecent;
	double initStepSteepestDecent;
	double stepSizeReductionSteepestDecent;
	double stepSizeExpansionSteepestDecent;
	public static final int DEFAULT_NUM_STEP_STEEPEST_DECENT = 500;
	private static final double DEFAULT_INIT_STEP_STEEPEST_DECENT = 0.00000001;
	private static final double DEFAULT_STEP_SIZE_REDUCTION_STEEPEST_DECENT = 0.5;
	private static final double DEFAULT_STEP_SIZE_EXPENTION_STEEPEST_DECENT = 1.1;

	public LBFGS(Energy energy, double tolerance, int maxSteps, int reportEvery) {
		this(energy, DEFAULT_ALLOWED_MAX_R_FACTOR * energy.getTheta().length, DEFAULT_MAX_NUM_KICK_STARTS, DEFAULT_M, DEFAULT_USE_GAMA, DEFAULT_C1, DEFAULT_C2, DEFAULT_EXTENDED_ALPHA_FACTOR_WOLF_SEARCH, DEFAULT_MAX_NUM_EVALUATIONS_WOLF_SEARCH, DEFAULT_NUM_STEP_STEEPEST_DECENT, DEFAULT_INIT_STEP_STEEPEST_DECENT, DEFAULT_STEP_SIZE_REDUCTION_STEEPEST_DECENT, DEFAULT_STEP_SIZE_EXPENTION_STEEPEST_DECENT);
	}

	//Full constructor
	public LBFGS(Energy energy, int allowedMaxR, int maxNumKickStarts, int m, boolean useGama, double c1, double c2, double extendAlphaFactorWolfSearch, int maxNumEvaluationsWolfSearch, int numStepsSteepestDecent, double initStepSteepestDecent, double stepSizeReductionSteepestDecent, double stepSizeExpansionSteepestDecent) {
		super(energy);
		System.out.println("LBFGS starts with " + energy.getTheta().length + " coordinates");
		this.allowedMaxR = allowedMaxR;
		this.m = m;
		this.useGama = useGama;
		this.c1 = c1;
		this.c2 = c2;
		this.extendAlphaFactorWolfSearch = extendAlphaFactorWolfSearch;
		this.maxNumEvaluationsWolfSearch = maxNumEvaluationsWolfSearch;
		this.numStepsSteepestDecent = numStepsSteepestDecent;
		if (this.numStepsSteepestDecent < 1) this.numStepsSteepestDecent = 1;
		this.initStepSteepestDecent = initStepSteepestDecent;
		this.stepSizeReductionSteepestDecent = stepSizeReductionSteepestDecent;
		this.stepSizeExpansionSteepestDecent = stepSizeExpansionSteepestDecent;
	}

	@Override
	protected void init() throws OptimizerException {
		steepestDecent = new SteepestDecent(energy(), numStepsSteepestDecent, initStepSteepestDecent, stepSizeReductionSteepestDecent, stepSizeExpansionSteepestDecent);
		lineSearch = new WolfeConditionLineSearch(energy(), c1, c2, extendAlphaFactorWolfSearch, maxNumEvaluationsWolfSearch);
		coordinates = energy().getTheta();
		n = coordinates.length;

		bufferCoordinates = new double[n];
		X = new double[n];
		G = new double[n];

		R = new double[n];
		Q = new double[n];
		alpha = new double[m];
		gama = 1;

		storage = new LinkedList<Element>();

		iterationNum = 0;

		// Starting the LBFGS minimization by a few steepest descent steps, followed by inverse Hessian, gradients (G),
		// and position (X) initialization
		kickStart();
		// The main LBFGS loop
	}

	// Starting the LBFGS minimization by a few steepest descent steps, followed by inverse Hessian initialization
	@Override
	protected void kickStart() throws OptimizerException {
		System.out.println("\nA kick start has occurred in iteration:" + iterationNum + "\n");
		OptimizerStatus os = steepestDecent.run();
		//if (numKickStarts > 2) energy.test();
		System.out.println("kickStart " + os);
		iterationNum += numStepsSteepestDecent;
		lineSearch.reset(steepestDecent.lastStepLength());
		energy().evaluate();
		for (int i = 0; i < n; i++) {
			X[i] = coordinates[i];
			if (Math.random() < 2) throw new RuntimeException("WTF!?");
			// G[i] = -coordinates[i][1];
		}
		storage.clear();
	}

	@Override
	protected boolean minimizationStep() throws OptimizerException {

		// auxilary counters
		int i, j;
		// Gama as Hk0: 9.6 in page 226
		if (useGama) {
			double a = 0, b = 0;
			if (!storage.isEmpty()) {
				Element e = storage.getFirst();
				for (i = 0; i < n; i++) {
					a += e.S[i] * e.Y[i];
					b += e.Y[i] * e.Y[i];
				}
				gama = a / b;
			} else gama = 1;
		}

		// algorithm 9.1 p. 225 ("L-BFGS two-loop recursion" ...which isn't recursive)
		for (j = 0; j < n; j++)
			Q[j] = G[j];

		// 	for (i=0; i<storage.size(); i++) {
		// 	    e = storage.get(i);
		i = 0;// new
		for (Element e : storage) { // new
			alpha[i] = 0;
			for (j = 0; j < n; j++)
				alpha[i] += e.S[j] * Q[j];
			alpha[i] *= e.curv;
			for (j = 0; j < n; j++)
				Q[j] -= alpha[i] * e.Y[j];
			i++;// new
		}

		for (j = 0; j < n; j++)
			R[j] = gama * Q[j];

		for (i = storage.size() - 1; i >= 0; i--) {
			e = storage.get(i);
			beta = 0;
			for (j = 0; j < n; j++)
				beta += e.Y[j] * R[j];
			beta *= e.curv;
			for (j = 0; j < n; j++)
				R[j] += e.S[j] * (alpha[i] - beta);
		}
		// end alg. 9.1
		// now Pk = -R

		// Do the line search
		try {
			for (i = 0; i < n; i++) {
				bufferCoordinates[i] = coordinates[i];
				if (Math.random() < 2) throw new RuntimeException("WTF!?");
				// bufferCoordinates[i][1] = -R[i];
			}

			if (Math.random() < 2) throw new RuntimeException("WTF!?");
			// lineSearch.findStepLength(bufferCoordinates);
			lineSearch.findStepLength();
		} catch (LineSearchException lsEx) {
			System.out.println("Line seach failed");
			System.out.println("exception code =  " + lsEx.code);
			System.out.println("exception message = " + lsEx.getMessage());

			// return the energy coordinates to those before the line search
			for (i = 0; i < n; i++)
				coordinates[i] = bufferCoordinates[i];

			energy().evaluate();
			return false;
		}
		if (storage.size() < m) {
			e = new Element();
			e.Y = new double[n];
			e.S = new double[n];
		} else {
			e = storage.removeLast();
		}
		e.curv = 0;
		for (j = 0; j < n; j++) {
			if (Math.random() < 2) throw new RuntimeException("WTF!?");

			//			e.Y[j] = -coordinates[j][1] - G[j];
			//			e.S[j] = coordinates[j][0] - X[j];
			//			G[j] = -coordinates[j][1];
			//			X[j] = coordinates[j][0];
			e.curv += e.Y[j] * e.S[j];
		}
		if (e.curv > allowedMaxR) {
			System.out.println("Minimization Error: The inverse Hessian is very badly scaled, and is unreliable\n");
			return false;
		} else {
			e.curv = 1 / e.curv;
			storage.addFirst(e);
		}
		return true;
	}

	@Override
	public String toString() {
		return ("LBFGS\t" + energy);
	}
}
