package ca.mcgill.pcingola.epistasis.testCases;

import org.junit.Assert;
import org.junit.Test;

import ca.mcgill.pcingola.epistasis.coEvolutionMetrics.AaSimilarityMatrix;
import ca.mcgill.pcingola.epistasis.coEvolutionMetrics.McBasc;
import junit.framework.TestCase;

/**
 * Test cases for McBASC algorithm
 *
 * @author pcingola
 */
public class TestCaseZzz extends TestCase {

	public static boolean debug = false;
	public static boolean verbose = true || debug;

	@Test
	public void test01() {
		String matrix = "data/McLachlan_matrix.txt";

		AaSimilarityMatrix simMatrix = new AaSimilarityMatrix(matrix);

		String coli = "AGH";
		String colj = "AGH";
		McBasc mcBasc = new McBasc(simMatrix, coli, colj);
		System.out.println("Score: " + mcBasc.score());

		Assert.assertEquals(1.4444444444444444, mcBasc.getMeanS_i(), 1e-6);
		Assert.assertEquals(1.4444444444444444, mcBasc.getMeanS_j(), 1e-6);

		Assert.assertEquals(2.697735676039774, mcBasc.getSigmaS_i(), 1e-6);
		Assert.assertEquals(2.697735676039774, mcBasc.getSigmaS_j(), 1e-6);
	}

}
