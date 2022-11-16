package eu._4fh.wowsync.util;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;

@DefaultAnnotation(NonNull.class)
public class MacCalculator {
	private static final String MAC_ALGORITHM = "HmacSHA256";
	private static final int KEY_SIZE = 512; // In theory for sha256 also 256 bits would be enough, but 64 bits give greatest security. https://crypto.stackexchange.com/questions/34864/key-size-for-hmac-sha256
	private static final KeyGenerator keyGen;
	static {
		try {
			keyGen = KeyGenerator.getInstance(MAC_ALGORITHM);
			keyGen.init(KEY_SIZE);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private MacCalculator() {
	}

	public static Key fromString(final String str) {
		return new SecretKeySpec(Base64.getDecoder().decode(str), MAC_ALGORITHM);
	}

	public static Key generateKey() {
		return keyGen.generateKey();
	}

	private static byte[] calculateMac(final Key key, final String... macValues) {
		try {
			final Mac mac = Mac.getInstance(MAC_ALGORITHM);
			mac.init(key);
			for (final String value : macValues) {
				mac.update(value.getBytes(StandardCharsets.UTF_8));
			}
			return mac.doFinal();
		} catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new RuntimeException(e);
		}
	}

	public static String generateHmac(final Key key, final String... macValues) {
		return Base64.getEncoder().encodeToString(calculateMac(key, macValues));
	}

	public static void testMac(final Key key, final String macInStr, final String... macValues) {
		final byte[] inMac = Base64.getDecoder().decode(macInStr);
		final byte[] localMacResult = calculateMac(key, macValues);

		if (!MessageDigest.isEqual(inMac, localMacResult)) {
			throw new IllegalArgumentException("Invalid MAC");
		}
	}

	public static void main(final String[] args) {
		final Key key = generateKey();
		final String result = Base64.getEncoder().encodeToString(key.getEncoded());
		System.out.println("Generated key: ");
		System.out.println(result);
	}
}
