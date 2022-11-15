@DefaultAnnotation(edu.umd.cs.findbugs.annotations.NonNull.class)
@SuppressFBWarnings(value = { "NP_NONNULL_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR", "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD",
		"EI_EXPOSE_REP",
		"EI_EXPOSE_REP2" }, justification = "This package contains classes that are constructed by hibernate")
package eu._4fh.wowsync.database.data;

import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
