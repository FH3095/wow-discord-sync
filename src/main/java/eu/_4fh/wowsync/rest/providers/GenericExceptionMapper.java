package eu._4fh.wowsync.rest.providers;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu._4fh.wowsync.rest.helper.HtmlHelper;

@Provider
public class GenericExceptionMapper implements ExceptionMapper<Throwable> {
	private static final Logger log = LoggerFactory.getLogger(GenericExceptionMapper.class);

	@Override
	public Response toResponse(Throwable exception) {
		log.error("Error in request", exception);
		return Response.serverError().type(MediaType.TEXT_HTML)
				.entity(buildErrorMessage(Response.Status.INTERNAL_SERVER_ERROR, exception.getMessage())).build();
	}

	/*package*/ static String buildErrorMessage(Response.StatusType status, String message) {
		String errorStr = "ERROR: " + status.getStatusCode() + " - " + status.getReasonPhrase();
		final StringBuilder str = HtmlHelper.getHtmlHead(errorStr);
		str.append("<body><h1>").append(errorStr).append("</h1><p>").append(message).append("</p></body></html>");
		return str.toString();
	}
}
