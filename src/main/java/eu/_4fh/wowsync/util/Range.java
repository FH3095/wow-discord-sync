package eu._4fh.wowsync.util;

import java.util.Objects;

public class Range<T extends Comparable<T>> {
	public final T start;
	public final T end;

	public Range(final T start, final T end) {
		if (start.compareTo(end) > 0) {
			throw new IllegalArgumentException(start + " is greater than " + end);
		}
		this.start = start;
		this.end = end;
	}

	public boolean fits(final T element) {
		return element.compareTo(start) >= 0 && element.compareTo(end) <= 0;
	}

	@Override
	public String toString() {
		return "Range [start=" + start + ", end=" + end + "]";
	}

	@Override
	public int hashCode() {
		return Objects.hash(end, start);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof Range)) {
			return false;
		}
		Range<?> other = (Range<?>) obj;
		return Objects.equals(end, other.end) && Objects.equals(start, other.start);
	}
}
