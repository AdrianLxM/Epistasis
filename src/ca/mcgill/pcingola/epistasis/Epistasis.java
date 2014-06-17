package ca.mcgill.pcingola.epistasis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ca.mcgill.mcb.pcingola.snpEffect.commandLine.CommandLine;
import ca.mcgill.mcb.pcingola.stats.CountByType;
import ca.mcgill.mcb.pcingola.util.Gpr;
import ca.mcgill.mcb.pcingola.util.Timer;
import ca.mcgill.pcingola.epistasis.phylotree.LikelihoodTree;
import ca.mcgill.pcingola.epistasis.phylotree.MaxLikelihoodTm;
import ca.mcgill.pcingola.epistasis.phylotree.TransitionMatrix;

/**
 * Main command line
 *
 * @author pcingola
 */
public class Epistasis implements CommandLine {

	public static int MIN_DISTANCE = 1000000;
	public static boolean debug = true;

	public static void main(String[] args) {
		Epistasis epistasis = new Epistasis(args);
		epistasis.run();
	}

	String[] args;
	String cmd;
	String aaContactFile, configFile, genome, idMapFile, multAlignFile, pdbDir, qMatrixFile, treeFile;
	LikelihoodTree tree;
	List<DistanceResult> aaContacts;
	TransitionMatrix Q;
	MultipleSequenceAlignmentSet msas;
	MaxLikelihoodTm mltm;
	IdMapper idMapper;
	PdbGenome pdbGenome;

	public Epistasis(String[] args) {
		this.args = args;
		// genome = debug ? "testHg19Chr1" : "hg19";
		// configFile = Gpr.HOME + "/snpEff/" + Config.DEFAULT_CONFIG_FILE;
	}

	@Override
	public String[] getArgs() {
		return args;
	}

	/**
	 * Load files
	 */
	void load() {
		if (treeFile != null) loadTree(treeFile);
		if (multAlignFile != null) loadMsas(multAlignFile);
		if (idMapFile != null) loadIdMap(idMapFile);
		if (aaContactFile != null) loadAaContact(aaContactFile);

		if (genome != null) {
			pdbGenome = new PdbGenome(configFile, genome, pdbDir);
			pdbGenome.setDebug(debug);
			pdbGenome.setIdMapper(idMapper);
			pdbGenome.setMsas(msas);
			pdbGenome.setTree(tree);
			pdbGenome.initialize();
		}

		if (qMatrixFile != null) loadQ(qMatrixFile);
	}

	/**
	 * Load AA contact list
	 */
	List<DistanceResult> loadAaContact(String aaContactFile) {
		Timer.showStdErr("Loading AA contact information " + aaContactFile);
		aaContacts = new ArrayList<>();
		for (String line : Gpr.readFile(aaContactFile).split("\n"))
			aaContacts.add(new DistanceResult(line));

		return aaContacts;
	}

	void loadIdMap(String idMapFile) {
		Timer.showStdErr("Loading id maps " + idMapFile);
		idMapper = new IdMapper(idMapFile);
	}

	/**
	 * Load MSAs
	 */
	void loadMsas(String multAlign) {
		int numAligns = tree.childNames().size();

		// Load: MSA
		Timer.showStdErr("Loading " + numAligns + " way multiple alignment from " + multAlign);
		msas = new MultipleSequenceAlignmentSet(multAlign, numAligns);
		msas.load();
	}

	/**
	 * Load Q matrix
	 */
	void loadQ(String qMatrixFile) {
		Timer.showStdErr("Loading Q matrix  from file '" + qMatrixFile);
		if (!Gpr.exists(qMatrixFile)) {
			System.err.println("Matrix file doesn't exists, nothing done");
			return;
		}

		MaxLikelihoodTm mltm = new MaxLikelihoodTm(tree, msas);
		Q = mltm.loadTransitionMatrix(qMatrixFile);
	}

	/**
	 * Load a tree from a file
	 */
	void loadTree(String phyloFileName) {
		Timer.showStdErr("Loading phylogenetic tree from " + phyloFileName);
		tree = new LikelihoodTree();
		tree.load(phyloFileName);
	}

	/**
	 * Parse command line arguments
	 */
	@Override
	public void parseArgs(String[] args) {
		if (args.length < 1) usage("Missing command");
		cmd = args[0];

		int argNum = 1;
		switch (cmd.toLowerCase()) {
		case "addmsaseqs":
			configFile = args[argNum++];
			genome = args[argNum++];
			treeFile = args[argNum++];
			multAlignFile = args[argNum++];
			idMapFile = args[argNum++];
			aaContactFile = args[argNum++];
			runAddMsaSeqs();
			break;

		case "corr":
			treeFile = args[argNum++];
			multAlignFile = args[argNum++];
			runMsaCorr();
			break;

		case "mi":
			int numBases = Gpr.parseIntSafe(args[argNum++]);
			treeFile = args[argNum++];
			multAlignFile = args[argNum++];
			if (numBases <= 0) usage("number of alignments must be positive number");
			runMsaMi(numBases);
			break;

		case "mappdbgenome":
			configFile = args[argNum++];
			genome = args[argNum++];
			pdbDir = args[argNum++];
			idMapFile = args[argNum++];
			runMapPdbGenome();
			break;

		case "pdbdist":
			// Parse command line
			double distThreshold = Gpr.parseDoubleSafe(args[argNum++]);
			if (distThreshold <= 0) usage("Distance must be a positive number: '" + args[argNum - 1] + "'");
			int aaMinSeparation = Gpr.parseIntSafe(args[argNum++]);
			pdbDir = args[argNum++];
			idMapFile = args[argNum++];
			runPdbDist(distThreshold, aaMinSeparation);
			break;

		case "qhat":
			if (args.length < 4) usage("Missing arguments for command '" + cmd + "'");
			treeFile = args[argNum++];
			multAlignFile = args[argNum++];
			qMatrixFile = args[argNum++];
			runQhat();
			break;

		case "test":
			aaContactFile = args[argNum++];
			runTest();
			break;

		default:
			throw new RuntimeException("Unknown command: '" + cmd + "'");
		}

		Timer.showStdErr("Done!");
	}

	/**
	 * Calculate or load transition matrix
	 */
	TransitionMatrix qHat(String qMatrixFile) {
		MaxLikelihoodTm mltm = new MaxLikelihoodTm(tree, msas);
		Timer.showStdErr("Q matrix file '" + qMatrixFile + "' not found, calculating matrix");
		Q = mltm.estimateTransitionMatrix();
		Timer.showStdErr("Saving Q matrix to file '" + qMatrixFile + "'");
		Q.save(qMatrixFile);

		System.out.println("Q matrix:\n" + Q.toString());
		mltm.showEienQ();

		return Q;
	}

	/**
	 * Parse and Dispatch to right command
	 */
	@Override
	public boolean run() {
		Timer.showStdErr("Start");
		parseArgs(args);
		Timer.showStdErr("End");
		return true;
	}

	/**
	 * Add MSA sequences to 'AA contact' data
	 */
	void runAddMsaSeqs() {
		load();

		// Sanity check: Make sure MSA protein sequences match genome's protein data
		Timer.showStdErr("Checking MSA proteing sequences vs. genome protein sequences");
		pdbGenome.checkSequenceMsaTr();
		System.err.println("Totals:\n" + pdbGenome.countMatch);
		pdbGenome.resetStats();

		// Add MSA sequences to 'AA contact' entries
		Timer.showStdErr("Adding MSA sequences");
		aaContacts.forEach(d -> pdbGenome.mapToMsa(msas, d));
		System.err.println("Totals:\n" + pdbGenome.countMatch);

		System.err.println("Mapped AA sequences:\n");
		aaContacts.stream().filter(d -> d.aaSeq1 != null).forEach(System.out::println);
	}

	void runBayes() {
		//	// Calculate Bayes Factor
		//
		//	// For each MSA...
		//	mltm.calcPi();
		//	for (MultipleSequenceAlignment msa1 : msas) {
		//		for (MultipleSequenceAlignment msa2 : msas) {
		//			// Make sure the MSAs are far away from each other
		//			if (msa2.compareTo(msa1) < MIN_DISTANCE) continue;
		//
		//			// All pair of positions
		//			for (int pos1 = 0; pos1 < msa1.length(); pos1++) {
		//				if (msa1.isSkip(pos1)) continue;
		//
		//				// Log likelihood at position 1
		//				double loklik1 = mltm.logLikelyhood(msa1, pos1);
		//
		//				for (int pos2 = 0; pos2 < msa2.length(); pos2++) {
		//					if (msa2.isSkip(pos2)) continue;
		//
		//					// Log likelihood at position 2
		//					double loklik2 = mltm.logLikelyhood(msa2, pos2);
		//					System.out.println(msa1.getId() + "\t" + msa2.getId() + "\t" + pos1 + "\t" + pos2 + "\t" + loklik1 + "\t" + loklik2);
		//
		//					// Combined log likelihood
		//					mltm.logLikelyhood(msa1, pos1, msa2, pos2);
		//				}
		//			}
		//		}
		//	}
	}

	void runMapPdbGenome() {
		load();
		pdbGenome.checkSequencePdbTr();
	}

	/**
	 * Run correlation
	 * @param numAligns
	 * @param multAlign
	 */
	void runMsaCorr() {
		load();

		// Run similarity
		MsaSimilarity sim = new MsaSimilarity(msas);
		sim.similarity();

		System.out.println("Score statistics:\n" + sim);
	}

	/**
	 * Run Mutual Information
	 */
	void runMsaMi(int numBases) {
		load();

		// Run similarity
		MsaSimilarity sim = numBases > 1 ? new MsaSimilarityMutInfN(msas, numBases) : new MsaSimilarityMutInf(msas);
		sim.similarity();

		System.out.println("Score statistics:\n" + sim);
	}

	/**
	 * Run Pdb distance
	 */
	void runPdbDist(double distThreshold, int aaMinSeparation) {
		load();

		// Run analysis
		PdbDistanceAnalysis pdban = new PdbDistanceAnalysis(pdbDir, distThreshold, aaMinSeparation, idMapper);
		pdban.run();
		System.out.println(pdban);

		// Write results
		String outFile = "pdb_distance_by_AA_pos.txt";
		Gpr.toFile(outFile, pdban);
		System.err.println("Distance metrics file written to: " + outFile);
	}

	/**
	 * Estimate Q matrix from MSA and Phylogenetic-Tree
	 */
	void runQhat() {
		load();
		sanityCheck(tree, msas); // Sanity check: Make sure that the alignment and the tree match
		qHat(qMatrixFile); // Calculate Qhat
	}

	void runTest() {
		Timer.showStdErr("Test!");
		load();

		// Show MI and conservation
		aaContacts.stream().forEach( //
				d -> System.out.println( //
						MsaSimilarityMutInf.mi(d.aaSeq1, d.aaSeq2) //
								+ "\t" + MsaSimilarity.conservation(d.aaSeq1) //
								+ "\t" + MsaSimilarity.conservation(d.aaSeq2) //
								+ "\t" + d) //
				);

		// Count first 'AA'
		CountByType countFirstAa = new CountByType();
		aaContacts.stream().forEach( //
				d -> countFirstAa.addScore( //
						(d.aa1 <= d.aa2 ? d.aa1 + "-" + d.aa2 : d.aa2 + "-" + d.aa1) //
						, MsaSimilarityMutInf.miNoNan(d.aaSeq1, d.aaSeq2) //
						) //
				);
		System.err.println("Count fist AA:\n" + countFirstAa);

		//---
		// Group by genomic position
		//---
		List<DistanceResult> aaContactsByPos = new ArrayList<DistanceResult>();
		aaContactsByPos.addAll(aaContacts);
		Collections.sort(aaContactsByPos, (ac1, ac2) -> ac1.compareByPos(ac2));
		aaContactsByPos.forEach(System.out::println);
	}

	/**
	 * Check consistency between MSA and tree
	 */
	void sanityCheck(LikelihoodTree tree, MultipleSequenceAlignmentSet msas) {
		// Sanity check: Make sure that the alignment and the tree match
		StringBuilder sbMsa = new StringBuilder();
		for (String sp : msas.getSpecies())
			sbMsa.append(sp + " ");

		StringBuilder sbTree = new StringBuilder();
		for (String sp : tree.childNames())
			sbTree.append(sp + " ");

		if (!sbTree.toString().equals(sbMsa.toString())) throw new RuntimeException("Species form MSA and Tree do not match:\n\tMSA : " + sbMsa + "\n\tTree: " + sbTree);

		System.out.println("\nSpecies [" + tree.childNames().size() + "]: " + sbTree);
	}

	@Override
	public void usage(String message) {
		if (message != null) System.err.println("Error: " + message + "\n");
		System.err.println("Usage: " + this.getClass().getSimpleName() + " cmd options");

		System.err.println("Command 'addMsaSeqs'     : " + this.getClass().getSimpleName() + " addMsaSeqs snpeff.config genome phylo.nh multiple_alignment_file.fa id_map.txt aa_contact.txt ");
		System.err.println("Command 'corr'           : " + this.getClass().getSimpleName() + " corr phylo.nh multiple_alignment_file.fa");
		System.err.println("Command 'mapPdbGenome'   : " + this.getClass().getSimpleName() + " mapPdbGenome snpeff.config genome pdbDir idMapFile");
		System.err.println("Command 'mi'             : " + this.getClass().getSimpleName() + " mi number_of_bases phylo.nh multiple_alignment_file.fa");
		System.err.println("Command 'pdbdist'        : " + this.getClass().getSimpleName() + " pdbdist distanceThreshold aaMinSeparation path/to/pdb/dir id_map.txt");
		System.err.println("Command 'qhat'           : " + this.getClass().getSimpleName() + " qhat phylo.nh multiple_sequence_alignment.fa transition_matrix.txt");
		System.exit(-1);
	}

}
