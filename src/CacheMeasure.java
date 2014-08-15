package cpucache;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.Callable;

/**
 * Estimate the sizes of L1 and L2 caches
 */
public class CacheMeasure implements Callable<CacheMeasurementResult> {
	final private static int INT_BYTES = 4;
	final private static int ONE_K_BYTES = 1024;

	final private int totalSize;
	final private int stepSize;
	final private int minCacheSizeBytes;
	final private int maxCacheSizeBytes;

	public CacheMeasure(int totalSize, int stepSize, int minCacheSizeBytes, int maxCacheSizeBytes) {
		this.totalSize = totalSize;
		this.stepSize = stepSize;
		this.minCacheSizeBytes = minCacheSizeBytes;
		this.maxCacheSizeBytes = maxCacheSizeBytes;
	}

	/**
	 * Run the test
	 * @return the test result
	 * @throws Exception if something bad happens
	 */
	@Override
	public CacheMeasurementResult call() throws Exception {
		int dataStructureBytes = minCacheSizeBytes / 4; // start below the min size
		int maxTestSize = maxCacheSizeBytes * 4; // stop above the max size
		
		SortedMap<Integer,Double> runTimes = new TreeMap<Integer,Double>();
		while (dataStructureBytes < maxTestSize ) {
			int[] dataStructure = createSparseDataStructure(dataStructureBytes, stepSize);
			double runTime = traverseDataStructure(totalSize, stepSize, dataStructure);
			runTimes.put(dataStructureBytes, runTime);

			float dataStructureKb = dataStructure.length * INT_BYTES / (float)ONE_K_BYTES;
			System.out.println(dataStructureKb + " KB: average cost / op = " + runTime + " ns");

			dataStructureBytes = dataStructureBytes * 2;
		}

		return analyzeRunTimes(runTimes);
	}

	/**
	 * Create a sparse array. Each value is the index of another element in the same array.
	 * @param totalSize the size of the data structure in bytes
	 * @param gapSize the sparseness coefficient, ie how elements to hop at a time
	 * @return the data structure
	 */
	private int[] createSparseDataStructure(int totalSize, int gapSize) {
		int dataStructureLength = totalSize / INT_BYTES;
		int[] dataStructure = new int[dataStructureLength];

		for (int i = 0; i < dataStructureLength - gapSize; i += gapSize) {
			for (int j = i; j < i + gapSize; j += gapSize) {
				dataStructure[j] = j + gapSize;
			}
		}
		dataStructure[dataStructureLength - gapSize] = 0; // hop back to the beginning

		return dataStructure;
	}

	/**
	 * Traverse the sparse array, using values in the array to determine where to read next.
	 * @param totalAmountToRead the loop condition or end point
	 * @param amountPerRead the loop increment
	 * @param dataStructure the data structure
	 * @return how long each traversal took on average, in nanoseconds
	 */
	private double traverseDataStructure(int totalAmountToRead, int amountPerRead, int[] dataStructure) {
		int nextReadLocation = 0;

		long start = System.nanoTime();
		for (int i = 0; i < totalAmountToRead; i += amountPerRead) {
			nextReadLocation = dataStructure[nextReadLocation];
			nextReadLocation = dataStructure[nextReadLocation];
			nextReadLocation = dataStructure[nextReadLocation];
			nextReadLocation = dataStructure[nextReadLocation];
			nextReadLocation = dataStructure[nextReadLocation];
			nextReadLocation = dataStructure[nextReadLocation];
			nextReadLocation = dataStructure[nextReadLocation];
			nextReadLocation = dataStructure[nextReadLocation];
		}
		long stop = System.nanoTime();

		double totalRuntime = (double)stop - start;
		int numberOfReads = totalAmountToRead / amountPerRead;
		return (totalRuntime / numberOfReads);
	}

	/**
	 * Extract a best guess for the L1 and L2 cache sizes.
	 * @param runTimes a run time per data structure size
	 * @return the result
	 */
	private CacheMeasurementResult analyzeRunTimes(SortedMap<Integer,Double> runTimes) {
		int[] sizes = new int[runTimes.size()];
		double[] times = new double[runTimes.size()];
		int i = 0;
		for (Map.Entry<Integer,Double> runTime : runTimes.entrySet()) {
			sizes[i] = runTime.getKey();
			times[i] = runTime.getValue();
			i++;
		}
		
		double[] diffTimes = new double[times.length - 1];
		for (int j = 0; j < times.length - 1; j++) {
			double nextDiffPct = (times[j + 1] - times[j]) / times[j];
			diffTimes[j] = (nextDiffPct < 0 ? 0 : nextDiffPct);
		}

		int indexOfMaxValue = findIndexOfMax(diffTimes);
		int maxValue = sizes[indexOfMaxValue];

		diffTimes[indexOfMaxValue] = 0; // remove this from the array before searching for the next biggest max
		int indexOfNextMaxValue = findIndexOfMax(diffTimes);
		int nextMaxValue = sizes[indexOfNextMaxValue];

		int cacheSizeL1 = Math.min(maxValue, nextMaxValue);
		int cacheSizeL2 = Math.max(maxValue, nextMaxValue);
		return new CacheMeasurementResult(cacheSizeL1, cacheSizeL2);
	}

	/**
	 * Simple linear search for the max value
	 * @param values the values
	 * @return the max
	 */
	private int findIndexOfMax(double[] values) {
		int index = 0;
		double max = 0;
		for (int m = 1; m < values.length; m++) {
			if (values[m] > max) {
				max = values[m];
				index = m;
			}
		}
		return index;
	}

	public static void main(String[] args) throws Exception {
		int totalSize = 250000000;
		int stepSize = 32;
		int minCacheSizeBytes = ONE_K_BYTES;
		int maxCacheSizeBytes = ONE_K_BYTES * 16384;

		Callable<CacheMeasurementResult> app = new CacheMeasure(totalSize, stepSize, minCacheSizeBytes, maxCacheSizeBytes);
		CacheMeasurementResult result = app.call();

		float sizeL1KB = result.getL1Bytes() / (float)ONE_K_BYTES;
		float sizeL2KB = result.getL2Bytes() / (float)ONE_K_BYTES;
		System.out.println("L1 cache = " + sizeL1KB + " KB, L2 cache = " + sizeL2KB + " KB");
	}

}
