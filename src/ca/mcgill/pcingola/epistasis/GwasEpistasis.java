package ca.mcgill.pcingola.epistasis;

import java.util.ArrayList;
import java.util.HashMap;

import ca.mcgill.mcb.pcingola.collections.AutoHashMap;
import ca.mcgill.mcb.pcingola.fileIterator.LineFileIterator;
import ca.mcgill.mcb.pcingola.fileIterator.VcfFileIterator;
import ca.mcgill.mcb.pcingola.interval.Chromosome;
import ca.mcgill.mcb.pcingola.interval.Exon;
import ca.mcgill.mcb.pcingola.interval.Gene;
import ca.mcgill.mcb.pcingola.interval.Marker;
import ca.mcgill.mcb.pcingola.interval.Markers;
import ca.mcgill.mcb.pcingola.interval.Transcript;
import ca.mcgill.mcb.pcingola.interval.tree.IntervalForest;
import ca.mcgill.mcb.pcingola.snpEffect.commandLine.SnpEff;
import ca.mcgill.mcb.pcingola.util.Gpr;
import ca.mcgill.mcb.pcingola.util.Timer;
import ca.mcgill.mcb.pcingola.vcf.VcfEntry;

/**
 * Perform GWAS using epistasis data
 *
 * @author pcingola
 */
public class GwasEpistasis extends SnpEff {

	public static int SHOW_EVERY = 10000;
	public static int SHOW_LINE_EVERY = 100 * SHOW_EVERY;

	boolean analyzeAllPairs = false; // Use for testing and debugging
	int countOk, countErr;
	String logLikelihoodFile; // Log likelihood file (epistatic model)
	String vcfFile;
	String genomeVer, pdbDir;
	String phenoCovariatesFile;
	HashMap<String, Transcript> trancriptById; // Transcript by (incomplete) transcript ID (no version number is used)
	HashMap<String, Marker> llmarkerById = new HashMap<String, Marker>(); // log-likelihood markers by ID
	ArrayList<MarkerPairLikelihood> llpairs; // Gene log-likelihood entries
	AutoHashMap<String, ArrayList<byte[]>> gtById; // Genotypes by ID
	IntervalForest llforest; // Interval forest of ll-markers

	public GwasEpistasis(String configFile, String genomeVer, String genesLikeFile, String vcfFile, String phenoCovariatesFile) {
		this.configFile = configFile;
		this.genomeVer = genomeVer;
		logLikelihoodFile = genesLikeFile;
		this.vcfFile = vcfFile;
		this.phenoCovariatesFile = phenoCovariatesFile;
	}

	/**
	 * Build interval forest using LogLik markers
	 */
	protected void buildForest() {
		Timer.showStdErr("Building Log-likelihood marker forest");
		Markers markers = new Markers();
		markers.addAll(llmarkerById.values());

		// Create forest and build
		llforest = new IntervalForest(markers);
		llforest.build();
		Timer.showStdErr("Done. Added " + markers.size() + " markers.");
	}

	public int getCountErr() {
		return countErr;
	}

	public int getCountOk() {
		return countOk;
	}

	/**
	 * Perform GWAS analysis using epistatic information
	 */
	public void gwas() {
		initialize();
		readGenesLogLikelihood();
		readVcf();

		LikelihoodAnalysis2 llan = new LikelihoodAnalysis2(phenoCovariatesFile, vcfFile);
		llan.init();

		if (analyzeAllPairs) {
			for (String idi : gtById.keySet()) {
				for (String idj : gtById.keySet()) {
					if (idi.compareTo(idj) < 0) {
						for (byte gti[] : gtById.get(idi))
							for (byte gtj[] : gtById.get(idj)) {
								double ll = llan.logLikelihood(idi, gti, idj, gtj);
								System.out.println("ll:" + ll + "\t" + idi + "\t" + idj);
							}
					}
				}
			}
		} else {
			// Analyze each enriched region
			for (MarkerPairLikelihood llpair : llpairs) {

				// Find genotypes in under bog markers
				String idi = llpair.getMarker1().getId();
				String idj = llpair.getMarker2().getId();

				// No genotypes in any of those regions? Nothing to do
				if (!gtById.containsKey(idi) || !gtById.containsKey(idj)) {
					if (debug) Gpr.debug("Nothing found:\t" + llpair);
					continue;
				}

				// Analyze all genotype pairs within those regions
				for (byte gti[] : gtById.get(idi)) {
					for (byte gtj[] : gtById.get(idj)) {
						double ll = llan.logLikelihood(idi, gti, idj, gtj);
						System.out.println("ll:" + ll + "\t" + idi + "\t" + idj);
					}
				}
			}
		}
	}

	/**
	 * Load all data
	 */
	public void initialize() {
		// Initialize SnpEff
		String argsSnpEff[] = { "eff", "-v", "-c", configFile, genomeVer };
		args = argsSnpEff;
		setGenomeVer(genomeVer);
		parseArgs(argsSnpEff);
		loadConfig();

		// Load SnpEff database
		if (genomeVer != null) loadDb();

		// Initialize trancriptById
		trancriptById = new HashMap<>();
		for (Gene g : config.getSnpEffectPredictor().getGenome().getGenes())
			for (Transcript tr : g) {
				String id = tr.getId();
				if (id.indexOf('.') > 0) {
					// When using RefSeq transcripts, we don't store sub-version number
					id = id.substring(0, id.indexOf('.'));
				}
				trancriptById.put(id, tr);
			}

	}

	/**
	 * Parse MSA_ID from gene likelihood file
	 *
	 * Format: trId _ chr : start - end [ aaIdx ]
	 *
	 * E.g.  : 	NM_004635_3:50679137-50679201[0]
	 * 			NM_006936_21:46226865-46226954[22]
	 *
	 * @return A marker that contains the codon referenced by the ID
	 *			Note: The marker has ALL bases in the codon.
	 *				  For instance, is the codon is split between two exons, the
	 *				  marker will contain the intron
	 *
	 */
	protected Marker parseMsaId(String id, char aaExpected) {
		// Try to find cached copy
		Marker marker = llmarkerById.get(id);
		if (marker != null) {
			countOk++;
			showCount(true);
			return marker;
		}

		String idRep = id.replace(':', '_').replace('-', '_').replace('[', '_').replace(']', '_');
		String f[] = idRep.split("_");
		String trId = f[0] + "_" + f[1];
		String chr = f[2];
		int start = Gpr.parseIntSafe(f[3]);
		int end = Gpr.parseIntSafe(f[4]);
		int idx = Gpr.parseIntSafe(f[5]);

		//---
		// Calculate position within CDS
		//---

		// Find transcript and exon
		Transcript tr = trancriptById.get(trId);
		if (tr == null) return null;
		Exon ex = tr.findExon(start);
		if (ex == null) return null;

		// Calculate start position
		int startPos;
		int fr = 0;
		if (ex.getFrame() != 0) {
			if (ex.isStrandPlus()) {
				idx--;
				if (ex.getFrame() == 2) idx++; // I don't know why UCSC numbers the AA differentlt when frame is 2
				fr = 3 - ex.getFrame(); // Offset based on frame
			} else {
				idx--;
				if (ex.getFrame() == 2) idx++; // I don't know why UCSC numbers the AA differentlt when frame is 2
				fr = 3 - ex.getFrame(); // Offset based on frame
			}
		}

		// Find AA start position
		if (ex.isStrandPlus()) {
			int exStart = Math.max(start, tr.getCdsStart());
			startPos = exStart + (idx * 3 + fr);
		} else {
			int exEnd = Math.min(end, tr.getCdsStart());
			startPos = exEnd - (idx * 3 + fr);
		}

		// Get position within CDS
		int cdsBase = tr.baseNumberCds(startPos, false);
		int cds2pos[] = tr.baseNumberCds2Pos();
		if ((ex.isStrandPlus() && (startPos < ex.getStart())) //
				|| (ex.isStrandMinus() && (startPos > ex.getEnd()))) {
			// If the position is outside the exon, then we must jump to previous exon
			startPos = cds2pos[cdsBase - ex.getFrame()];
			cdsBase = tr.baseNumberCds(startPos, true);
		}

		//---
		// Sanity check: Make sure that AA matches between transcript model and MSA data from 'genes likelihood' file
		//---

		// Extract codon
		String cdsSeq = tr.cds();
		String codonStr = cdsSeq.substring(cdsBase, cdsBase + 3);
		String aa = genome.codonTable().aa(codonStr);

		if (aa.equals("" + aaExpected)) {
			countOk++;
			if (debug) Gpr.debug("OK: " + id + " : " + aa);
			else showCount(true);
		} else {
			countErr++;
			if (debug) Gpr.debug("Entry ID     : " + id //
					+ "\ntr ID        : " + trId + ", chr: " + chr + ", start: " + start + ", end: " + end + ", idx: " + idx + ", fr: " + fr//
					+ "\nTranscript : " + tr //
					+ "\nExon       : " + ex //
					+ "\nStart pos: " + startPos //
					+ "\nCodon    : " + codonStr + ", aa (real): " + aa + ", aa (exp): " + aaExpected //
			);
			else showCount(false);
		}

		//---
		// Create marker
		// Important: The marker has ALL bases in the codon.
		//            For instance, is the codon is split between two exons, the
		//            marker will contain the intron
		//---
		int markerStart, markerEnd;
		if (tr.isStrandPlus()) {
			markerStart = cds2pos[cdsBase];
			markerEnd = cds2pos[cdsBase + 2];
		} else {
			markerStart = cds2pos[cdsBase + 2];
			markerEnd = cds2pos[cdsBase];
		}

		Chromosome chromo = genome.getChromosome(chr);
		marker = new Marker(chromo, markerStart, markerEnd, ex.isStrandMinus(), id);
		llmarkerById.put(id, marker); // Cache marker
		return marker;
	}

	/**
	 * Perform GWAS analysis using epistatic data
	 */
	public void readGenesLogLikelihood() {
		llpairs = new ArrayList<MarkerPairLikelihood>();

		//---
		// Read "genes likelihood" file
		//---
		Timer.showStdErr("Reading genes likelihood file '" + logLikelihoodFile + "'.");
		int count = 0;
		LineFileIterator lfi = new LineFileIterator(logLikelihoodFile);
		for (String line : lfi) {
			if (line.isEmpty()) continue;

			// Parse line
			String f[] = line.split("\t");
			String msaId1 = f[0];
			String msaId2 = f[1];
			double logLikRatio = Gpr.parseDoubleSafe(f[2]);
			String seq1 = f[5];
			String seq2 = f[6];

			// Filter by log likelihood
			count++;

			// Create MarkerPair
			Marker m1 = parseMsaId(msaId1, seq1.charAt(0));
			Marker m2 = parseMsaId(msaId2, seq2.charAt(0));

			if (m1 != null && m2 != null) {
				MarkerPairLikelihood llp = new MarkerPairLikelihood(m1, m2, logLikRatio);
				llpairs.add(llp);
				if (debug) Gpr.debug(llp);
			} else if (debug) {
				if (m1 == null) Gpr.debug("Cannot create marker: " + msaId1 + ", AA sequence '" + seq1.charAt(0) + "'");
				if (m2 == null) Gpr.debug("Cannot create marker: " + msaId2 + ", AA sequence '" + seq2.charAt(0) + "'");
			}

		}

		int tot = countErr + countOk;
		Timer.showStdErr("Genes likelihood file '" + logLikelihoodFile + "'." //
				+ "\n\tEntries loaded: " + count //
				+ "\n\tmapping. Err / OK : " + countErr + " / " + tot + " [ " + (countErr * 100.0 / tot) + "% ]" //
		);
	}

	/**
	 * Read VCF file: Only entries matching markers from GenesLogLik file
	 * TODO: We could optimize this by using an index and reading only the regions we need
	 */
	public void readVcf() {
		// Initialize
		if (llforest == null) buildForest();
		gtById = new AutoHashMap<String, ArrayList<byte[]>>(new ArrayList<byte[]>());

		// Read VCF file
		int count = 0;
		Timer.showStdErr("Reading vcf file '" + vcfFile + "'");
		VcfFileIterator vcf = new VcfFileIterator(vcfFile);
		for (VcfEntry ve : vcf) {
			// Is this entry overlapping any llmarker?
			Markers results = llforest.query(ve);
			if (results.isEmpty()) continue; // No hits

			// Add genotypes to map (all results)
			byte gt[] = ve.getGenotypesScores();
			for (Marker r : results) {
				gtById.getOrCreate(r.getId()).add(gt);
				count++;
				if (debug) Gpr.debug("Adding GT " + ve.toStr() + "\t" + r.getId());
			}
		}

		Timer.showStdErr("Done. Added " + count + " gentype entries (hash size:" + gtById.size() + ").");
	}

	public void setAnalyzeAllPairs(boolean analyzeAllPairs) {
		this.analyzeAllPairs = analyzeAllPairs;
	}

	void showCount(boolean ok) {
		if (!debug) {
			int tot = countOk + countErr;

			if (ok) {
				if (tot % SHOW_EVERY == 0) System.out.print('.'); // Show OK
			} else System.out.print('*'); // Show error

			// Add a newline every now and then
			if (tot % SHOW_LINE_EVERY == 0) System.out.print("\n" + tot + "\t");
		}
	}
}