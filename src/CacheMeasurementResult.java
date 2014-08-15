package cpucache;

/**
 * Estimated sizes of L1 and L2 caches
 */
public class CacheMeasurementResult {
	final private int sizeL1;
	final private int sizeL2;

	public CacheMeasurementResult(int sizeL1, int sizeL2) {
		this.sizeL1 = sizeL1;
		this.sizeL2 = sizeL2;
	}

	public int getL1Bytes() {
		return sizeL1;
	}

	public int getL2Bytes() {
		return sizeL2;
	}

}
