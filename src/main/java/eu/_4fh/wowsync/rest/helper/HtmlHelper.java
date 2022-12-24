package eu._4fh.wowsync.rest.helper;

import java.net.URI;
import java.util.Arrays;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;
import eu._4fh.wowsync.util.Config;
import eu._4fh.wowsync.util.Singletons;

@DefaultAnnotation(NonNull.class)
public class HtmlHelper {
	private HtmlHelper() {
	}

	public static StringBuilder getHtmlHead(final String title, final @CheckForNull String... additionalHeadContent) {
		final Config config = Singletons.instance(Config.class);
		final StringBuilder str = new StringBuilder();
		str.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n")
				.append("<meta http-equiv=\"Content-type\" content=\"text/html; charset=UTF-8\">\n")
				.append("<meta name=\"ROBOTS\" content=\"NOINDEX, NOFOLLOW\">\n").append("<title>").append(title)
				.append("</title>\n");
		str.append("<style>\n").append(config.cssStyle).append("\n</style>\n");
		if (additionalHeadContent != null && additionalHeadContent.length > 0) {
			Arrays.stream(additionalHeadContent).forEach(str::append);
		}
		str.append("</head>\n\n");
		return str;
	}

	public static String encodeLinkForHref(final URI uri) {
		return uri.toASCIIString().replace("&", "&amp;");
	}
}
