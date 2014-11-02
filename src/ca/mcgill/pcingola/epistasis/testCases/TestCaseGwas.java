package ca.mcgill.pcingola.epistasis.testCases;

import junit.framework.TestCase;

import org.junit.Assert;

import ca.mcgill.mcb.pcingola.util.Gpr;
import ca.mcgill.pcingola.epistasis.gwas.GwasEpistasis;

/**
 * Test cases for logistic regression
 *
 * @author pcingola
 */
public class TestCaseGwas extends TestCase {

	public static boolean debug = false;
	public static boolean verbose = false || debug;

	public void test_01_Gwas_Map() {
		Gpr.debug("Test");

		String configFile = Gpr.HOME + "/snpEff/snpEff.config";
		String genome = "testHg19Chr1";
		String genesLikeFile = "test/NM_001438.txt";
		String vcfFile = ""; // It doesn't matter, it is not used
		String phenoFile = ""; // It doesn't matter, it is not used

		GwasEpistasis gwasEpistasis = new GwasEpistasis(configFile, genome, genesLikeFile, vcfFile, phenoFile);
		gwasEpistasis.setDebug(debug);
		gwasEpistasis.initialize();
		gwasEpistasis.readGenesLogLikelihood();

		// Check that all mappings are OK
		Assert.assertEquals(0, gwasEpistasis.getCountErr());
		Assert.assertEquals(334, gwasEpistasis.getCountOk());
	}

	public void test_02_Gwas_Map() {
		Gpr.debug("Test");

		String configFile = Gpr.HOME + "/snpEff/snpEff.config";
		String genome = "testHg19Chr1";
		String genesLikeFile = "test/NM_021969.txt";
		String vcfFile = ""; // It doesn't matter, it is not used
		String phenoFile = ""; // It doesn't matter, it is not used

		GwasEpistasis gwasEpistasis = new GwasEpistasis(configFile, genome, genesLikeFile, vcfFile, phenoFile);
		gwasEpistasis.setDebug(debug);
		gwasEpistasis.initialize();
		gwasEpistasis.readGenesLogLikelihood();

		// Check that all mappings are OK
		Assert.assertEquals(0, gwasEpistasis.getCountErr());
		Assert.assertEquals(458, gwasEpistasis.getCountOk());
	}

	public void test_03_Gwas_Map() {
		Gpr.debug("Test");

		String configFile = Gpr.HOME + "/snpEff/snpEff.config";
		String genome = "testHg19Chr1";
		String genesLikeFile = "test/NM_004905.txt";
		String vcfFile = ""; // It doesn't matter, it is not used
		String phenoFile = ""; // It doesn't matter, it is not used

		GwasEpistasis gwasEpistasis = new GwasEpistasis(configFile, genome, genesLikeFile, vcfFile, phenoFile);
		gwasEpistasis.setDebug(debug);
		gwasEpistasis.initialize();
		gwasEpistasis.readGenesLogLikelihood();

		// Check that all mappings are OK
		Assert.assertEquals(0, gwasEpistasis.getCountErr());
		Assert.assertEquals(298, gwasEpistasis.getCountOk());
	}

	public void test_04_Gwas_Map() {
		Gpr.debug("Test");

		String configFile = Gpr.HOME + "/snpEff/snpEff.config";
		String genome = "testHg19Chr1";
		String genesLikeFile = "test/gwas_map_test_chr1.txt";
		String vcfFile = ""; // It doesn't matter, it is not used
		String phenoFile = ""; // It doesn't matter, it is not used

		GwasEpistasis gwasEpistasis = new GwasEpistasis(configFile, genome, genesLikeFile, vcfFile, phenoFile);
		gwasEpistasis.setDebug(debug);
		gwasEpistasis.initialize();
		gwasEpistasis.readGenesLogLikelihood();

		// Check that all mappings are OK
		Assert.assertEquals(0, gwasEpistasis.getCountErr());
		Assert.assertEquals(13280, gwasEpistasis.getCountOk());
	}

	public void test_05_Gwas_Map_RoundTrip() {
		// Create a atest to map using
		//	i) Select a random <msaId, aaIdx>
		//	ii) Map it to genomic coordinate using GwasEpistasis.parseMsaId(String id, char aaExpected) => marker
		// 	iii) Use all positions in the marker, to map back to <msaId, aaIdx> using GwasEpistasis.id2MsaAa( "chr:pos" )
		//  iv) Check that <mdsId, aaIdx> are recovered correctly 
		throw new RuntimeException("Unimplemented test!");
	}

	public void test_06_Map_InDels() {
		// Create a atest to map using
		//	i) Select a random <msaId, aaIdx>
		//	ii) Map it to genomic coordinate using GwasEpistasis.parseMsaId(String id, char aaExpected) => marker
		// 	iii) Use all positions in the marker, to map back to <msaId, aaIdx> using GwasEpistasis.id2MsaAa( "chr:pos" )
		//  iv) Check that <mdsId, aaIdx> are recovered correctly 
		throw new RuntimeException("Unimplemented test!");
	}

}
