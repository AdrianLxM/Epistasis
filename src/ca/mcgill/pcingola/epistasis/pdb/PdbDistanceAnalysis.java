package ca.mcgill.pcingola.epistasis.pdb;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.biojava.bio.structure.AminoAcid;
import org.biojava.bio.structure.Atom;
import org.biojava.bio.structure.Calc;
import org.biojava.bio.structure.Chain;
import org.biojava.bio.structure.Group;
import org.biojava.bio.structure.Structure;
import org.biojava.bio.structure.StructureException;

import ca.mcgill.mcb.pcingola.util.Gpr;
import ca.mcgill.pcingola.epistasis.IdMapper;

/**
 * PDB distance analysis
 *
 * References: http://biojava.org/wiki/BioJava:CookBook:PDB:read
 *
 * @author pcingola
 */
public class PdbDistanceAnalysis {

	public static final int MAX_AA_LEN = 10000;
	public static final boolean debug = false;
	public static final boolean verbose = true || debug;
	public static final ArrayList<DistanceResult> EMPTY_DISTANCES = new ArrayList<>();

	//	boolean far = false;
	String pdbDir;
	double distanceThreshold;
	double distanceThresholdNon = Double.POSITIVE_INFINITY; // Distance threshold for 'not in contact'
	double sumDist[];
	int count[];
	int countTh[];
	int aaMinSeparation;
	IdMapper idMapper;

	public PdbDistanceAnalysis(String pdbDir, double distanceThreshold, int aaMinSeparation, IdMapper idMapper) {
		this.pdbDir = pdbDir;
		this.distanceThreshold = distanceThreshold;
		this.aaMinSeparation = aaMinSeparation;
		this.idMapper = idMapper;
		sumDist = new double[MAX_AA_LEN];
		count = new int[sumDist.length];
		countTh = new int[sumDist.length];
	}

	/**
	 * Get all AAs in a chain
	 */
	List<AminoAcid> aminoAcids(Chain chain) {
		ArrayList<AminoAcid> aas = new ArrayList<AminoAcid>();
		for (Group group : chain.getAtomGroups())
			if (group instanceof AminoAcid) aas.add((AminoAcid) group);
		return aas;
	}

	/**
	 * Distances within two amino acids within the same chain
	 */
	List<DistanceResult> distance(Chain chain, boolean printDistance) {
		ArrayList<DistanceResult> results = new ArrayList<>();
		List<AminoAcid> aas = aminoAcids(chain);

		for (int i = 0; i < aas.size(); i++) {
			int minj = i + aaMinSeparation;

			for (int j = minj; j < aas.size(); j++) {
				AminoAcid aa1 = aas.get(i);
				AminoAcid aa2 = aas.get(j);

				int aadist = Math.abs(i - j);
				double d = distanceMin(aa1, aa2);
				sumDist[aadist] += d;
				count[aadist]++;

				if ((Double.isFinite(distanceThreshold) && d <= distanceThreshold) // Amino acids in close distance
						|| (Double.isFinite(distanceThresholdNon) && (d > distanceThresholdNon)) // Amino acids far apart
				) {
					countTh[aadist]++;
					DistanceResult dres = new DistanceResult(aa1, aa2, d);
					results.add(dres);
					if (printDistance) System.out.println(((d <= distanceThreshold) ? "AA_IN_CONTACT\t" : "AA_NOT_IN_CONTACT\t") + dres);
				}
			}
		}

		return results;
	}

	/**
	 * Distances within all chains in a structure
	 */
	List<DistanceResult> distance(Structure structure, boolean printDistance) {
		ArrayList<DistanceResult> results = new ArrayList<>();

		// Distance
		for (Chain chain : structure.getChains())
			results.addAll(distance(chain, printDistance));

		return results;
	}

	/**
	 * Distances associated with this PDB file
	 */
	public List<DistanceResult> distanceFile(String pdbFileName, boolean printDistance) {
		try {
			// Read structure form file
			PdbFile pdbreader = new PdbFile();
			if (verbose) System.err.println("Distance: " + pdbFileName);
			Structure pdbStruct = pdbreader.getStructure(pdbFileName);

			// Does it have associated transcripts?
			String id = pdbStruct.getPDBCode();
			if (idMapper.getByPdbId(id) == null) return EMPTY_DISTANCES;

			// Distance
			return distance(pdbStruct, printDistance);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return EMPTY_DISTANCES;
	}

	/**
	 * Distances associated with this entry
	 */
	public List<DistanceResult> distanceId(String pdbId, boolean printDistance) {
		// Does file exists?
		String pdbFileName = pdbDir + "/" + pdbId.toLowerCase() + ".pdb";
		if (!Gpr.exists(pdbFileName)) {
			Gpr.debug("Cannot open file '" + pdbFileName + "'");
			return EMPTY_DISTANCES;
		}

		// Read structure form file
		return distanceFile(pdbFileName, printDistance);
	}

	/**
	 * Minimum distance between all atoms in two amino acids
	 */
	double distanceMin(AminoAcid aa1, AminoAcid aa2) {
		double distMin = Double.POSITIVE_INFINITY;

		for (Atom atom1 : aa1.getAtoms())
			for (Atom atom2 : aa2.getAtoms()) {
				try {
					double dist = Calc.getDistance(atom1, atom2);
					distMin = Math.min(distMin, dist);
				} catch (StructureException e) {
					throw new RuntimeException(e);
				}
			}

		return distMin;
	}

	public Stream<DistanceResult> distanceStream() {
		// Calculate distances for each one
		return idMapper.getEntries().stream() //
				.map(ime -> ime.pdbId) //
				.sorted() //
				.distinct() //
				.parallel() //
				.flatMap(pid -> distanceId(pid, false).stream()) //
				;
	}

	/**
	 * Run
	 */
	public List<DistanceResult> run() {
		// Calculate distances for each one
		List<DistanceResult> res = idMapper.getEntries().stream() //
				.map(ime -> ime.pdbId) //
				.sorted() //
				.distinct() //
				.parallel() //
				.flatMap(pid -> distanceId(pid, true).stream()) //
				.collect(Collectors.toList()) //
				;

		return res;
	}

	public void setDistanceThresholdNon(double distanceThresholdNon) {
		this.distanceThresholdNon = distanceThresholdNon;
	}

	//	public void setFar(boolean far) {
	//		this.far = far;
	//	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();

		sb.append("pos.dist\tdist\tcount.threshold\tp.threshold\tcount\n");
		for (int i = 0; i < sumDist.length; i++) {
			double dist = 0;
			double pth = 0;
			if (count[i] > 0) {
				dist = sumDist[i] / count[i];
				pth = ((double) countTh[i]) / count[i];
			}

			sb.append(i + "\t" + dist + "\t" + countTh[i] + "\t" + pth + "\t" + count[i] + "\n");
		}

		return sb.toString();
	}
}
