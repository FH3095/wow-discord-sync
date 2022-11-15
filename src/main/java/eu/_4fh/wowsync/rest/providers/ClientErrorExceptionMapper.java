package eu._4fh.wowsync.rest.providers;

import javax.ws.rs.ClientErrorException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
public class ClientErrorExceptionMapper implements ExceptionMapper<ClientErrorException> {

	@Override
	public Response toResponse(ClientErrorException exception) {
		Response response = Response.status(exception.getResponse().getStatusInfo()).type(MediaType.TEXT_HTML)
				.entity(GenericExceptionMapper.buildErrorMessage(exception.getResponse().getStatusInfo(),
						exception.getMessage()))
				.build();
		return response;
	}
}
