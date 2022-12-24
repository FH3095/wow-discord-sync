package eu._4fh.wowsync.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RangeTest {
	private static Range<Integer> range(final int start, final int end) {
		return new Range<>(start, end);
	}

	@Test
	void testRangeConstructor() {
		assertThatNoException().isThrownBy(() -> range(5, 5));
		assertThatNoException().isThrownBy(() -> range(5, 6));
		assertThatNoException().isThrownBy(() -> range(-5, -5));
		assertThatNoException().isThrownBy(() -> range(-5, -4));
		assertThatThrownBy(() -> range(-4, -5)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> range(5, 4)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void testFits() {
		final Range<Integer> r = range(-1, 1);
		assertThat(r.fits(-2)).isFalse();
		assertThat(r.fits(-1)).isTrue();
		assertThat(r.fits(0)).isTrue();
		assertThat(r.fits(1)).isTrue();
		assertThat(r.fits(2)).isFalse();
	}
}
